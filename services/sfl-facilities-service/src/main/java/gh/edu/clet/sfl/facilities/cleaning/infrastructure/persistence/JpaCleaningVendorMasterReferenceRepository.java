package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningVendorMasterReferenceRepository extends JpaRepository<CleaningVendorMasterReferenceRecord, UUID> {

    @Query("""
            select m from CleaningVendorMasterReferenceRecord m
             where m.siteCode = :siteCode and m.vendorMasterReference = :reference
            """)
    Optional<CleaningVendorMasterReferenceRecord> findByReference(@Param("siteCode") String siteCode,
            @Param("reference") String reference);

    @Query("""
            select m from CleaningVendorMasterReferenceRecord m where m.siteCode = :siteCode
             order by m.vendorMasterReference asc
            """)
    List<CleaningVendorMasterReferenceRecord> findForSite(@Param("siteCode") String siteCode);
}
