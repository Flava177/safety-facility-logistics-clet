package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaContractorJpaRepository extends JpaRepository<ContractorRecord, UUID> {

    Optional<ContractorRecord> findBySiteCodeAndContractorCode(String siteCode, String contractorCode);

    @Query("select c from ContractorRecord c where (:siteCode is null or c.siteCode = :siteCode)")
    List<ContractorRecord> findForSite(@Param("siteCode") String siteCode);
}
