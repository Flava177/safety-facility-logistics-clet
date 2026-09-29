package gh.edu.clet.sfl.facilities.construction.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionEvents;
import gh.edu.clet.sfl.facilities.construction.application.ports.SiteAccessPort;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Contractor site access, handed to SSEMP's Access Control (S160a) and Visitor Management (S160) by
 * event - SRS-SFL-S176-02.
 *
 * <p>Recorded, and honest about it. The request and every suspension are written to the outbox as
 * {@code sfl.ifimp.contractor-site-access-requested.v1} / {@code -suspended.v1}; the drainer ships
 * them to the broker; <strong>no SSEMP consumer exists for either</strong>, so nothing opens or closes a
 * door because of them. {@link Dispatch#enforced()} is therefore always {@code false}, the grant is
 * stored {@code RECORDED_NOT_ENFORCED}, and a suspension is logged at WARN so it is not only in a
 * table nobody reads.
 *
 * <p>When S160a consumes these, the change is an acknowledgement path back into S176 that sets
 * {@code ENFORCED} - not a change to this class's contract.
 */
@Component
public class OutboxSiteAccessAdapter implements SiteAccessPort {

    static final String PROVIDER = "SSEMP-S160A-OUTBOX-NO-CONSUMER";

    private static final Logger log = LoggerFactory.getLogger(OutboxSiteAccessAdapter.class);

    private final ServiceOutbox outbox;

    public OutboxSiteAccessAdapter(ServiceOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public Dispatch requestAccess(SiteAccessGrant grant, Contractor contractor, ActorContext actor) {
        outbox.record(ConstructionEvents.SITE_ACCESS_REQUESTED, 1, "SiteAccessGrant", grant.id(), grant.siteCode(),
                actor.correlationId(), actor.actorId(), ConstructionEvents.payload("grantId", grant.id(),
                        "contractorId", contractor.id(), "contractorCode", contractor.contractorCode(),
                        "vendorReference", contractor.vendorReference(), "projectId", grant.projectId(),
                        "accessScope", grant.accessScope(), "validFrom", grant.validFrom(), "validTo",
                        grant.validTo(), "enforcement", SiteAccessGrant.Enforcement.RECORDED_NOT_ENFORCED));
        return new Dispatch(PROVIDER, false);
    }

    @Override
    public Dispatch suspendAccess(SiteAccessGrant grant, Contractor contractor, String reason, ActorContext actor) {
        outbox.record(ConstructionEvents.SITE_ACCESS_SUSPENDED, 1, "SiteAccessGrant", grant.id(), grant.siteCode(),
                actor.correlationId(), actor.actorId(), ConstructionEvents.payload("grantId", grant.id(),
                        "contractorId", contractor.id(), "contractorCode", contractor.contractorCode(),
                        "vendorReference", contractor.vendorReference(), "projectId", grant.projectId(),
                        "reasonCode", "CONTRACTOR_COMPLIANCE_LAPSED", "suspendedAt", grant.suspendedAt(),
                        "enforcement", SiteAccessGrant.Enforcement.RECORDED_NOT_ENFORCED));
        log.warn("Contractor {} site-access grant {} at {} suspended for lapsed compliance. RECORDED, NOT ENFORCED: "
                + "no S160a consumer exists; the site must stop the contractor by hand.", contractor.contractorCode(),
                grant.id(), grant.siteCode());
        return new Dispatch(PROVIDER, false);
    }
}
