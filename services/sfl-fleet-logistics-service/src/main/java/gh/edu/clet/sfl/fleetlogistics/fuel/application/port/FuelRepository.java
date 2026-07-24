package gh.edu.clet.sfl.fleetlogistics.fuel.application.port;

import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.DriverLogbook;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPolicy;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for S168_fuel. It exposes domain records, never JDBC/JPA types. */
public interface FuelRepository {
    FuelPolicy savePolicy(FuelPolicy policy);
    Optional<FuelPolicy> findApplicablePolicy(String siteCode, Instant at);
    List<FuelPolicy> findPolicies(List<String> sites);
    FuelTransaction saveTransaction(FuelTransaction transaction);
    Optional<FuelTransaction> findTransaction(UUID id);
    Optional<FuelTransaction> findProviderTransaction(String siteCode, String sourceSystem, String providerId);
    List<FuelTransaction> findTransactions(List<String> sites, String site, FuelTransaction.Status status, UUID vehicleId, UUID driverId, Instant from, Instant to, int limit);
    DriverLogbook saveLogbook(DriverLogbook logbook);
    Optional<DriverLogbook> findLogbook(UUID id);
    List<DriverLogbook> findLogbooks(List<String> sites, String actorId, boolean ownOnly, DriverLogbook.Status status, int limit);
    FuelAnomalyCase saveAnomaly(FuelAnomalyCase anomaly);
    Optional<FuelAnomalyCase> findAnomaly(UUID id);
    Optional<FuelAnomalyCase> findAnomaly(UUID transactionId, FuelAnomalyCase.Type type);
    Optional<FuelAnomalyCase> findAnomalyForTrip(UUID tripId, FuelAnomalyCase.Type type);
    List<FuelAnomalyCase> findAnomalies(List<String> sites, FuelAnomalyCase.Status status, Instant dueBefore, int limit);
    /** Most recent active transaction for the vehicle strictly before {@code before}, for consumption and cost-variance rules. */
    Optional<FuelTransaction> findPreviousTransaction(String siteCode, UUID vehicleId, Instant before);
    /** Count of anomaly cases raised for the same vehicle or driver since {@code since}, for repeated-pattern detection. */
    long countRecentAnomalies(List<String> sites, UUID vehicleId, UUID driverId, Instant since);
    /** Latest non-cancelled driver logbook associated with the trip, for logbook/fuel mismatch detection. */
    Optional<DriverLogbook> findLogbookForTrip(UUID tripId);
    void saveReconciliation(UUID id, UUID transactionId, UUID policyId, Integer policyVersion, String outcome,
            BigDecimal consumption, Instant evaluatedAt, String actor, Map<String, Object> ruleResults,
            String correlationId);
    Map<String, Object> dashboard(List<String> sites, String siteCode);
}
