package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEventRiskAssessmentRepository
        extends JpaRepository<EventRiskAssessmentRecord, EventRiskAssessmentRecord.Key> {

    @Query("select a from EventRiskAssessmentRecord a where a.assessmentId = :assessmentId order by a.version asc")
    List<EventRiskAssessmentRecord> findVersions(@Param("assessmentId") String assessmentId);
}
