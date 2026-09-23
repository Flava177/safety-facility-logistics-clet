package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.emergency.application.service.ActivationService;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.model.ChannelType;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.model.Priority;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.EmergencyFastLanePort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SRS-SFL-S162a-02: the only class in {@code lifesafety} that imports the {@code emergency} package -
 * every other cross-module reference goes through {@link EmergencyFastLanePort}. S174 lives in the
 * same deployable, so this is an in-process call to {@code ActivationService.breakGlass}, not a
 * recorded/simulated external adapter.
 *
 * <p>Break-glass activation needs a break-glass-eligible template or scenario, which nothing in this
 * Phase-1 build seeds automatically. Rather than guess one, this adapter takes it from configuration -
 * a real deployment configures the exam-hall/campus fire-response template's id once, alongside the
 * template itself; without it configured, {@link #triggerFastLane} returns empty and the caller
 * records a degraded fast-lane trigger, per the SRS's own "Emergency Trigger Degraded" error state.
 */
@Component
public class EmergencyFastLaneAdapter implements EmergencyFastLanePort {

    private static final Logger log = LoggerFactory.getLogger(EmergencyFastLaneAdapter.class);

    private final ActivationService activationService;
    private final UUID fastLaneTemplateId;
    private final UUID fastLaneScenarioId;

    public EmergencyFastLaneAdapter(ActivationService activationService,
            @Value("${sfl.life-safety.fast-lane.template-id:}") String templateId,
            @Value("${sfl.life-safety.fast-lane.scenario-id:}") String scenarioId) {
        this.activationService = activationService;
        this.fastLaneTemplateId = parse(templateId);
        this.fastLaneScenarioId = parse(scenarioId);
    }

    @Override
    public Optional<UUID> triggerFastLane(String siteCode, String zoneCode, String description, ActorContext actor) {
        if (fastLaneTemplateId == null && fastLaneScenarioId == null) {
            log.warn("No break-glass-eligible template/scenario configured; fast-lane trigger degraded for site={}",
                    siteCode);
            return Optional.empty();
        }
        try {
            var activation = activationService.breakGlass(new ActivationService.CreateActivation(siteCode,
                    fastLaneScenarioId, fastLaneTemplateId, List.of(), List.of(), List.of(ChannelType.SMS,
                            ChannelType.SIREN), Priority.CRITICAL, description,
                    "lifesafety-fast-lane-" + siteCode + "-" + zoneCode + "-" + description.hashCode(), actor,
                    SourceChannel.SYSTEM));
            return Optional.of(activation.id());
        } catch (RuntimeException e) {
            log.warn("Fast-lane break-glass activation failed for site={} zone={}: {}", siteCode, zoneCode,
                    e.getMessage());
            return Optional.empty();
        }
    }

    private static UUID parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value.strip());
    }
}
