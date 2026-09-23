package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEvent;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessOverride;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessProvisioning;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessZone;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.OverrideStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ProvisioningStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ReaderHealth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one port for every S160a aggregate, following {@code VisitorRepository}'s "one port per
 * module, several aggregates" shape rather than one interface per aggregate - S160a's six
 * requirement areas are one cohesive workflow (an ingested event can raise an exception, which can
 * seed an incident, while zones and provisioning are configured alongside), not six unrelated slices.
 */
public interface AccessControlRepository {

    AccessEvent saveEvent(AccessEvent event);

    Optional<AccessEvent> findEventByExternalId(String source, String externalEventId);

    List<AccessEvent> findRecentEvents(String siteCode, String zoneCode, String personRef, int limit);

    /** The most recent GRANTED event for this person in this zone - the anti-passback check (S160a-06). */
    Optional<AccessEvent> findLastGrantedEvent(String siteCode, String zoneCode, String personRef);

    ReaderHealth saveReaderHealth(ReaderHealth health);

    Optional<ReaderHealth> findReaderHealth(String siteCode, String readerId);

    List<ReaderHealth> findReaderHealthBySite(String siteCode);

    AccessZone saveZone(AccessZone zone);

    Optional<AccessZone> findZone(UUID id);

    Optional<AccessZone> findZoneByCode(String siteCode, String zoneCode);

    List<AccessZone> findZonesBySite(String siteCode);

    AccessProvisioning saveProvisioning(AccessProvisioning provisioning);

    Optional<AccessProvisioning> findProvisioning(UUID id);

    List<AccessProvisioning> findProvisioningForPerson(String siteCode, String personRef);

    List<AccessProvisioning> findProvisioningByStatus(String siteCode, ProvisioningStatus status);

    AccessOverride saveOverride(AccessOverride override);

    Optional<AccessOverride> findOverride(UUID id);

    List<AccessOverride> findOverridesByStatus(String siteCode, OverrideStatus status);

    /** Every {@code ACTIVE} override across every site, whatever the site scope - the expiry sweep's feed. */
    List<AccessOverride> findActiveOverrides();

    AccessException saveException(AccessException exception);

    Optional<AccessException> findException(UUID id);

    List<AccessException> findExceptionsByStatus(String siteCode, ExceptionStatus status);
}
