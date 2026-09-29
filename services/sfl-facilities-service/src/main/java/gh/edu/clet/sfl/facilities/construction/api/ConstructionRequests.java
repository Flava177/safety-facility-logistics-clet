package gh.edu.clet.sfl.facilities.construction.api;

import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectContractor;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The S176 request bodies, with Bean Validation on every field the SRS constrains. What is not
 * duplicated here is anything needing more than one field or a clock - see {@code BookingRequests}
 * for the same reasoning.
 */
public final class ConstructionRequests {

    private ConstructionRequests() {
    }

    public record MilestoneRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 200) String name,
            @NotNull LocalDate targetDate) {
    }

    public record ContractorAssignmentRequest(@NotNull UUID contractorId, ProjectContractor.Role role) {
    }

    public record RegisterProject(@NotBlank @Size(max = 40) String siteCode, @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String scope,
            @NotEmpty List<@NotBlank String> workTypes, @NotNull @Min(1) BigDecimal budgetBaseline,
            @NotBlank @Size(min = 3, max = 3) String currency, @NotBlank @Size(max = 120) String fundingSourceReference,
            @Size(max = 200) String fundingSourceName, @NotEmpty @Valid List<MilestoneRequest> milestones,
            List<@Valid ContractorAssignmentRequest> contractors, String projectManagerId) {
    }

    public record CompleteRegistration(@NotBlank @Size(max = 4000) String scope,
            @NotEmpty List<@NotBlank String> workTypes, @NotNull @Min(1) BigDecimal budgetBaseline,
            @NotBlank @Size(min = 3, max = 3) String currency, @NotBlank @Size(max = 120) String fundingSourceReference,
            @Size(max = 200) String fundingSourceName, @NotEmpty @Valid List<MilestoneRequest> milestones,
            List<@Valid ContractorAssignmentRequest> contractors, String projectManagerId, Long expectedVersion) {
    }

    public record ApproveProject(@Size(max = 2000) String note, Long expectedVersion) {
    }

    public record VersionedRequest(Long expectedVersion) {
    }

    public record CancelProject(@NotBlank @Size(max = 2000) String reason, Long expectedVersion) {
    }

    public record ReviseBaseline(@NotNull @Min(1) BigDecimal amount, @Size(min = 3, max = 3) String currency,
            @NotBlank @Size(max = 2000) String reason, Long expectedVersion) {
    }

    public record ReviseMilestone(@NotNull LocalDate targetDate, @NotBlank @Size(max = 2000) String reason,
            Long expectedVersion) {
    }

    public record AchieveMilestone(LocalDate achievedOn) {
    }

    public record LinkPermit(@NotBlank @Size(max = 80) String permitId, @NotBlank @Size(max = 60) String workType) {
    }

    public record RegisterContractor(@NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 40) String contractorCode,
            @NotBlank @Size(max = 200) String name, @Size(max = 120) String vendorReference,
            @Size(max = 200) String insuranceProvider, @Size(max = 120) String insurancePolicyReference,
            @NotNull LocalDate insuranceExpiresOn) {
    }

    public record UpdateInsurance(@Size(max = 200) String insuranceProvider,
            @Size(max = 120) String insurancePolicyReference, @NotNull LocalDate insuranceExpiresOn,
            Long expectedVersion) {
    }

    public record RecordCompetency(@NotBlank @Size(max = 60) String certificationCode,
            @Size(max = 500) String description, @Size(max = 120) String certificateReference,
            @NotNull LocalDate expiresOn) {
    }

    public record RequestSiteAccess(UUID projectId, @NotBlank @Size(max = 500) String accessScope, Instant validFrom,
            @NotNull Instant validTo) {
    }

    public record SubmitVariation(@NotBlank @Size(max = 4000) String changeDescription,
            @NotNull BigDecimal costDelta, @Size(min = 3, max = 3) String currency,
            @NotBlank @Size(max = 4000) String justification) {
    }

    public record DecideVariation(@NotNull Boolean approve, @Size(max = 2000) String note) {
    }

    public record EscalatedApproval(@Size(max = 2000) String note) {
    }

    public record RoomChangeRequest(@NotNull RoomAction action, UUID roomId, UUID floorId,
            @Size(max = 80) String roomCode, @Size(max = 200) String name, SpaceType spaceType, Integer capacity,
            BigDecimal areaSqm, String costCentre, Boolean bookable, Boolean examinationCapable) {

        public enum RoomAction {
            CREATE,
            UPDATE
        }
    }

    public record RecordHandover(LocalDate handoverDate, @Size(max = 4000) String notes,
            List<@Valid RoomChangeRequest> roomChanges, Long expectedVersion) {
    }

    public record RaiseDefect(@NotNull UUID contractorId, @NotBlank @Size(max = 4000) String description,
            UUID roomId, @Size(max = 80) String locationCode, DefectItem.Priority priority) {
    }

    public record DeferDefect(@NotBlank @Size(max = 2000) String reason) {
    }
}
