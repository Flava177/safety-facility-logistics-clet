package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RuntimeConfigurationRepository extends JpaRepository<RuntimeConfigurationEntity, UUID> {

    /**
     * The active value scoped to one site.
     *
     * <p>"Active" is {@code effective_to is null}. A partial unique index in V5 guarantees at most one
     * such row per key and scope, so this returns at most one without an ordering tie-break.
     */
    @Query("""
            select c from RuntimeConfigurationEntity c
            where c.configKey = :key and c.effectiveTo is null and c.siteCode = :siteCode
            """)
    Optional<RuntimeConfigurationEntity> findActiveForSite(@Param("key") String key,
            @Param("siteCode") String siteCode);

    /** The active platform default — the value used when no site override exists. */
    @Query("""
            select c from RuntimeConfigurationEntity c
            where c.configKey = :key and c.effectiveTo is null and c.siteCode is null
            """)
    Optional<RuntimeConfigurationEntity> findActiveDefault(@Param("key") String key);

    /** Every active value visible for a scope: the site's own overrides plus the platform defaults. */
    @Query("""
            select c from RuntimeConfigurationEntity c
            where c.effectiveTo is null
              and (:siteCode is null or c.siteCode is null or c.siteCode = :siteCode)
            order by c.configKey asc
            """)
    List<RuntimeConfigurationEntity> findAllActive(@Param("siteCode") String siteCode);
}
