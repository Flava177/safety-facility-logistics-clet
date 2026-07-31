package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaMaintenanceVendorRepository extends JpaRepository<MaintenanceVendorRecord, UUID> {

    Optional<MaintenanceVendorRecord> findBySiteCodeAndVendorCode(String siteCode, String vendorCode);

    @Query("""
            select v from MaintenanceVendorRecord v
            where (:siteCode is null or v.siteCode = :siteCode)
            order by v.vendorCode asc
            """)
    List<MaintenanceVendorRecord> search(@Param("siteCode") String siteCode);
}
