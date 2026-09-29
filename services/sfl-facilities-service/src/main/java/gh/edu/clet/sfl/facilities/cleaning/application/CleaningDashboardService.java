package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFeedback;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The S169 dashboard: "scheduled versus completed tasks by site, overdue reactive requests, vendor SLA
 * compliance, occupant-feedback trend" (SRS §3.1, S169 Dashboard attribute).
 *
 * <p>Read-only and computed at request time from the same tables the workflow writes, so it cannot
 * disagree with them. It summarises every task at a site, so it is refused to anyone whose reading of
 * the rota is narrowed to their own tasks.
 */
@Service
public class CleaningDashboardService {

    static final int OVERDUE_LIST_LIMIT = 20;

    public record Dashboard(Instant from, Instant to, Instant generatedAt, List<SiteSummary> sites) {
    }

    /**
     * @param tasksWithoutChecklist raised for a space type with no active template - visible here
     *        because such a task completes with no checklist evidence
     */
    public record SiteSummary(String siteCode, int scheduled, int completed, int cancelled, int reactiveRaised,
            int overdueReactiveCount, List<OverdueRequest> overdueReactive, int tasksWithoutChecklist,
            List<VendorCompliance> vendorCompliance, List<FeedbackPoint> feedbackTrend) {
    }

    public record OverdueRequest(String taskNumber, String roomCode, Instant raisedAt, Instant dueBy,
            long minutesOverdue, TaskStatus status) {
    }

    public record VendorCompliance(String vendorName, String vendorMasterReference, int tasksCompleted,
            BigDecimal compliancePercent, int responseBreaches, int completionBreaches, int qualityBreaches,
            BigDecimal averageRating) {
    }

    public record FeedbackPoint(LocalDate day, int ratings, BigDecimal averageRating, int lowRatings) {
    }

    private final CleaningSupport support;
    private final CleaningRepository repository;
    private final CleaningVendorService vendors;

    public CleaningDashboardService(CleaningSupport support, CleaningVendorService vendors) {
        this.support = support;
        this.repository = support.repository();
        this.vendors = vendors;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(String siteCode, Instant from, Instant to, ActorContext actor, SourceChannel channel) {
        String site = siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode);
        support.requireUnnarrowedRead(actor, site, channel, "CleaningDashboard");
        support.authorization().requireRequestedSite(actor, site, channel, "CleaningDashboard");
        Instant now = support.now();
        Instant end = to == null ? now : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (!end.isAfter(start)) {
            throw new FacilitiesException.ValidationFailedException("The dashboard period must end after it starts.");
        }
        List<String> sites = site != null ? List.of(site)
                : support.facilities().findSites().stream().map(Site::siteCode)
                        .filter(code -> support.authorization().canAccessSite(actor, code)).sorted().toList();
        List<SiteSummary> summaries = new ArrayList<>();
        for (String code : sites) {
            summaries.add(summarise(code, start, end, now));
        }
        return new Dashboard(start, end, now, List.copyOf(summaries));
    }

    private SiteSummary summarise(String siteCode, Instant start, Instant end, Instant now) {
        List<CleaningTask> tasks = repository.findTasksStartingBetween(siteCode, start, end);
        int cancelled = (int) tasks.stream().filter(task -> task.status() == TaskStatus.CANCELLED).count();
        int scheduled = tasks.size() - cancelled;
        int completed = (int) tasks.stream().filter(task -> task.status() == TaskStatus.COMPLETED).count();
        int reactive = (int) tasks.stream().filter(task -> task.origin() == TaskOrigin.REACTIVE).count();
        int withoutChecklist = (int) tasks.stream()
                .filter(task -> task.checklistTemplateId() == null && task.status() != TaskStatus.CANCELLED).count();

        List<CleaningTask> overdue = repository.findOverdueReactive(siteCode, now, 500);
        List<OverdueRequest> overdueList = overdue.stream().limit(OVERDUE_LIST_LIMIT)
                .map(task -> new OverdueRequest(task.taskNumber(), task.roomCode(), task.requestedAt(), task.dueBy(),
                        Duration.between(task.dueBy(), now).toMinutes(), task.status()))
                .toList();

        List<VendorCompliance> compliance = new ArrayList<>();
        for (CleaningVendor vendor : repository.findVendors(siteCode)) {
            CleaningVendorService.Scorecard card = vendors.computeScorecard(vendor, start, end);
            compliance.add(new VendorCompliance(vendor.name(), vendor.vendorMasterReference(), card.tasksCompleted(),
                    card.compliancePercent(), card.responseBreaches(), card.completionBreaches(),
                    card.qualityBreaches(), card.averageRating()));
        }

        ZoneId zone = support.configuration().timeZone(siteCode);
        int lowMax = support.configuration().lowRatingMax(siteCode);
        Map<LocalDate, List<CleaningFeedback>> byDay = new TreeMap<>();
        for (CleaningFeedback feedback : repository.findFeedback(siteCode, start, end)) {
            byDay.computeIfAbsent(feedback.submittedAt().atZone(zone).toLocalDate(), day -> new ArrayList<>())
                    .add(feedback);
        }
        List<FeedbackPoint> trend = byDay.entrySet().stream().map(entry -> {
            List<CleaningFeedback> day = entry.getValue();
            BigDecimal average = BigDecimal.valueOf(day.stream().mapToInt(CleaningFeedback::rating).sum())
                    .divide(BigDecimal.valueOf(day.size()), 2, RoundingMode.HALF_UP);
            int low = (int) day.stream().filter(feedback -> feedback.rating() <= lowMax).count();
            return new FeedbackPoint(entry.getKey(), day.size(), average, low);
        }).toList();

        return new SiteSummary(siteCode, scheduled, completed, cancelled, reactive, overdue.size(), overdueList,
                withoutChecklist, List.copyOf(compliance), trend);
    }
}
