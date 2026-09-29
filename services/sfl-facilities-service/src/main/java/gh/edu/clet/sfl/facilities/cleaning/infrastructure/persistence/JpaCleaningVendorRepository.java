package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningVendorRepository extends JpaRepository<CleaningVendorRecord, UUID> {

    @Query("""
            select v from CleaningVendorRecord v
             where v.siteCode = :siteCode and v.vendorMasterReference = :reference
            """)
    Optional<CleaningVendorRecord> findByReference(@Param("siteCode") String siteCode,
            @Param("reference") String reference);

    @Query("select v from CleaningVendorRecord v where v.siteCode = :siteCode order by v.name asc")
    List<CleaningVendorRecord> findForSite(@Param("siteCode") String siteCode);
}
