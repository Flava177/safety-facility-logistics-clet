package gh.edu.clet.sfl.fleetlogistics.fleet.domain.event;

/**
 * The fleet integration event catalogue.
 *
 * <p>Names follow {@code docs/integration/event-catalog.md} exactly:
 * {@code sfl.{platform}.{event-name}.v{version}} with platform {@code ftlmp}. The numeric
 * {@link #version()} always mirrors the {@code .vN} suffix, so the wire name and the stored
 * {@code event_version} column can never disagree.
 *
 * <p>Gap report C-03 records that the facilities and asset-visibility services currently publish a
 * different naming scheme; S166 follows the catalog.
 */
public enum FleetEventType {

    VEHICLE_CREATED("sfl.ftlmp.vehicle-created.v1", "Vehicle"),
    VEHICLE_UPDATED("sfl.ftlmp.vehicle-updated.v1", "Vehicle"),
    VEHICLE_LIFECYCLE_CHANGED("sfl.ftlmp.vehicle-lifecycle-changed.v1", "Vehicle"),
    VEHICLE_READINESS_CHANGED("sfl.ftlmp.vehicle-readiness-changed.v1", "Vehicle"),
    VEHICLE_AVAILABILITY_CHANGED("sfl.ftlmp.vehicle-availability-changed.v1", "Vehicle"),
    VEHICLE_COMPLIANCE_EXPIRING("sfl.ftlmp.vehicle-compliance-expiring.v1", "ComplianceDocument"),
    VEHICLE_COMPLIANCE_EXPIRED("sfl.ftlmp.vehicle-compliance-expired.v1", "ComplianceDocument"),
    VEHICLE_SERVICE_DUE("sfl.ftlmp.vehicle-service-due.v1", "Vehicle"),
    VEHICLE_SERVICE_OVERDUE("sfl.ftlmp.vehicle-service-overdue.v1", "Vehicle"),
    DRIVER_REGISTERED("sfl.ftlmp.driver-registered.v1", "DriverProfileReference"),
    DRIVER_ELIGIBILITY_CHANGED("sfl.ftlmp.driver-eligibility-changed.v1", "DriverProfileReference"),
    VEHICLE_ASSIGNED("sfl.ftlmp.vehicle-assigned.v1", "Trip"),
    TRIP_REASSIGNED("sfl.ftlmp.trip-reassigned.v1", "Trip"),
    TRIP_ACKNOWLEDGED("sfl.ftlmp.trip-acknowledged.v1", "Trip"),
    /**
     * A driver cannot take an assigned trip as scheduled.
     *
     * <p>Published separately from {@code TRIP_ACKNOWLEDGED} because it is the one a dispatcher must
     * act on. A single event carrying a state field would put "all is well" and "this vehicle may not
     * go out" on the same subscription, and whoever subscribed would have to filter correctly forever.
     */
    TRIP_DEFERRED("sfl.ftlmp.trip-deferred.v1", "Trip"),
    TRIP_CANCELLED("sfl.ftlmp.trip-cancelled.v1", "Trip"),
    TRIP_COMPLETED("sfl.ftlmp.trip-completed.v1", "Trip"),
    VEHICLE_INSPECTION_FAILED("sfl.ftlmp.vehicle-inspection-failed.v1", "VehicleInspection"),
    FLEET_WORKFLOW_ESCALATED("sfl.ftlmp.fleet-workflow-escalated.v1", "FleetWorkflowItem"),
    FLEET_EVIDENCE_REGISTERED("sfl.ftlmp.fleet-evidence-registered.v1", "EvidenceReference"),
    FLEET_AUDIT_INTEGRITY_FAILED("sfl.ftlmp.fleet-audit-integrity-failed.v1", "AuditChain"),
    VEHICLE_LOCATION_RECEIVED("sfl.ftlmp.vehicle-location-received.v1", "Vehicle"),
    FUEL_TRANSACTION_RECEIVED("sfl.ftlmp.fuel-transaction-received.v1", "FuelTransaction"),
    FUEL_TRANSACTION_RECONCILED("sfl.ftlmp.fuel-transaction-reconciled.v1", "FuelTransaction"),
    FUEL_TRANSACTION_REJECTED("sfl.ftlmp.fuel-transaction-rejected.v1", "FuelTransaction"),
    FUEL_EXCEPTION_DETECTED("sfl.ftlmp.fuel-exception-detected.v1", "FuelAnomalyCase"),
    FUEL_ANOMALY_ASSIGNED("sfl.ftlmp.fuel-anomaly-assigned.v1", "FuelAnomalyCase"),
    FUEL_ANOMALY_APPROVED("sfl.ftlmp.fuel-anomaly-approved.v1", "FuelAnomalyCase"),
    FUEL_ANOMALY_REJECTED("sfl.ftlmp.fuel-anomaly-rejected.v1", "FuelAnomalyCase"),
    FUEL_ANOMALY_ESCALATED("sfl.ftlmp.fuel-anomaly-escalated.v1", "FuelAnomalyCase"),
    DRIVER_LOGBOOK_SUBMITTED("sfl.ftlmp.driver-logbook-submitted.v1", "DriverLogbook"),
    DRIVER_LOGBOOK_RETURNED("sfl.ftlmp.driver-logbook-returned.v1", "DriverLogbook"),
    DRIVER_LOGBOOK_APPROVED("sfl.ftlmp.driver-logbook-approved.v1", "DriverLogbook"),
    DRIVER_LOGBOOK_OVERDUE("sfl.ftlmp.driver-logbook-overdue.v1", "DriverLogbook"),

    // Mailroom / Courier and Dispatch Tracking events (SRS S171). dispatch-created and dispatch-received
    // are the pre-seeded catalog names; the remainder are justified S171 lifecycle events.
    DISPATCH_ITEM_REGISTERED("sfl.ftlmp.dispatch-item-registered.v1", "CourierItem"),
    INBOUND_ITEM_REGISTERED("sfl.ftlmp.inbound-item-registered.v1", "CourierItem"),
    INBOUND_ITEM_DISTRIBUTED("sfl.ftlmp.inbound-item-distributed.v1", "CourierItem"),
    INBOUND_ITEM_UNDELIVERED("sfl.ftlmp.inbound-item-undelivered.v1", "CourierItem"),
    DISPATCH_CREATED("sfl.ftlmp.dispatch-created.v1", "Dispatch"),
    DISPATCH_DISPATCHED("sfl.ftlmp.dispatch-dispatched.v1", "Dispatch"),
    CUSTODY_HANDOVER_RECORDED("sfl.ftlmp.custody-handover-recorded.v1", "CustodyHandover"),
    CUSTODY_GAP_DETECTED("sfl.ftlmp.custody-gap-detected.v1", "DispatchExceptionCase"),
    DISPATCH_RECEIVED("sfl.ftlmp.dispatch-received.v1", "DispatchReceipt"),
    DISPATCH_RECEIPT_VARIANCE("sfl.ftlmp.dispatch-receipt-variance.v1", "DispatchReceipt"),
    DISPATCH_RETURN_RECONCILED("sfl.ftlmp.dispatch-return-reconciled.v1", "ReturnReconciliation"),
    DISPATCH_RETURN_DISCREPANCY("sfl.ftlmp.dispatch-return-discrepancy.v1", "ReturnReconciliation"),
    DISPATCH_SCAN_MISMATCH("sfl.ftlmp.dispatch-scan-mismatch.v1", "DispatchExceptionCase"),
    DISPATCH_EXCEPTION_ASSIGNED("sfl.ftlmp.dispatch-exception-assigned.v1", "DispatchExceptionCase"),
    DISPATCH_EXCEPTION_APPROVED("sfl.ftlmp.dispatch-exception-approved.v1", "DispatchExceptionCase"),
    DISPATCH_EXCEPTION_REJECTED("sfl.ftlmp.dispatch-exception-rejected.v1", "DispatchExceptionCase"),
    DISPATCH_EXCEPTION_ESCALATED("sfl.ftlmp.dispatch-exception-escalated.v1", "DispatchExceptionCase"),
    DISPATCH_SECURITY_VARIANCE("sfl.ftlmp.dispatch-security-variance.v1", "DispatchExceptionCase");

    private final String eventType;
    private final String defaultAggregateType;

    FleetEventType(String eventType, String defaultAggregateType) {
        this.eventType = eventType;
        this.defaultAggregateType = defaultAggregateType;
    }

    /** The wire event type, e.g. {@code sfl.ftlmp.vehicle-created.v1}. */
    public String eventType() {
        return eventType;
    }

    /** The RabbitMQ routing key, e.g. {@code ftlmp.vehicle-created.v1}. */
    public String routingKey() {
        return eventType.substring("sfl.".length());
    }

    /** The numeric version parsed from the {@code .vN} suffix. */
    public int version() {
        return Integer.parseInt(eventType.substring(eventType.lastIndexOf(".v") + 2));
    }

    public String defaultAggregateType() {
        return defaultAggregateType;
    }
}
