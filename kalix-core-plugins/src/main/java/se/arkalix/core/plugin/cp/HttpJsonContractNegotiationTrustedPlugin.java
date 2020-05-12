package se.arkalix.core.plugin.cp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.arkalix.ArSystem;
import se.arkalix.core.plugin.eh.ArEventSubscriberPluginFacade;
import se.arkalix.core.plugin.eh.ArEventSubscriptionHandle;
import se.arkalix.core.plugin.eh.HttpJsonEventSubscriberPlugin;
import se.arkalix.plugin.Plugin;
import se.arkalix.plugin.PluginAttached;
import se.arkalix.plugin.PluginFacade;
import se.arkalix.util.concurrent.Future;
import se.arkalix.util.concurrent.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class HttpJsonContractNegotiationTrustedPlugin implements Plugin {
    private static final Logger logger = LoggerFactory.getLogger(HttpJsonContractNegotiationTrustedPlugin.class);

    @Override
    public Set<Class<? extends Plugin>> dependencies() {
        return Collections.singleton(HttpJsonEventSubscriberPlugin.class);
    }

    @Override
    public Future<PluginAttached> attachTo(
        final ArSystem system,
        final Map<Class<? extends Plugin>, PluginFacade> dependencies)
    {
        final var eventSubscriber = dependencies.get(HttpJsonEventSubscriberPlugin.class);
        if (eventSubscriber == null) {
            throw new IllegalStateException("Expected HttpJsonEventSubscriberPlugin to provide plugin facade");
        }
        final var attached = new Attached(system, (ArEventSubscriberPluginFacade) eventSubscriber);
        return attached.subscribe()
            .pass(attached);
    }

    private static class Attached implements PluginAttached {
        private final Facade facade = new Facade();
        private final ArSystem system;
        private final ArEventSubscriberPluginFacade eventSubscriber;
        private final Map<String, Handler> handlerMap = new ConcurrentHashMap<>();

        private ArEventSubscriptionHandle eventSubscriptionHandle = null;

        private Attached(final ArSystem system, final ArEventSubscriberPluginFacade eventSubscriber) {
            this.system = Objects.requireNonNull(system, "Expected system");
            this.eventSubscriber = Objects.requireNonNull(eventSubscriber, "Expected eventSubscriber");
        }

        public Future<?> subscribe() {
            return eventSubscriber
                .subscribe(ContractProxyConstants.TOPIC, (metadata, data) -> {
                    final long sessionId;
                    try {
                        sessionId = Long.parseLong(data);
                    }
                    catch (final NumberFormatException exception) {
                        logger.warn("HTTP/JSON contract negotiator received " +
                            "contract event with invalid session identifier; " +
                            "cannot process event [sessionId={}, metadata={}]", data, metadata);
                        return;
                    }

                    final var offerorName = metadata.get("offeror");
                    if (offerorName == null) {
                        logger.warn("HTTP/JSON contract negotiator received " +
                            "contract event without a named offeror; " +
                            "cannot process event [sessionId={}, metadata={}]", data, metadata);
                        return;
                    }

                    final var receiverName = metadata.get("receiver");
                    if (receiverName == null) {
                        logger.warn("HTTP/JSON contract negotiator received " +
                            "contract event without a named receiver; " +
                            "cannot process event [sessionId={}, metadata={}]", data, metadata);
                        return;
                    }

                    final var handlerKey = offerorName + "." + receiverName + "." + sessionId;
                    final var handler = handlerMap.get(handlerKey);
                    if (handler == null) {
                        logger.trace("HTTP/JSON contract negotiator received " +
                            "contract event identifying session not relevant " +
                            "to this system; ignoring event [sessionId={}, " +
                            "metadata={}]", data, metadata);
                        return;
                    }

                    system.consume()
                        .using(HttpJsonContractNegotiationTrustedSession.factory())
                        .flatMap(service -> service.getByNamesAndId(offerorName, receiverName, sessionId))
                        .ifSuccess(session -> {
                            final var candidate = session.candidate();
                            switch (session.status()) {
                            case OFFERING:

                                final var expirationDelay = Duration.between(
                                    candidate.validUntil(),
                                    candidate.validAfter());
                                handler.setExpirationTask(expirationDelay, () -> {
                                    if (handlerMap.remove(handlerKey) != null && logger.isWarnEnabled()) {
                                        logger.warn("HTTP/JSON contract " +
                                            "negotiator session expired due " +
                                            "received offer never being " +
                                            "handled [offer={}]", candidate);
                                    }
                                });

                                handler.onOffer(candidate, new ArTrustedNegotiationResponder() {
                                    @Override
                                    public Future<?> accept() {
                                        handlerMap.remove(handlerKey);
                                        return system.consume()
                                            .using(HttpJsonContractNegotiationTrusted.factory())
                                            .flatMap(service -> service.accept(new TrustedAcceptanceBuilder()
                                                .sessionId(sessionId)
                                                .acceptedAt(Instant.now())
                                                .build()));
                                    }

                                    @Override
                                    public Future<?> offer(final TrustedCounterOffer offer) {
                                        final var expirationDelay = Duration.between(
                                            offer.validUntil(),
                                            offer.validAfter());
                                        handler.setExpirationTask(expirationDelay, () -> {
                                            if (handlerMap.remove(handlerKey) != null && logger.isWarnEnabled()) {
                                                logger.warn("HTTP/JSON contract negotiator " +
                                                    "session expired; counter-party never " +
                                                    "responded to {}", offer);
                                            }
                                        });
                                        return system.consume()
                                            .using(HttpJsonContractNegotiationTrusted.factory())
                                            .flatMap(service -> service.offer(new TrustedOfferBuilder()
                                                .sessionId(sessionId)
                                                .offerorName(offerorName)
                                                .receiverName(receiverName)
                                                .validAfter(offer.validAfter())
                                                .validUntil(offer.validUntil())
                                                .contracts(offer.contracts())
                                                .offeredAt(offer.offeredAt())
                                                .build()));
                                    }

                                    @Override
                                    public Future<?> reject() {
                                        handlerMap.remove(handlerKey);
                                        return system.consume()
                                            .using(HttpJsonContractNegotiationTrusted.factory())
                                            .flatMap(service -> service.reject(new TrustedRejectionBuilder()
                                                .sessionId(sessionId)
                                                .rejectedAt(Instant.now())
                                                .build()));
                                    }
                                });
                                break;

                            case ACCEPTED:
                                handler.onAccept(candidate);
                                break;

                            case REJECTED:
                                handler.onReject(candidate);
                                break;
                            }
                        });
                })
                .ifSuccess(handle -> {
                    synchronized (this) {
                        eventSubscriptionHandle = handle;
                    }
                });
        }

        @Override
        public Optional<PluginFacade> facade() {
            return Optional.of(facade);
        }

        @Override
        public void onDetach() {
            synchronized (this) {
                eventSubscriptionHandle.unsubscribe();
                eventSubscriptionHandle = null;
            }
        }

        private class Facade implements ArContractNegotiationTrustedPluginFacade {
            @Override
            public void offer(final TrustedOfferDto offer, final ArTrustedNegotiationHandler handler) {
                system.consume()
                    .using(HttpJsonContractNegotiationTrusted.factory())
                    .flatMap(service -> service.offer(offer))
                    .ifSuccess(ignored -> {
                        final var key = offer.offerorName() + "." + offer.receiverName() + "." + offer.sessionId();
                        final var handler0 = new Handler(handler);
                        handlerMap.put(key, handler0);

                        final var expirationDelay = Duration.between(offer.validUntil(), offer.validAfter());
                        handler0.setExpirationTask(expirationDelay, () -> {
                            if (handlerMap.remove(key) != null && logger.isWarnEnabled()) {
                                logger.warn("HTTP/JSON contract negotiator " +
                                    "session expired; counter-party never " +
                                    "responded to {}", offer);
                            }
                        });
                    })
                    .onFailure(handler::onFault);
            }
        }
    }

    private static class Handler implements ArTrustedNegotiationHandler {
        private final ArTrustedNegotiationHandler handler;

        private Future<?> expirationTask = null;

        private Handler(final ArTrustedNegotiationHandler handler) {
            this.handler = handler;
        }

        public synchronized void setExpirationTask(final Duration delay, final Runnable task) {
            if (expirationTask != null) {
                expirationTask.cancel();
            }
            expirationTask = Schedulers.fixed().schedule(delay, task);
        }

        @Override
        public synchronized void onAccept(final TrustedSessionCandidate candidate) {
            handler.onAccept(candidate);
        }

        @Override
        public synchronized void onOffer(final TrustedSessionCandidate candidate, final ArTrustedNegotiationResponder responder) {
            handler.onOffer(candidate, responder);
        }

        @Override
        public synchronized void onReject(final TrustedSessionCandidate candidate) {
            handler.onReject(candidate);
        }
    }
}
