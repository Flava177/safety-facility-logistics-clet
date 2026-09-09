package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.application.ports.ReadinessRepository;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessment;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessmentItem;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklist;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklistItem;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The readiness JPA adapter against a real PostgreSQL - the LAZY-fetch change (item 4 of the
 * architecture-audit remediation) is only worth anything if items are still there after the session
 * that loaded the parent has closed, which an in-memory double cannot prove.
 *
 * <p>Covers what {@code EntityGraph} and the two-step id-then-fetch pagination were written for: a
 * checklist and an assessment both carry their items back after {@code open-in-view: false} has ended
 * the persistence context, and a paginated search reports the true total rather than the page size.
 */
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class JpaReadinessRepositoryAdapterTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @Autowired private FacilitiesRepository facilities;
    @Autowired private ReadinessRepository readiness;

    private String siteCode;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        // A fresh site/room per run: this is the shared e2e database, and a fixed code would collide
        // with a previous run's rows or with another test's fixture.
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        siteCode = "RT" + suffix;
        Instant now = Instant.now();
        Site site = facilities.saveSite(Site.create(UUID.randomUUID(), siteCode, "Readiness Test Site", null,
                "tester", now, SourceChannel.WEB, "corr-readiness-test"));
        Building building = facilities.saveBuilding(Building.create(UUID.randomUUID(), site.id(), siteCode,
                "B1", "Building 1", null, "tester", now, SourceChannel.WEB, "corr-readiness-test"));
        FacilityFloor floor = facilities.saveFloor(FacilityFloor.create(UUID.randomUUID(), building.id(),
                siteCode, "F1", "Floor 1", 1, "tester", now, SourceChannel.WEB, "corr-readiness-test"));
        FacilityRoom room = facilities.saveRoom(FacilityRoom.create(UUID.randomUUID(), floor.id(), siteCode,
                "R1", "Room 1", SpaceType.EXAMINATION_HALL, 100, null, null, true, true, "tester", now,
                SourceChannel.WEB, "corr-readiness-test"));
        roomId = room.id();
    }

    @Test
    void a_saved_checklists_items_survive_past_the_session_that_loaded_it() {
        ReadinessChecklist created = readiness.saveChecklist(withItems(ReadinessChecklist.create(
                UUID.randomUUID(), siteCode, "CL1", "Exam readiness", null, SpaceType.EXAMINATION_HALL,
                OperatingMode.ROUTINE, "tester", Instant.now(), SourceChannel.WEB, "corr-1")));

        // Each of these is a separate call - and therefore, with open-in-view off, a separate closed
        // session by the time the adapter's toDomain() runs - which is exactly the scenario a LAZY
        // collection without an explicit fetch would fail in.
        ReadinessChecklist byId = readiness.findChecklist(created.id()).orElseThrow();
        assertThat(byId.items()).extracting(ReadinessChecklistItem::itemCode)
                .containsExactly("FIRE-DOOR", "LIGHTING");

        ReadinessChecklist byCode = readiness.findChecklistByCode(siteCode, "CL1").orElseThrow();
        assertThat(byCode.items()).hasSize(2);

        assertThat(readiness.findChecklists(siteCode)).singleElement()
                .satisfies(checklist -> assertThat(checklist.items()).hasSize(2));

        assertThat(readiness.findApplicableChecklist(siteCode, SpaceType.EXAMINATION_HALL,
                OperatingMode.ROUTINE)).isPresent()
                .get().satisfies(checklist -> assertThat(checklist.items()).hasSize(2));
    }

    @Test
    void a_saved_assessments_items_survive_past_the_session_that_loaded_it() {
        ReadinessAssessment saved = readiness.saveAssessment(assessment());

        ReadinessAssessment byId = readiness.findAssessment(saved.id()).orElseThrow();
        assertThat(byId.items()).hasSize(2);

        assertThat(readiness.findLatestAssessment(roomId)).isPresent()
                .get().satisfies(assessment -> assertThat(assessment.items()).hasSize(2));
    }

    @Test
    void a_paginated_assessment_search_reports_the_true_total_and_keeps_its_items() {
        for (int i = 0; i < 3; i++) {
            readiness.saveAssessment(assessment());
        }

        RepositoryPage<ReadinessAssessment> firstPage = readiness.findAssessments(siteCode, roomId, 0, 2);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.items()).allSatisfy(assessment -> assertThat(assessment.items()).hasSize(2));

        RepositoryPage<ReadinessAssessment> secondPage = readiness.findAssessments(siteCode, roomId, 1, 2);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.totalElements()).isEqualTo(3);
    }

    @Test
    void a_paginated_blocker_search_reports_the_true_total() {
        for (int i = 0; i < 3; i++) {
            readiness.saveBlocker(ReadinessBlocker.raise(roomId, siteCode, null, BlockerSource.MANUAL, null,
                    BlockerSeverity.MINOR, "Chipped paint", "tester", Instant.now()));
        }

        RepositoryPage<ReadinessBlocker> firstPage = readiness.findBlockers(siteCode, roomId, null, null, 0, 2);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
    }

    private ReadinessAssessment assessment() {
        UUID assessmentId = UUID.randomUUID();
        ReadinessChecklistItem fireDoor = ReadinessChecklistItem.of(UUID.randomUUID(), "FIRE-DOOR",
                "Fire door closes", BlockerSeverity.CRITICAL, true, 1, 1);
        ReadinessChecklistItem lighting = ReadinessChecklistItem.of(UUID.randomUUID(), "LIGHTING",
                "Lighting works", BlockerSeverity.MINOR, false, 1, 2);
        List<ReadinessAssessmentItem> items = List.of(
                ReadinessAssessmentItem.answered(assessmentId, fireDoor, true, null),
                ReadinessAssessmentItem.answered(assessmentId, lighting, true, null));
        return new ReadinessAssessment(assessmentId, roomId, siteCode, null, null, 0, OperatingMode.ROUTINE,
                LocationReadinessStatus.READY, 100, items, null, "tester", Instant.now());
    }

    private static ReadinessChecklist withItems(ReadinessChecklist checklist) {
        ReadinessChecklistItem fireDoor = ReadinessChecklistItem.of(checklist.id(), "FIRE-DOOR",
                "Fire door closes", BlockerSeverity.CRITICAL, true, 1, 1);
        ReadinessChecklistItem lighting = ReadinessChecklistItem.of(checklist.id(), "LIGHTING",
                "Lighting works", BlockerSeverity.MINOR, false, 1, 2);
        return checklist.withItems(List.of(fireDoor, lighting), "tester", Instant.now(), SourceChannel.WEB,
                "corr-1");
    }
}
