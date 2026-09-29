package gh.edu.clet.sfl.facilities.energy.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One published sustainability KPI - the S225 Analytics read-model row (SRS-SFL-S157-03).
 *
 * <p>Every field a reader needs to judge the number travels with it: the period, whether the period has
 * ended, how many readings were expected and received, and the flag. A consumer that shows
 * {@code consumption} without {@code completenessFlag} is misusing the contract, and the event payload and
 * API response both put the two side by side for that reason.
 *
 * @param siteCode the site, or {@code *} for a {@link KpiScope#CLUSTER} row
 * @param trendPct change against the previous period of the same scope; {@code null} with no previous KPI
 * @param carbonKgCo2e {@code null} unless an emission factor applied
 * @param revision starts at 1; incremented each time a sweep recomputes the same scope and period
 */
public record SustainabilityKpi(
        UUID id,
        String kpiKey,
        String siteCode,
        KpiScope scope,
        String buildingCode,
        Utility utility,
        EnergyPeriod period,
        boolean periodClosed,
        BigDecimal consumption,
        BigDecimal previousConsumption,
        BigDecimal trendPct,
        BigDecimal carbonKgCo2e,
        EmissionFactorStatus emissionFactorStatus,
        int expectedReadings,
        int receivedReadings,
        BigDecimal completenessPct,
        CompletenessFlag completenessFlag,
        BigDecimal minimumCompletenessPct,
        int revision,
        Instant computedAt,
        RecordMetadata metadata) {

    public static final String CLUSTER_SITE = "*";

    public enum KpiScope {
        SITE,
        BUILDING,
        CLUSTER
    }

    /** Whether the carbon figure used a configured factor for all, some or none of its consumption. */
    public enum EmissionFactorStatus {
        APPLIED,
        PARTIAL,
        NOT_CONFIGURED
    }

    public static String keyOf(KpiScope scope, String siteCode, String buildingCode, Utility utility,
            EnergyPeriod period) {
        return scope + ":" + siteCode + ":" + (buildingCode == null ? "-" : buildingCode) + ":" + utility + ":"
                + period.type() + ":" + period.start();
    }
}
