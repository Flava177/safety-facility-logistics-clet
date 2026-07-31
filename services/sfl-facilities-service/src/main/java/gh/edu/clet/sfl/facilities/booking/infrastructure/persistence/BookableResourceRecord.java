package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA mapping for {@link BookableResource}. Column names match V10 exactly. */
@Entity
@Table(name = "bookable_resources", schema = "facilities")
public class BookableResourceRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "resource_code", nullable = false, length = 80)
    private String resourceCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResourceCategory category;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "home_room_id")
    private UUID homeRoomId;
    @Column(name = "asset_id")
    private UUID assetId;
    @Column(name = "requires_setup", nullable = false)
    private boolean requiresSetup;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected BookableResourceRecord() {
    }

    public void apply(BookableResource resource) {
        id = resource.id();
        siteCode = resource.siteCode();
        resourceCode = resource.resourceCode();
        name = resource.name();
        category = resource.category();
        description = resource.description();
        quantity = resource.quantity();
        homeRoomId = resource.homeRoomId();
        assetId = resource.assetId();
        requiresSetup = resource.requiresSetup();
        lifecycleStatus = resource.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(resource.metadata());
    }

    public BookableResource toDomain() {
        return new BookableResource(id, siteCode, resourceCode, name, category, description, quantity,
                homeRoomId, assetId, requiresSetup, lifecycleStatus, metadata.toDomain());
    }
}
