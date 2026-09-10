package gh.edu.clet.sfl.facilities.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.PreventiveMaintenanceSchedule;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderType;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code generateDueWorkOrders} must not read the same site's SLA policy, operating mode or evidence
 * requirement once per schedule - it should read each exactly once per distinct site per run and reuse
 * that for every schedule at that site. Before this fix, six schedules across two sites cost twelve
 * {@code findSiteByCode} calls (two per schedule, one for the response SLA and one for the resolution
 * SLA) and six {@code slaPolicyFor}/{@code evidenceRequiredFor} calls each; this pins it at two of each -
 * one per distinct site - regardless of how many schedules share that site.
 */
class PreventiveMaintenanceGenerationQueryCountTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private final MaintenanceRepository maintenance = mock(MaintenanceRepository.class);
    private final FacilitiesRepository facilities = mock(FacilitiesRepository.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final IdempotencyPort idempotency = mock(IdempotencyPort.class);
    private final TestDoubles.RecordingOutbox outbox = new TestDoubles.RecordingOutbox();
    private final RuntimeConfigurationPort configPort = mock(RuntimeConfigurationPort.class);
    private final MaintenanceConfiguration configuration = spy(new MaintenanceConfiguration(configPort));

    @Test
    void reads_each_site_s_configuration_once_per_run_no_matter_how_many_schedules_share_it() {
        when(configPort.duration(anyString(), anyString(), any())).thenAnswer(inv -> inv.getArgument(2));
        when(configPort.integer(anyString(), anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(2));

        UUID assetId = UUID.randomUUID();
        FacilityAsset asset = FacilityAsset.register(assetId, "SITE-A", "AST-1", "Chiller 1",
                AssetCategory.HVAC, AssetCriticality.MEDIUM, null, "L1", null, null, null, null, null, null,
                "tester", null, null, "tester", Instant.now(), SourceChannel.WEB, "corr-fixture");
        when(facilities.findAsset(any())).thenReturn(Optional.of(asset));
        when(facilities.findSiteByCode(anyString())).thenReturn(Optional.empty());

        List<PreventiveMaintenanceSchedule> schedules = new ArrayList<>();
        for (String siteCode : List.of("SITE-A", "SITE-B")) {
            for (int i = 0; i < 3; i++) {
                schedules.add(PreventiveMaintenanceSchedule.create(UUID.randomUUID(), siteCode,
                        "SCH-" + siteCode + "-" + i, "Schedule " + i, "Routine service", assetId, null, 30, 0,
                        FaultPriority.MEDIUM, WorkOrderType.PREVENTIVE, TODAY, "tester", Instant.now(),
                        SourceChannel.WEB, "corr-" + siteCode + i));
            }
        }
        when(maintenance.findSchedulesDueForGeneration(eq(TODAY), anyInt())).thenReturn(schedules);
        when(maintenance.nextWorkOrderNumber(anyString())).thenReturn("WO-TEST");
        when(maintenance.saveWorkOrder(any())).thenAnswer(inv -> inv.getArgument(0));
        when(maintenance.saveSchedule(any())).thenAnswer(inv -> inv.getArgument(0));

        FacilitiesAuthorization authorization = new FacilitiesAuthorization(audit);
        PreventiveMaintenanceService service = new PreventiveMaintenanceService(maintenance, facilities,
                configuration, authorization, audit, idempotency, outbox, CLOCK);
        ActorContext systemActor = TestDoubles.actor("scheduler", Set.of(SflRole.SFL_ADMIN), "*");

        List<WorkOrder> generated = service.generateDueWorkOrders(systemActor, TODAY);

        assertThat(generated).hasSize(6);
        // One read per distinct site (2), not one per schedule (6) or one per SLA-due-date call (12).
        verify(configuration, times(2)).slaPolicyFor(anyString());
        verify(configuration, times(2)).evidenceRequiredFor(anyString(), eq(FaultPriority.MEDIUM));
        verify(facilities, times(2)).findSiteByCode(anyString());
    }
}
