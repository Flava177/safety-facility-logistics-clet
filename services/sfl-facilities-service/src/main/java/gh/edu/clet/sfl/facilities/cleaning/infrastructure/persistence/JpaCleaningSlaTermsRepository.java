package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningSlaTermsRepository extends JpaRepository<CleaningVendorSlaTermsRecord, UUID> {

    @Query("select t from CleaningVendorSlaTermsRecord t where t.vendorId = :vendorId and t.effectiveTo is null")
    Optional<CleaningVendorSlaTermsRecord> findCurrent(@Param("vendorId") UUID vendorId);

    @Query("select t from CleaningVendorSlaTermsRecord t where t.vendorId = :vendorId order by t.termsVersion asc")
    List<CleaningVendorSlaTermsRecord> findHistory(@Param("vendorId") UUID vendorId);
}
