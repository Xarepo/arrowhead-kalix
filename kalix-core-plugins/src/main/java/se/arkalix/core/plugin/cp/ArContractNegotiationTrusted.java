package se.arkalix.core.plugin.cp;

import se.arkalix.util.concurrent.Future;

import java.time.Instant;

/**
 * A service useful for making contract offers, as well as accepting or
 * rejecting received contract offers.
 * <p>
 * The services that may be consumed via this interface must be trusted not to
 * alter the messages sent to them.
 */
@SuppressWarnings("unused")
public interface ArContractNegotiationTrusted {
    /**
     * Accepts {@link TrustedContractSession#offer() contract offer} identified
     * by given {@code acceptance}.
     *
     * @param acceptance Identifies accepted session offer.
     * @return Future completed successfully only if acceptance succeeds.
     */
    Future<?> accept(TrustedContractAcceptanceDto acceptance);

    /**
     * Accepts {@link TrustedContractSession#offer() contract offer} identified
     * by given session identifier.
     *
     * @param sessionId Identifies negotiation session the accepted offer is
     *                  part of.
     * @return Future completed successfully only if acceptance succeeds.
     */
    default Future<?> accept(final long sessionId) {
        return accept(new TrustedContractAcceptanceBuilder()
            .sessionId(sessionId)
            .acceptedAt(Instant.now())
            .build());
    }

    /**
     * Makes a new {@link TrustedContractOffer contract offer}.
     *
     * @param offer Offer details.
     * @return Future completed with a negotiation session identifier only if
     * the offer could be made.
     */
    Future<Long> offer(TrustedContractOfferDto offer);

    /**
     * Makes a {@link TrustedContractOffer contract counter-offer}.
     *
     * @param counterOffer Counter-offer details.
     * @return Future completed with successfully only if the counter-offer
     * could be made.
     */
    Future<?> counterOffer(TrustedContractCounterOfferDto counterOffer);

    /**
     * Rejects {@link TrustedContractSession#offer() contract offer} identified
     * by given {@code rejection}.
     *
     * @param rejection Identifies rejected session offer.
     * @return Future completed successfully only if rejection succeeds.
     */
    Future<?> reject(TrustedContractRejectionDto rejection);

    /**
     * Rejects {@link TrustedContractSession#offer() contract offer} identified
     * by given session identifier.
     *
     * @param sessionId Identifies negotiation session the rejected offer is
     *                  part of.
     * @return Future completed successfully only if rejection succeeds.
     */
    default Future<?> reject(final long sessionId) {
        return reject(new TrustedContractRejectionBuilder()
            .sessionId(sessionId)
            .rejectedAt(Instant.now())
            .build());
    }
}
