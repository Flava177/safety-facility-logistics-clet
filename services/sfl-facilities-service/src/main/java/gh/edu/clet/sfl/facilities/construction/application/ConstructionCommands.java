package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectContractor;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The S176 commands. Each carries the actor and channel it is authorised and audited under.
 *
 * <p>{@code expectedVersion} is the optional If-Match of every edit, with the semantics
 * {@code RecordMetadata.requireVersion} gives it: {@code null} accepts last-write-wins.
 */
public final class ConstructionCommands {

    private ConstructionCommands() {
    }

    // ---- S176-01 project register -------------------------------------------------------------

    public record MilestoneSpec(String code, String name, LocalDate targetDate) {
    }

    public record ContractorSpec(UUID contractorId, ProjectContractor.Role role) {
    }

    public record RegisterProject(String siteCode, String title, String scope, List<String> workTypes,
            BigDecimal budgetBaseline, String currency, String fundingSourceReference, String fundingSourceName,
            List<MilestoneSpec> milestones, List<ContractorSpec> contractors, String projectManagerId,
            ActorContext actor, SourceChannel channel, String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.join("|", String.valueOf(siteCode), String.valueOf(title), String.valueOf(budgetBaseline),
                    String.valueOf(currency), String.valueOf(workTypes));
        }
    }

    public record CompleteRegistration(UUID projectId, String scope, List<String> workTypes,
            BigDecimal budgetBaseline, String currency, String fundingSourceReference, String fundingSourceName,
            List<MilestoneSpec> milestones, List<ContractorSpec> contractors, String projectManagerId,
            Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record ApproveProject(UUID projectId, String note, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    public record StartProject(UUID projectId, Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record CancelProject(UUID projectId, String reason, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    public record ReviseBaseline(UUID projectId, BigDecimal amount, String currency, String reason,
            Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record AddMilestone(UUID projectId, MilestoneSpec milestone, ActorContext actor, SourceChannel channel) {
    }

    public record ReviseMilestone(UUID projectId, UUID milestoneId, LocalDate targetDate, String reason,
            Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record AchieveMilestone(UUID projectId, UUID milestoneId, LocalDate achievedOn, ActorContext actor,
            SourceChannel channel) {
    }

    public record AssignContractor(UUID projectId, UUID contractorId, ProjectContractor.Role role,
            ActorContext actor, SourceChannel channel) {
    }

    public record LinkPermit(UUID projectId, String permitId, String workType, ActorContext actor,
            SourceChannel channel) {
    }

    // ---- S176-02 contractors ------------------------------------------------------------------

    public record RegisterContractor(String siteCode, String contractorCode, String name, String vendorReference,
            String insuranceProvider, String insurancePolicyReference, LocalDate insuranceExpiresOn,
            ActorContext actor, SourceChannel channel) {
    }

    public record UpdateInsurance(UUID contractorId, String insuranceProvider, String insurancePolicyReference,
            LocalDate insuranceExpiresOn, Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record RecordCompetency(UUID contractorId, String certificationCode, String description,
            String certificateReference, LocalDate expiresOn, ActorContext actor, SourceChannel channel) {
    }

    public record RequestSiteAccess(UUID contractorId, UUID projectId, String accessScope, Instant validFrom,
            Instant validTo, ActorContext actor, SourceChannel channel) {
    }

    // ---- S176-03 variations -------------------------------------------------------------------

    public record SubmitVariation(UUID projectId, String changeDescription, BigDecimal costDelta, String currency,
            String justification, ActorContext actor, SourceChannel channel) {
    }

    public record DecideVariation(UUID variationId, boolean approve, String note, ActorContext actor,
            SourceChannel channel) {
    }

    public record EscalatedApproval(UUID variationId, String note, ActorContext actor, SourceChannel channel) {
    }

    // ---- S176-04 handover and defects ---------------------------------------------------------

    public record RecordPracticalCompletion(UUID projectId, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    /**
     * One S152 register change a handover applies.
     *
     * @param action {@code CREATE} (needs {@code floorId} and {@code roomCode}) or {@code UPDATE}
     *        (needs {@code roomId}); every other field is optional on update and means "unchanged"
     */
    public record RoomChange(Action action, UUID roomId, UUID floorId, String roomCode, String name,
            SpaceType spaceType, Integer capacity, BigDecimal areaSqm, String costCentre, Boolean bookable,
            Boolean examinationCapable) {

        public enum Action {
            CREATE,
            UPDATE
        }
    }

    public record RecordHandover(UUID projectId, LocalDate handoverDate, String notes, List<RoomChange> roomChanges,
            Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record RetryScenarioConfirmation(UUID projectId, ActorContext actor, SourceChannel channel) {
    }

    public record RaiseDefect(UUID projectId, UUID contractorId, String description, UUID roomId,
            String locationCode, DefectItem.Priority priority, ActorContext actor, SourceChannel channel) {
    }

    public record DeferDefect(UUID defectId, String reason, ActorContext actor, SourceChannel channel) {
    }

    public record CloseProject(UUID projectId, Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }
}
