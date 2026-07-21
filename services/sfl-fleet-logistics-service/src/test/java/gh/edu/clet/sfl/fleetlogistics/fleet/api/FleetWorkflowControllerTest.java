package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetWorkflowMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.EscalateWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.RaiseWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.query.FleetWorkflowQueryService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAuditService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetWorkflowApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.UnauthorizedApprovalException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SlaTarget;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** Contract tests for fleet workflow item endpoints (SRS-SFL-S166-02). */
@WebMvcTest(controllers = FleetWorkflowController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({FleetWorkflowControllerTest.TestBeans.class, FleetActorResolver.class, FleetWorkflowMapper.class})
class FleetWorkflowControllerTest {

    private static final UUID ITEM_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FleetWorkflowApplicationService workflowService;

    @MockitoBean
    private FleetWorkflowQueryService workflowQueries;

    @MockitoBean
    private FleetAuditService auditService;

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(FleetFixtures.NOW, ZoneOffset.UTC);
        }
    }

    @Test
    @DisplayName("raising a workflow item returns 201, Location and the shared response envelope")
    void raise_workflow_item_returns_created_envelope() throws Exception {
        when(workflowService.raise(any(RaiseWorkflowItem.class))).thenReturn(workflowItem());

        mockMvc.perform(post("/api/v1/fleet/workflow-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "officer@clet.edu.gh")
                        .header("X-SFL-Roles", "FLEET_LOGISTICS_OFFICER")
                        .header("X-SFL-Sites", "ACCRA")
                        .header("X-Correlation-ID", "corr-workflow-1")
                        .header("Idempotency-Key", "idem-workflow-1")
                        .content(validRaiseBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/fleet/workflow-items/" + ITEM_ID))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.data.workflowType").value("VEHICLE_DEFECT"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.siteCode").value("ACCRA"));

        ArgumentCaptor<RaiseWorkflowItem> command = ArgumentCaptor.forClass(RaiseWorkflowItem.class);
        verify(workflowService).raise(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().actor().actorId())
                .isEqualTo("officer@clet.edu.gh");
        org.assertj.core.api.Assertions.assertThat(command.getValue().idempotencyKey())
                .isEqualTo("idem-workflow-1");
    }

    @Test
    @DisplayName("closure without evidence is rejected at the API contract boundary")
    void closure_without_evidence_is_validation_failure() throws Exception {
        mockMvc.perform(patch("/api/v1/fleet/workflow-items/{itemId}/closure", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "closureReason": "Resolved"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FLEET_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("unauthorised escalation returns the SRS Unauthorized Approval wording and audits denial")
    void unauthorized_escalation_uses_srs_wording() throws Exception {
        when(workflowService.escalate(any(EscalateWorkflowItem.class)))
                .thenThrow(new UnauthorizedApprovalException(Map.of(
                        "siteCode", "ACCRA",
                        "resourceType", "FleetWorkflowItem",
                        "resourceId", ITEM_ID.toString(),
                        "requiredPermission", "FLEET_WORKFLOW_ESCALATE")));

        mockMvc.perform(patch("/api/v1/fleet/workflow-items/{itemId}/escalation", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-workflow-2")
                        .content("""
                                {
                                  "reason": "SLA breached"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FLEET_UNAUTHORIZED_APPROVAL"))
                .andExpect(jsonPath("$.error.message")
                        .value(FleetErrorCode.FLEET_UNAUTHORIZED_APPROVAL.message()))
                .andExpect(jsonPath("$.error.correlationId").value("corr-workflow-2"));

        verify(auditService).recordAuthorizationDenial(any(), any(), any(), any(), any(), any());
    }

    private static FleetWorkflowItem workflowItem() {
        return FleetWorkflowItem.raise(ITEM_ID, "FWF-99999999", FleetWorkflowType.VEHICLE_DEFECT,
                "VehicleInspection", "inspection-001", SiteCode.of("ACCRA"), "Brake defect",
                "Brake inspection failed.", WorkflowPriority.URGENT, WorkflowSeverity.CRITICAL,
                OperatingMode.MAINTENANCE,
                new SlaTarget(Duration.ofHours(1), Duration.ofHours(8),
                        gh.edu.clet.sfl.common.security.SflRole.FLEET_MANAGER, "default"),
                FleetFixtures.NOW, RecordMetadata.createdBy("officer@clet.edu.gh", FleetFixtures.NOW,
                        SourceChannel.WEB, "corr-workflow-1"));
    }

    private static String validRaiseBody() {
        return """
                {
                  "workflowType": "VEHICLE_DEFECT",
                  "relatedRecordType": "VehicleInspection",
                  "relatedRecordId": "inspection-001",
                  "siteCode": "ACCRA",
                  "title": "Brake defect",
                  "description": "Brake inspection failed.",
                  "priority": "URGENT",
                  "severity": "CRITICAL",
                  "operatingMode": "MAINTENANCE",
                  "assignee": "mechanic@clet.edu.gh"
                }
                """;
    }
}
