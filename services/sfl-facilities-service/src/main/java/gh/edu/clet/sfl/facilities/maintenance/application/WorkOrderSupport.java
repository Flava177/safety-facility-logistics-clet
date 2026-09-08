package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceVendor;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-cutting lookups and guards shared by {@link WorkOrderApplicationService},
 * {@link WorkOrderLifecycleCommands} and {@link WorkOrderPartCommands} - the per-record vendor
 * narrowing, work-order lookup, event publication and the assignable-vendor check, each needed by
 * more than one of those three classes. Split out so the split itself does not duplicate them.
 *
 * <p>Package-private: this is glue between the three work-order classes, not a type another module
 * has any business depending on.
 */
final class WorkOrderSupport {

    private final MaintenanceRepository maintenance;
    private final FacilitiesRepository facilities;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    WorkOrderSupport(MaintenanceRepository maintenance, FacilitiesRepository facilities, AuditPort audit,
            ServiceOutbox outbox, Clock clock) {
        this.maintenance = maintenance;
        this.facilities = facilities;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    WorkOrder requireWorkOrder(UUID id) {
        return maintenance.findWorkOrder(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Work order", id));
    }

    /**
     * The per-record narrowing for contractors - see {@link WorkOrderApplicationService}'s class
     * comment. Applied to reads and writes alike: a vendor who could not <em>see</em> an order but
     * could still transition it by guessing its id would make the read-side narrowing decorative.
     */
    void assertVisible(ActorContext actor, WorkOrder order, SourceChannel channel) {
        String filter = vendorFilter(actor);
        if (filter == null || filter.equals(order.assignedTo())) {
            return;
        }
        audit.recordDenial(actor, channel, "WorkOrder", order.id().toString(), order.siteCode(),
                "A vendor technician may act only on work orders assigned to them");
        throw new FacilitiesException.UnauthorizedScopeException(
                "You may only view work orders assigned to you.");
    }

    /** The {@code assignedTo} a query must be narrowed to, or {@code null} for no narrowing. */
    String vendorFilter(ActorContext actor) {
        Set<SflRole> roles = actor.principal().roles();
        boolean onlyVendor = roles.contains(SflRole.VENDOR_TECHNICIAN)
                && roles.stream().allMatch(role -> role == SflRole.VENDOR_TECHNICIAN);
        return onlyVendor ? actor.actorId() : null;
    }

    MaintenanceVendor requireAssignableVendor(UUID vendorId, String siteCode, Instant at) {
        MaintenanceVendor vendor = maintenance.findVendor(vendorId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Vendor", vendorId));
        if (!vendor.siteCode().equals(siteCode)) {
            throw new FacilitiesException.ValidationFailedException(
                    "Vendor " + vendor.vendorCode() + " is not registered at site " + siteCode + ".");
        }
        String reason = vendor.unassignableReason(at.atZone(ZoneOffset.UTC).toLocalDate());
        if (reason != null) {
            throw new FacilitiesException.ValidationFailedException(reason);
        }
        return vendor;
    }

    OperatingMode operatingModeOf(String siteCode) {
        return facilities.findSiteByCode(siteCode).map(Site::operatingMode).orElse(OperatingMode.ROUTINE);
    }

    void publish(String eventType, WorkOrder order, ActorContext actor) {
        outbox.record(eventType, 1, "WorkOrder", order.id(), order.siteCode(), actor.correlationId(),
                actor.actorId(), order);
    }

    Instant now() {
        return clock.instant();
    }
}
