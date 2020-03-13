package eu.arrowhead.kalix.internal.net.http;

import eu.arrowhead.kalix.ArrowheadService;
import eu.arrowhead.kalix.ArrowheadSystem;
import eu.arrowhead.kalix.internal.ArrowheadServer;
import eu.arrowhead.kalix.internal.net.NettyBootstraps;
import eu.arrowhead.kalix.internal.net.http.service.NettyHttpServiceConnectionInitializer;
import eu.arrowhead.kalix.net.http.service.HttpArrowheadService;
import eu.arrowhead.kalix.util.concurrent.Future;
import io.netty.channel.Channel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import static eu.arrowhead.kalix.internal.util.concurrent.NettyFutures.adapt;

public class HttpArrowheadServer extends ArrowheadServer {
    private final Map<String, HttpArrowheadService> providedServices = new ConcurrentSkipListMap<>();

    private Channel channel = null;

    public HttpArrowheadServer(final ArrowheadSystem system) {
        super(system);
    }

    @Override
    public Future<InetSocketAddress> start() {
        try {
            final var system = system();
            SslContext sslContext = null;
            if (system.isSecure()) {
                final var keyStore = system.keyStore();
                sslContext = SslContextBuilder
                    .forServer(keyStore.privateKey(), keyStore.certificateChain())
                    .trustManager(system.trustStore().certificates())
                    .clientAuth(ClientAuth.REQUIRE)
                    .startTls(false)
                    .build();
            }
            return adapt(NettyBootstraps
                .createServerBootstrapUsing(system.scheduler())
                .handler(new LoggingHandler())
                .childHandler(new NettyHttpServiceConnectionInitializer(this::getServiceByPath, sslContext))
                .bind(system.localAddress(), system.localPort()))
                .map(channel0 -> {
                    channel = channel0;
                    return (InetSocketAddress) channel0.localAddress();
                });
        }
        catch (final Throwable throwable) {
            return Future.failure(throwable);
        }
    }

    @Override
    public Future<?> stop() {
        providedServices.clear();
        return channel != null
            ? adapt(channel.close())
            : Future.done();
    }

    @Override
    public boolean canProvide(final ArrowheadService service) {
        return service instanceof HttpArrowheadService;
    }

    @Override
    public synchronized Collection<HttpArrowheadService> providedServices() {
        return Collections.unmodifiableCollection(providedServices.values());
    }

    private Optional<HttpArrowheadService> getServiceByPath(final String path) {
        for (final var entry : providedServices.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean provideService(final ArrowheadService service) {
        Objects.requireNonNull(service, "Expected service");
        if (!(service instanceof HttpArrowheadService)) {
            throw new IllegalArgumentException("Expected service to be HttpService");
        }
        final var existingService = providedServices.putIfAbsent(service.qualifier(), (HttpArrowheadService) service);
        if (existingService != null) {
            if (existingService != service) {
                throw new IllegalStateException("Qualifier (base path) \"" +
                    service.qualifier() + "\" already in use by  \"" +
                    existingService.name() + "\"; cannot provide \"" +
                    service.name() + "\"");
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean dismissService(final ArrowheadService service) {
        Objects.requireNonNull(service, "Expected service");
        return providedServices.remove(service.qualifier()) != null;
    }
}
