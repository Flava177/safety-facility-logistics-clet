package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AvampAssetProjection;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps S156's AVAMP projection current from AVAMP's events - SRS-SFL-S156-04.
 *
 * <p>Called by the inbound event handler for {@code sfl.avamp.asset-registered.v1} and
 * {@code sfl.avamp.asset-location-changed.v1}. An upsert keyed on the AVAMP asset id, ordered by AVAMP's own
 * {@code updatedAt}: a redelivered older event is ignored rather than moving an asset back to where it was.
 * Audited, because whether a device may be registered depends on what this table says.
 */
@Service
public class AvampAssetProjectionService {

    private final BuildingSystemsRepository repository;
    private final AuditPort audit;
    private final Clock clock;

    public AvampAssetProjectionService(BuildingSystemsRepository repository, AuditPort audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    /** AVAMP's statement about one asset, as the event carried it. */
    public record AvampAssetFact(String eventType, String avampAssetId, String siteCode, String assetCode,
            String name, String category, String status, String locationType, String locationReference,
            Instant updatedAt) {
    }

    /** @return the projection after the event, or empty when the event was older than what is held */
    @Transactional
    public Optional<AvampAssetProjection> apply(AvampAssetFact fact, ActorContext actor) {
        Instant now = clock.instant();
        Optional<AvampAssetProjection> existing = repository.findAvampAsset(fact.avampAssetId());
        if (existing.isPresent() && !existing.get().isSupersededBy(fact.updatedAt())) {
            return Optional.empty();
        }
        AvampAssetProjection next = existing
                .map(held -> new AvampAssetProjection(held.id(), held.avampAssetId(), fact.siteCode(),
                        orElse(fact.assetCode(), held.assetCode()), orElse(fact.name(), held.name()),
                        orElse(fact.category(), held.category()), orElse(fact.status(), held.assetStatus()),
                        orElse(fact.locationType(), held.locationType()),
                        orElse(fact.locationReference(), held.locationReference()), fact.eventType(), fact.updatedAt(),
                        held.metadata().modifiedBy(actor.actorId(), now, SourceChannel.INTEGRATION,
                                actor.correlationId())))
                .orElseGet(() -> new AvampAssetProjection(UUID.randomUUID(), fact.avampAssetId(), fact.siteCode(),
                        fact.assetCode(), fact.name(), fact.category(), fact.status(), fact.locationType(),
                        fact.locationReference(), fact.eventType(), fact.updatedAt(),
                        RecordMetadata.createdBy(actor.actorId(), now, SourceChannel.INTEGRATION,
                                actor.correlationId())));
        AvampAssetProjection saved = repository.saveAvampAsset(next);
        audit.record(actor, SourceChannel.INTEGRATION, AuditAction.BMS_AVAMP_ASSET_PROJECTED, "BmsAvampAsset",
                saved.avampAssetId(), saved.siteCode(), existing.orElse(null), saved);
        return Optional.of(saved);
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
