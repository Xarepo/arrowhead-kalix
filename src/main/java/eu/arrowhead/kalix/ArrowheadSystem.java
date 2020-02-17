package eu.arrowhead.kalix;

import eu.arrowhead.kalix.security.Keystore;
import eu.arrowhead.kalix.security.Truststore;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Represents an abstract Arrowhead Framework (AHF) system.
 * <p>
 * An instance of this class is used manage the existence of a single AHF
 * system, which exists to <i>provide</i> and <i>consume</i>
 * {@link Service}s.
 */
public class ArrowheadSystem {
    private final String name;
    private final InetSocketAddress host;
    private final Keystore keystore;
    private final Truststore truststore;
    private final List<Service> services;

    protected ArrowheadSystem(final Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "System name is mandatory");
        this.host = Objects.requireNonNullElseGet(builder.host, () -> new InetSocketAddress(0));
        this.keystore = builder.keystore;
        this.truststore = builder.truststore;
        this.services = builder.services;
    }

    public String name() {
        return name;
    }

    public InetSocketAddress host() {
        return host;
    }

    public static class Builder {
        private String name;
        private InetSocketAddress host;
        private Keystore keystore;
        private Truststore truststore;
        private List<Service> services = new ArrayList<>(0);

        public final Builder name(final String name) {
            this.name = name;
            return this;
        }

        public final Builder host(final InetSocketAddress host) {
            this.host = host;
            return this;
        }

        public final Builder host(final InetAddress address, final int port) {
            return host(new InetSocketAddress(address, port));
        }

        public final Builder host(final String hostname, final int port) {
            return host(new InetSocketAddress(hostname, port));
        }

        public final Builder keystore(final Keystore keystore) {
            this.keystore = keystore;
            return this;
        }

        public final Builder truststore(final Truststore truststore) {
            this.truststore = truststore;
            return this;
        }

        public final Builder services(final List<Service> services) {
            this.services = services;
            return this;
        }

        public final Builder services(final Service... services) {
            this.services = Arrays.asList(services);
            return this;
        }

        public final Builder addService(final Service service) {
            this.services.add(service);
            return this;
        }

        public final Builder addServices(final Service... services) {
            Collections.addAll(this.services, services);
            return this;
        }

        public ArrowheadSystem build() {
            return new ArrowheadSystem(this);
        }
    }
}
