package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSignal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaUtilisationSignalJpaRepository extends JpaRepository<UtilisationSignalRecord, UUID> {

    Optional<UtilisationSignalRecord> findByRoomIdAndKindAndStatus(UUID roomId, UtilisationSignal.Kind kind,
            UtilisationSignal.Status status);

    List<UtilisationSignalRecord> findBySiteCodeAndStatusOrderByRoomCodeAsc(String siteCode,
            UtilisationSignal.Status status);

    List<UtilisationSignalRecord> findBySiteCodeOrderByRaisedAtDesc(String siteCode);
}
