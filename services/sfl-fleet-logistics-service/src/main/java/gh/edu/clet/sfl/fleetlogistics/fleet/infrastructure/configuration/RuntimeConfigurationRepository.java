package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.configuration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RuntimeConfigurationRepository extends JpaRepository<RuntimeConfigurationEntity, UUID> {

    /** Every currently effective value. Site-specific rows override the platform default. */
    @Query("select c from RuntimeConfigurationEntity c where c.effectiveTo is null")
    List<RuntimeConfigurationEntity> findAllEffective();
}
