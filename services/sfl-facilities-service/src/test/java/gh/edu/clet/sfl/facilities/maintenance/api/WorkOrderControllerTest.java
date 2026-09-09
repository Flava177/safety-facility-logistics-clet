package gh.edu.clet.sfl.facilities.maintenance.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceEvidenceService;
import gh.edu.clet.sfl.facilities.maintenance.application.WorkOrderApplicationService;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The S153 work-order write path's HTTP contract, following the pattern
 * {@code FacilitiesMasterDataControllerTest} established: status code, error envelope, correlation id.
 */
@WebMvcTest(controllers = WorkOrderController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({FacilitiesActorResolver.class, WorkOrderControllerTest.FixedClock.class})
class WorkOrderControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T09:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkOrderApplicationService service;

    @MockitoBean
    private MaintenanceEvidenceService evidence;

    @Test
    void raising_a_work_order_from_a_fault_returns_201_with_a_location_header() throws Exception {
        WorkOrder order = workOrder();
        given(service.createFromFault(any())).willReturn(order);

        mockMvc.perform(post("/api/v1/facilities/work-orders/from-fault")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "manager")
                        .header("X-SFL-Roles", "FACILITIES_MANAGER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"facilityFaultId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/facilities/work-orders/" + order.id()))
                .andExpect(jsonPath("$.data.workOrderNumber").value("WO-MAIN-000001"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void a_missing_fault_id_is_400_with_the_field_named() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/work-orders/from-fault")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignTo":"technician"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("facilityFaultId"))
                .andExpect(jsonPath("$.error.correlationId").exists());
    }

    @Test
    void assigning_a_stale_version_is_409() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new FacilitiesException.VersionConflictException("WorkOrder", id, 1L, 2L))
                .given(service).assign(any());

        mockMvc.perform(patch("/api/v1/facilities/work-orders/" + id + "/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedTo":"technician","expectedVersion":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
    }

    private static WorkOrder workOrder() {
        FacilityFault fault = FacilityFault.report(UUID.randomUUID(), "FLT-MAIN-000001", "MAIN",
                UUID.randomUUID(), null, null, "Projector will not start", "No power light", null,
                FaultPriority.LOW, "requester", NOW, SourceChannel.WEB, "corr-1");
        return WorkOrder.fromFault(UUID.randomUUID(), "WO-MAIN-000001", fault, NOW.plusSeconds(3600),
                NOW.plusSeconds(1800), 0, "manager", NOW, SourceChannel.WEB, "corr-1");
    }
}
