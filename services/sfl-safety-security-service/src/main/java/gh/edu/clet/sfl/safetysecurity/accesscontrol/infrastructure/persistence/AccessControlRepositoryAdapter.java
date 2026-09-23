package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlRepository;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEvent;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEventKind;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** The one adapter behind {@link AccessControlRepository}, following {@code VisitorRepositoryAdapter}'s shape. */
@Repository
public class AccessControlRepositoryAdapter implements AccessControlRepository {

    private final AccessEventJpaRepository events;
    private final ReaderHealthJpaRepository readerHealth;
    private final AccessZoneJpaRepository zones;
    private final AccessProvisioningJpaRepository provisioning;
    private final AccessOverrideJpaRepository overrides;
    private final AccessExceptionJpaRepository exceptions;

    public AccessControlRepositoryAdapter(AccessEventJpaRepository events, ReaderHealthJpaRepository readerHealth,
            AccessZoneJpaRepository zones, AccessProvisioningJpaRepository provisioning,
            AccessOverrideJpaRepository overrides, AccessExceptionJpaRepository exceptions) {
        this.events = events;
        this.readerHealth = readerHealth;
        this.zones = zones;
        this.provisioning = provisioning;
        this.overrides = overrides;
        this.exceptions = exceptions;
    }

    @Override
    public AccessEvent saveEvent(AccessEvent event) {
        return events.save(AccessEventJpaEntity.from(event)).toDomain();
    }

    @Override
    public Optional<AccessEvent> findEventByExternalId(String source, String externalEventId) {
        return events.findBySourceAndExternalEventId(source, externalEventId).map(AccessEventJpaEntity::toDomain);
    }

    @Override
    public List<AccessEvent> findRecentEvents(String siteCode, String zoneCode, String personRef, int limit) {
        return events.findRecent(siteCode, zoneCode, personRef, PageRequest.of(0, Math.max(1, limit))).stream()
                .map(AccessEventJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<AccessEvent> findLastGrantedEvent(String siteCode, String zoneCode, String personRef) {
        return events.findGrantedForPerson(siteCode, zoneCode, personRef, AccessEventKind.GRANTED,
                PageRequest.of(0, 1)).stream().map(AccessEventJpaEntity::toDomain).findFirst();
    }

    @Override
    public ReaderHealth saveReaderHealth(ReaderHealth health) {
        ReaderHealthJpaEntity entity = readerHealth.findById(health.id()).orElseGet(ReaderHealthJpaEntity::new);
        entity.apply(health);
        return readerHealth.save(entity).toDomain();
    }

    @Override
    public Optional<ReaderHealth> findReaderHealth(String siteCode, String readerId) {
        return readerHealth.findBySiteCodeAndReaderId(siteCode, readerId).map(ReaderHealthJpaEntity::toDomain);
    }

    @Override
    public List<ReaderHealth> findReaderHealthBySite(String siteCode) {
        return readerHealth.findBySiteCode(siteCode).stream().map(ReaderHealthJpaEntity::toDomain).toList();
    }

    @Override
    public AccessZone saveZone(AccessZone zone) {
        AccessZoneJpaEntity entity = zones.findById(zone.id()).orElseGet(AccessZoneJpaEntity::new);
        entity.apply(zone);
        return zones.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<AccessZone> findZone(UUID id) {
        return zones.findById(id).map(AccessZoneJpaEntity::toDomain);
    }

    @Override
    public Optional<AccessZone> findZoneByCode(String siteCode, String zoneCode) {
        return zones.findBySiteCodeAndZoneCode(siteCode, zoneCode).map(AccessZoneJpaEntity::toDomain);
    }

    @Override
    public List<AccessZone> findZonesBySite(String siteCode) {
        return zones.findBySiteCode(siteCode).stream().map(AccessZoneJpaEntity::toDomain).toList();
    }

    @Override
    public AccessProvisioning saveProvisioning(AccessProvisioning provisioningRecord) {
        AccessProvisioningJpaEntity entity = provisioning.findById(provisioningRecord.id())
                .orElseGet(AccessProvisioningJpaEntity::new);
        entity.apply(provisioningRecord);
        return provisioning.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<AccessProvisioning> findProvisioning(UUID id) {
        return provisioning.findById(id).map(AccessProvisioningJpaEntity::toDomain);
    }

    @Override
    public List<AccessProvisioning> findProvisioningForPerson(String siteCode, String personRef) {
        return provisioning.findBySiteCodeAndPersonRef(siteCode, personRef).stream()
                .map(AccessProvisioningJpaEntity::toDomain).toList();
    }

    @Override
    public List<AccessProvisioning> findProvisioningByStatus(String siteCode, ProvisioningStatus status) {
        return provisioning.findBySiteCodeAndStatus(siteCode, status).stream()
                .map(AccessProvisioningJpaEntity::toDomain).toList();
    }

    @Override
    public AccessOverride saveOverride(AccessOverride override) {
        AccessOverrideJpaEntity entity = overrides.findById(override.id()).orElseGet(AccessOverrideJpaEntity::new);
        entity.apply(override);
        return overrides.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<AccessOverride> findOverride(UUID id) {
        return overrides.findById(id).map(AccessOverrideJpaEntity::toDomain);
    }

    @Override
    public List<AccessOverride> findOverridesByStatus(String siteCode, OverrideStatus status) {
        return overrides.findBySiteCodeAndStatus(siteCode, status).stream()
                .map(AccessOverrideJpaEntity::toDomain).toList();
    }

    @Override
    public List<AccessOverride> findActiveOverrides() {
        return overrides.findByStatus(OverrideStatus.ACTIVE).stream().map(AccessOverrideJpaEntity::toDomain).toList();
    }

    @Override
    public AccessException saveException(AccessException exception) {
        AccessExceptionJpaEntity entity = exceptions.findById(exception.id())
                .orElseGet(AccessExceptionJpaEntity::new);
        entity.apply(exception);
        return exceptions.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<AccessException> findException(UUID id) {
        return exceptions.findById(id).map(AccessExceptionJpaEntity::toDomain);
    }

    @Override
    public List<AccessException> findExceptionsByStatus(String siteCode, ExceptionStatus status) {
        return exceptions.findBySiteCodeAndStatus(siteCode, status).stream()
                .map(AccessExceptionJpaEntity::toDomain).toList();
    }
}
