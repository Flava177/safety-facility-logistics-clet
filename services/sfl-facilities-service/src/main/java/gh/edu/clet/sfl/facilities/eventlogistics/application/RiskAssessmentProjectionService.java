package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.common.hse.RiskAssessmentCurrency;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.EventLogisticsRepository;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.RiskAssessmentProjection;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps S173's projection of S165 risk assessments - SRS-SFL-S173-03.
 *
 * <p>Fed by the four reserved SSEMP events through {@code RiskAssessmentEventsHandler}. The contracts
 * are in {@code docs/facilities/S173_Event_Contracts.md}; S165 is not built, so nothing publishes them
 * and the projection is empty in every current deployment.
 *
 * <h2>Out-of-order delivery</h2>
 *
 * <p>The broker does not promise order. Each rule below is written so a late event cannot make an
 * assessment more current than it is: a publication of version 2 supersedes version 1 whenever it
 * arrives; a supersession is permanent (a later "published" for the same version does not revive it);
 * a lapse can only bring the review date forward.
 */
@Service
public class RiskAssessmentProjectionService {

    public static final String PUBLISHED = "sfl.ssemp.risk-assessment-published.v1";
    public static final String SUPERSEDED = "sfl.ssemp.risk-assessment-superseded.v1";
    public static final String REVIEW_LAPSED = "sfl.ssemp.risk-assessment-review-lapsed.v1";
    public static final String SIGNED_OFF = "sfl.ssemp.risk-assessment-signed-off.v1";

    private final EventLogisticsRepository repository;

    public RiskAssessmentProjectionService(EventLogisticsRepository repository) {
        this.repository = repository;
    }

    /** S165 published a version: it is the current one, and every earlier version is superseded. */
    @Transactional
    public RiskAssessmentProjection published(String assessmentId, int version, String siteCode,
            RiskAssessmentCurrency.RiskLevel riskLevel, Instant reviewDueAt, String authorId, String signedOffBy,
            Instant at) {
        Optional<RiskAssessmentProjection> existing = repository.findRiskAssessment(assessmentId, version);
        RiskAssessmentCurrency.Status status = existing.map(RiskAssessmentProjection::status)
                .filter(current -> current == RiskAssessmentCurrency.Status.SUPERSEDED)
                .orElse(RiskAssessmentCurrency.Status.PUBLISHED);
        boolean laterExists = repository.findRiskAssessmentVersions(assessmentId).stream()
                .anyMatch(other -> other.version() > version
                        && other.status() != RiskAssessmentCurrency.Status.WITHDRAWN);
        if (laterExists) {
            status = RiskAssessmentCurrency.Status.SUPERSEDED;
        }
        RiskAssessmentProjection saved = repository.saveRiskAssessment(new RiskAssessmentProjection(assessmentId,
                version, siteCode, status, riskLevel, reviewDueAt, authorId, signedOffBy, PUBLISHED, at));
        for (RiskAssessmentProjection earlier : repository.findRiskAssessmentVersions(assessmentId)) {
            if (earlier.version() < version && earlier.status() != RiskAssessmentCurrency.Status.SUPERSEDED) {
                repository.saveRiskAssessment(earlier.withStatus(RiskAssessmentCurrency.Status.SUPERSEDED,
                        PUBLISHED, at));
            }
        }
        return saved;
    }

    /** @return false when S173 holds no such version - the event is logged and dropped by the handler */
    @Transactional
    public boolean superseded(String assessmentId, int version, Instant at) {
        return repository.findRiskAssessment(assessmentId, version)
                .map(found -> repository.saveRiskAssessment(found.withStatus(RiskAssessmentCurrency.Status.SUPERSEDED,
                        SUPERSEDED, at)))
                .isPresent();
    }

    /** The review date passed without sign-off. Only ever brings the due date forward. */
    @Transactional
    public boolean reviewLapsed(String assessmentId, int version, Instant lapsedAt, Instant at) {
        return repository.findRiskAssessment(assessmentId, version).map(found -> {
            Instant due = found.reviewDueAt() == null || lapsedAt.isBefore(found.reviewDueAt()) ? lapsedAt
                    : found.reviewDueAt();
            return repository.saveRiskAssessment(found.withReviewDue(due, REVIEW_LAPSED, at));
        }).isPresent();
    }

    /** A named reviewer signed the version off, renewing its review date. */
    @Transactional
    public boolean signedOff(String assessmentId, int version, String reviewer, Instant renewedReviewDue,
            Instant at) {
        return repository.findRiskAssessment(assessmentId, version)
                .map(found -> repository.saveRiskAssessment(found.signedOff(reviewer, renewedReviewDue, SIGNED_OFF,
                        at)))
                .isPresent();
    }
}
