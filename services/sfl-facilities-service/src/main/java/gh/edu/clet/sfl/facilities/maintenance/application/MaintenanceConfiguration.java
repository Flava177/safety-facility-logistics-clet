package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.policy.SlaPolicy;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads S153's rules out of runtime configuration, at the moment they are needed.
 *
 * <p>SRS-SFL-S153-02 is explicit: <em>"Escalation rules must be evaluated using the runtime
 * configuration active at the time of evaluation."</em> That sentence rules out the obvious
 * implementation — read the values once at startup into a bean — because a rule changed at nine
 * o'clock would then not apply until the service restarted, and nobody would be able to say from the
 * outside whether it had.
 *
 * <p>So every method here reads through {@link RuntimeConfigurationPort} on each call. The port is
 * effective-dated and site-scoped, which is what makes "active at the time of evaluation" a
 * well-defined thing rather than a hope about deployment timing.
 *
 * <h2>Why the keys are namespaced and defaulted</h2>
 *
 * Every key is {@code maintenance.*} and every read carries a fallback. A site that has configured
 * nothing gets {@link SlaPolicy#defaults()}, which means S153 works on a fresh database — the
 * alternative is a service that starts, accepts a fault, and then cannot compute its deadline
 * because somebody has not run a seed script.
 */
@Component
public class MaintenanceConfiguration {

    /** How long to acknowledge and assign, per priority. */
    static final String KEY_RESPONSE = "maintenance.sla.response.";
    /** How long to finish, per priority. This is what a work order's due date comes from. */
    static final String KEY_RESOLUTION = "maintenance.sla.resolution.";
    /** Multiplier applied to both while the site is in examination mode. */
    static final String KEY_EXAMINATION_FACTOR = "maintenance.sla.examination-factor";
    /** How long between successive escalation levels once overdue. */
    static final String KEY_ESCALATION_INTERVAL = "maintenance.escalation.interval";
    /** The top of the escalation ladder. */
    static final String KEY_MAX_ESCALATION = "maintenance.escalation.max-level";
    /** The priority at or above which a fault raises a readiness blocker on its space. */
    static final String KEY_BLOCKER_THRESHOLD = "maintenance.readiness.blocker-threshold";
    /** The priority at or above which closure evidence is mandatory. */
    static final String KEY_EVIDENCE_THRESHOLD = "maintenance.closure.evidence-threshold";
    /** How many pieces of evidence closure needs once the threshold is met. */
    static final String KEY_EVIDENCE_COUNT = "maintenance.closure.evidence-count";
    /** How many schedules one generation run may raise work for. */
    static final String KEY_GENERATION_BATCH = "maintenance.preventive.generation-batch";

    private final RuntimeConfigurationPort configuration;

    public MaintenanceConfiguration(RuntimeConfigurationPort configuration) {
        this.configuration = configuration;
    }

    /**
     * The SLA and escalation rules in force for a site, right now.
     *
     * <p>Built fresh on each call. That sounds wasteful until you count: it is one read per key from
     * a table the port already caches per request, against the alternative of a rule change that
     * silently does not apply.
     */
    public SlaPolicy slaPolicyFor(String siteCode) {
        SlaPolicy fallback = SlaPolicy.defaults();
        Map<FaultPriority, Duration> response = new EnumMap<>(FaultPriority.class);
        Map<FaultPriority, Duration> resolution = new EnumMap<>(FaultPriority.class);
        for (FaultPriority priority : FaultPriority.values()) {
            String suffix = priority.name().toLowerCase(java.util.Locale.ROOT);
            response.put(priority, configuration.duration(KEY_RESPONSE + suffix, siteCode,
                    fallback.response().get(priority)));
            resolution.put(priority, configuration.duration(KEY_RESOLUTION + suffix, siteCode,
                    fallback.resolution().get(priority)));
        }
        return new SlaPolicy(
                Map.copyOf(response),
                Map.copyOf(resolution),
                positiveDouble(KEY_EXAMINATION_FACTOR, siteCode, fallback.examinationFactor()),
                configuration.duration(KEY_ESCALATION_INTERVAL, siteCode, fallback.escalationInterval()),
                configuration.integer(KEY_MAX_ESCALATION, siteCode, fallback.maxEscalationLevel()));
    }

    /**
     * The priority at or above which a fault blocks the space it is in.
     *
     * <p>Defaults to {@link FaultPriority#HIGH}. A medium fault — a flickering light, a sticking door
     * — should not take an examination hall out of service, and a site that disagrees can say so.
     */
    public FaultPriority blockerThreshold(String siteCode) {
        return priority(KEY_BLOCKER_THRESHOLD, siteCode, FaultPriority.HIGH);
    }

    /** The priority at or above which closure evidence is mandatory. Defaults to HIGH. */
    public FaultPriority evidenceThreshold(String siteCode) {
        return priority(KEY_EVIDENCE_THRESHOLD, siteCode, FaultPriority.HIGH);
    }

    /**
     * How many pieces of evidence a work order of this priority needs before it can be closed.
     *
     * <p>Zero below the threshold. A count rather than a boolean because "one photograph" and "a
     * before, an after and a certificate" are both reasonable policies for different kinds of site,
     * and a boolean can only express the first.
     */
    public int evidenceRequiredFor(String siteCode, FaultPriority priority) {
        if (!priority.atLeast(evidenceThreshold(siteCode))) {
            return 0;
        }
        return Math.max(0, configuration.integer(KEY_EVIDENCE_COUNT, siteCode, 1));
    }

    /** How many schedules one preventive-generation run may raise work for. */
    public int generationBatchSize(String siteCode) {
        return Math.max(1, configuration.integer(KEY_GENERATION_BATCH, siteCode, 200));
    }

    private FaultPriority priority(String key, String siteCode, FaultPriority fallback) {
        return configuration.find(key, siteCode)
                .map(String::strip)
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .map(value -> {
                    try {
                        return FaultPriority.valueOf(value);
                    } catch (IllegalArgumentException unknown) {
                        // A typo in configuration must not take the module down, and must not silently
                        // widen a threshold either. Falling back to the default is the safe direction:
                        // it keeps the rule the service shipped with rather than removing it.
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    private double positiveDouble(String key, String siteCode, double fallback) {
        return configuration.find(key, siteCode)
                .map(String::strip)
                .map(value -> {
                    try {
                        double parsed = Double.parseDouble(value);
                        return parsed > 0d ? parsed : fallback;
                    } catch (NumberFormatException notANumber) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }
}
