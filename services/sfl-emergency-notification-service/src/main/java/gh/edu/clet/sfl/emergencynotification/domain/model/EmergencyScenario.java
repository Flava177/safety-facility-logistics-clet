package gh.edu.clet.sfl.emergencynotification.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A declared emergency scenario (fire, intrusion, evacuation, weather, drill …) with a default template
 * and priority. {@code breakGlassEligible} scenarios may be activated without pre-approval by an
 * authorised role.
 */
public record EmergencyScenario(UUID id, String scenarioCode, SiteCode siteCode, String name, Priority priority,
        UUID defaultTemplateId, boolean breakGlassEligible, RecordLifecycle lifecycle, RecordMetadata metadata) {

    public EmergencyScenario {
        Objects.requireNonNull(id);
        Objects.requireNonNull(siteCode);
        Objects.requireNonNull(priority);
        Objects.requireNonNull(lifecycle);
        Objects.requireNonNull(metadata);
        scenarioCode = require(scenarioCode, "scenarioCode");
        name = require(name, "name");
    }

    public boolean active() {
        return lifecycle.isActive();
    }

    public EmergencyScenario withLifecycle(RecordLifecycle next, RecordMetadata changed) {
        return new EmergencyScenario(id, scenarioCode, siteCode, name, priority, defaultTemplateId, breakGlassEligible,
                next, changed);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
