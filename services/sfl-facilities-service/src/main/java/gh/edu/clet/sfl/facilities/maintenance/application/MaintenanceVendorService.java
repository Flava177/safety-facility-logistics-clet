package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceVendor;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The local vendor register — SRS-SFL-S153-01 ("vendor assignment").
 *
 * <p>Small on purpose. This is not the procurement master: it holds enough to assign work, know the
 * contracted response time and see whether the contract has run out, and it carries the procurement
 * system's own identifier so the two can be reconciled when S153-04's integration is built. Anything
 * more would be a second source of truth for supplier data that nobody has agreed to maintain.
 */
@Service
public class MaintenanceVendorService {

    private final MaintenanceRepository maintenance;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public MaintenanceVendorService(MaintenanceRepository maintenance, FacilitiesAuthorization authorization,
            AuditPort audit, IdempotencyPort idempotency, ServiceOutbox outbox, Clock clock) {
        this.maintenance = maintenance;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public MaintenanceVendor register(MaintenanceCommands.RegisterVendor command) {
        ActorContext actor = command.actor();
        String siteCode = EstateCodes.normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_VENDOR_MANAGE, siteCode, command.channel(),
                "MaintenanceVendor", command.vendorCode());

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<MaintenanceVendor> replayed = idempotency
                    .findExistingResult("register-maintenance-vendor", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(maintenance::findVendor);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }

        String vendorCode = EstateCodes.normalize(command.vendorCode());
        maintenance.findVendorByCode(siteCode, vendorCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("maintenance vendor", vendorCode,
                        siteCode);
            }
        });

        Instant at = now();
        MaintenanceVendor vendor = maintenance.saveVendor(MaintenanceVendor.register(UUID.randomUUID(),
                siteCode, vendorCode, command.name(), command.specialisation(), command.contactName(),
                command.contactEmail(), command.contactPhone(), command.responseHours(),
                command.contractReference(), command.contractExpiresOn(), command.externalVendorId(),
                actor.actorId(), at, command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.MAINTENANCE_VENDOR_REGISTERED, "MaintenanceVendor",
                vendor.id().toString(), vendor.siteCode(), null, vendor);
        outbox.record("sfl.ifimp.maintenance-vendor-registered.v1", 1, "MaintenanceVendor", vendor.id(),
                vendor.siteCode(), actor.correlationId(), actor.actorId(), vendor);
        idempotency.recordResult("register-maintenance-vendor", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), vendor.id(), vendor.siteCode(),
                actor.actorId());
        return vendor;
    }

    @Transactional
    public MaintenanceVendor update(MaintenanceCommands.UpdateVendor command) {
        ActorContext actor = command.actor();
        MaintenanceVendor vendor = requireVendor(command.vendorId());
        authorization.require(actor, SflPermission.FACILITIES_VENDOR_MANAGE, vendor.siteCode(),
                command.channel(), "MaintenanceVendor", vendor.id().toString());
        vendor.metadata().requireVersion(command.expectedVersion(), "Maintenance vendor", vendor.id());

        MaintenanceVendor updated = maintenance.saveVendor(vendor.update(command.name(),
                command.specialisation(), command.contactName(), command.contactEmail(),
                command.contactPhone(), command.responseHours(), command.contractReference(),
                command.contractExpiresOn(), actor.actorId(), now(), command.channel(),
                actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.MAINTENANCE_VENDOR_UPDATED, "MaintenanceVendor",
                updated.id().toString(), updated.siteCode(), vendor, updated);
        return updated;
    }

    @Transactional
    public MaintenanceVendor changeLifecycle(MaintenanceCommands.ChangeVendorLifecycle command) {
        ActorContext actor = command.actor();
        MaintenanceVendor vendor = requireVendor(command.vendorId());
        authorization.require(actor, SflPermission.FACILITIES_VENDOR_MANAGE, vendor.siteCode(),
                command.channel(), "MaintenanceVendor", vendor.id().toString());
        vendor.metadata().requireVersion(command.expectedVersion(), "Maintenance vendor", vendor.id());

        MaintenanceVendor changed = maintenance.saveVendor(vendor.changeLifecycle(command.lifecycleStatus(),
                actor.actorId(), now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.MAINTENANCE_VENDOR_LIFECYCLE_CHANGED,
                "MaintenanceVendor", changed.id().toString(), changed.siteCode(), vendor, changed);
        return changed;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceVendor> list(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_VENDOR_READ, channel, "MaintenanceVendor",
                "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "MaintenanceVendor");
        return authorization.filterBySite(actor, maintenance.findVendors(siteCode),
                MaintenanceVendor::siteCode);
    }

    @Transactional(readOnly = true)
    public MaintenanceVendor findById(UUID id, ActorContext actor, SourceChannel channel) {
        MaintenanceVendor vendor = requireVendor(id);
        authorization.require(actor, SflPermission.FACILITIES_VENDOR_READ, vendor.siteCode(), channel,
                "MaintenanceVendor", id.toString());
        return vendor;
    }

    private MaintenanceVendor requireVendor(UUID id) {
        return maintenance.findVendor(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Maintenance vendor", id));
    }

    private Instant now() {
        return clock.instant();
    }
}
