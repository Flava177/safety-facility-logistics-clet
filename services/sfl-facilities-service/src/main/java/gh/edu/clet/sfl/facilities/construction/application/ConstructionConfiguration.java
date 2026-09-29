package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * S176's business thresholds, read out of runtime configuration at the moment they are needed.
 *
 * <p>Same contract as {@code BookingConfiguration}: nothing cached across a call, every read has a
 * fallback equal to what V21 seeds, so the module behaves identically on a database nobody seeded.
 *
 * <h2>The direction each fallback fails in</h2>
 *
 * The work-type catalogue and the permit-required list are the two values where a bad row matters.
 * An unknown work type is refused at registration rather than accepted, so a typo ("HOTWORK") cannot
 * put a project past the permit rule by not matching it. A permit-required list that parses to
 * nothing falls back to the seeded default rather than to "no work type needs a permit" - an emptied
 * configuration row must not quietly switch the S176-01 permit gate off.
 */
@Component
public class ConstructionConfiguration {

    static final String KEY_WORK_TYPES = "construction.work-types";
    static final String KEY_PERMIT_REQUIRED = "construction.permit-required.work-types";
    static final String KEY_ESCALATION_PERCENT = "construction.variation.escalation-threshold-percent";
    static final String KEY_LIABILITY_DAYS = "construction.defects-liability.period-days";
    static final String KEY_EXPIRY_WARNING_DAYS = "construction.compliance.expiry-warning-days";
    static final String KEY_SWEEP_BATCH = "construction.sweep.batch";

    static final String DEFAULT_PERMIT_REQUIRED =
            "HOT_WORK,WORK_AT_HEIGHT,CONFINED_SPACE,ELECTRICAL_ISOLATION,EXCAVATION";
    static final String DEFAULT_WORK_TYPES = "GENERAL_BUILDING,FIT_OUT,REFURBISHMENT,DEMOLITION,MECHANICAL,"
            + "PLUMBING,ROOFING," + DEFAULT_PERMIT_REQUIRED;

    private final RuntimeConfigurationPort configuration;

    public ConstructionConfiguration(RuntimeConfigurationPort configuration) {
        this.configuration = configuration;
    }

    /** Every work type a project may declare. The permit-required ones are always included. */
    public Set<String> workTypes(String siteCode) {
        Set<String> types = parse(configuration.find(KEY_WORK_TYPES, siteCode).orElse(DEFAULT_WORK_TYPES));
        if (types.isEmpty()) {
            types = parse(DEFAULT_WORK_TYPES);
        }
        types.addAll(permitRequiredWorkTypes(siteCode));
        return Set.copyOf(types);
    }

    /** Work types needing a current linked S164 permit before works start (SRS-SFL-S176-01). */
    public Set<String> permitRequiredWorkTypes(String siteCode) {
        Set<String> types = parse(configuration.find(KEY_PERMIT_REQUIRED, siteCode).orElse(DEFAULT_PERMIT_REQUIRED));
        return Set.copyOf(types.isEmpty() ? parse(DEFAULT_PERMIT_REQUIRED) : types);
    }

    /** Percentage of the approved baseline above which variations need escalated approval. */
    public BigDecimal escalationThresholdPercent(String siteCode) {
        return BigDecimal.valueOf(Math.max(0, configuration.integer(KEY_ESCALATION_PERCENT, siteCode, 10)));
    }

    public int defectsLiabilityDays(String siteCode) {
        return Math.max(0, configuration.integer(KEY_LIABILITY_DAYS, siteCode, 365));
    }

    public int expiryWarningDays(String siteCode) {
        return Math.max(0, configuration.integer(KEY_EXPIRY_WARNING_DAYS, siteCode, 30));
    }

    public int sweepBatchSize() {
        return Math.max(1, configuration.integer(KEY_SWEEP_BATCH, null, 200));
    }

    private static Set<String> parse(String raw) {
        Set<String> values = new TreeSet<>();
        Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .forEach(values::add);
        return values;
    }
}
