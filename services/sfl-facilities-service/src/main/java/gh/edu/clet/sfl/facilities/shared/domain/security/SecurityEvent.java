package gh.edu.clet.sfl.facilities.shared.domain.security;

import java.time.Instant;
import java.util.Objects;

/**
 * A security-relevant fact IFIMP owes the SIEM (S208) - SRS 2026/002 cross-cutting requirement.
 *
 * <p>Deliberately narrow. The audit chain (S204) records every state change; this records the subset a
 * security operations centre acts on - forged vendor traffic, a telemetry source rejected repeatedly, a
 * contractor's access suspended for lapsed compliance. Carries references and classifications only,
 * never payloads: a forged message's body is attacker-controlled text and has no business in a SIEM
 * index.
 *
 * @param module the SRS system that observed it - {@code S156}, {@code S176}, ...
 * @param category a stable classification a SIEM rule can match, e.g. {@code VENDOR_MESSAGE_REJECTED}
 * @param siteCode the site concerned, or {@code null} when the claim of one cannot be trusted
 * @param reference the record id the SOC follows up - an inbox message id, an access grant id
 */
public record SecurityEvent(
        String module,
        String category,
        Severity severity,
        String siteCode,
        String summary,
        String reference,
        Instant occurredAt) {

    public SecurityEvent {
        Objects.requireNonNull(module, "module is required");
        Objects.requireNonNull(category, "category is required");
        Objects.requireNonNull(severity, "severity is required");
        Objects.requireNonNull(summary, "summary is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    public enum Severity {
        INFO,
        WARNING,
        HIGH
    }
}
