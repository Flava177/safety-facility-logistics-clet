package gh.edu.clet.sfl.facilities.shared.infrastructure.integration;

import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorSourcePort;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The vendor allowlist from {@code sfl.facilities.vendor-inbox.*}, secrets from the environment.
 *
 * <p>The shipped sources are simulators with a development secret, exactly as SSEMP's inboxes ship -
 * no real vendor has passed the SRS §5.2 procurement gate (see {@code VendorIntegrationRegistry}). Every
 * startup warns about any source still carrying the development secret or reporting for {@code *}, so a
 * production configuration that forgot to override one says so in its first log line rather than after
 * an incident.
 */
@Component
@ConfigurationProperties(prefix = "sfl.facilities.vendor-inbox")
public class ConfiguredVendorSources implements VendorSourcePort {

    static final String DEVELOPMENT_SECRET = "dev-only-not-a-real-secret";
    private static final Logger log = LoggerFactory.getLogger(ConfiguredVendorSources.class);

    private Duration signatureWindow = Duration.ofMinutes(5);
    private Map<String, Source> sources = new LinkedHashMap<>();

    @Override
    public Optional<VendorSource> find(String sourceId) {
        if (sourceId == null) {
            return Optional.empty();
        }
        return sources.entrySet().stream()
                .filter(entry -> entry.getKey().strip().equalsIgnoreCase(sourceId.strip()))
                .findFirst()
                .filter(entry -> entry.getValue().channel != null && entry.getValue().secret != null
                        && !entry.getValue().secret.isBlank())
                .map(entry -> new VendorSource(entry.getKey().strip().toUpperCase(Locale.ROOT),
                        entry.getValue().channel, entry.getValue().secret,
                        entry.getValue().sites.stream().map(site -> site.strip().toUpperCase(Locale.ROOT))
                                .collect(Collectors.toUnmodifiableSet())));
    }

    @Override
    public Duration signatureWindow() {
        return signatureWindow;
    }

    @jakarta.annotation.PostConstruct
    void warnAboutDevelopmentSources() {
        sources.forEach((id, source) -> {
            if (DEVELOPMENT_SECRET.equals(source.secret)) {
                log.warn("Vendor source {} ({}) is using the development secret - override "
                        + "sfl.facilities.vendor-inbox.sources.{}.secret in any shared environment", id,
                        source.channel, id);
            }
            if (source.sites.contains("*")) {
                log.warn("Vendor source {} may report for every site - production sources should be "
                        + "registered per site", id);
            }
        });
    }

    public Duration getSignatureWindow() {
        return signatureWindow;
    }

    public void setSignatureWindow(Duration signatureWindow) {
        this.signatureWindow = signatureWindow;
    }

    public Map<String, Source> getSources() {
        return sources;
    }

    public void setSources(Map<String, Source> sources) {
        this.sources = sources;
    }

    public static class Source {
        private VendorChannel channel;
        private String secret;
        private List<String> sites = List.of();

        public VendorChannel getChannel() {
            return channel;
        }

        public void setChannel(VendorChannel channel) {
            this.channel = channel;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public List<String> getSites() {
            return sites;
        }

        public void setSites(List<String> sites) {
            this.sites = sites == null ? List.of() : sites;
        }

        Set<String> siteSet() {
            return Set.copyOf(sites);
        }
    }
}
