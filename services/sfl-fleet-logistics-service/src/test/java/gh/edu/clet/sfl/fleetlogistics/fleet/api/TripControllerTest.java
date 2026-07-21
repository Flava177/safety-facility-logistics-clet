package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetAssessmentMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetWorkflowMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CloseTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CreateTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.query.TripQueryService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAuditService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.TripApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ClosureEvidenceMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DateTimeRange;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
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

/** Contract tests for trip and assignment endpoints (SRS-SFL-S166-02). */
@WebMvcTest(controllers = TripController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({TripControllerTest.TestBeans.class, FleetActorResolver.class, FleetWorkflowMapper.class,
        FleetAssessmentMapper.class})
class TripControllerTest {

    private static final UUID TRIP_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID VEHICLE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DRIVER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVIDENCE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripApplicationService tripService;

    @MockitoBean
    private TripQueryService tripQueries;

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
    @DisplayName("creating a trip returns 201, Location and the shared response envelope")
    void create_trip_returns_created_envelope() throws Exception {
        when(tripService.create(any(CreateTripCommand.class))).thenReturn(assignedTrip());

        mockMvc.perform(post("/api/v1/fleet/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "officer@clet.edu.gh")
                        .header("X-SFL-Roles", "FLEET_LOGISTICS_OFFICER")
                        .header("X-SFL-Sites", "ACCRA")
                        .header("X-Correlation-ID", "corr-trip-1")
                        .header("Idempotency-Key", "idem-trip-1")
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/fleet/trips/" + TRIP_ID))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.siteCode").value("ACCRA"));

        ArgumentCaptor<CreateTripCommand> command = ArgumentCaptor.forClass(CreateTripCommand.class);
        verify(tripService).create(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().actor().actorId())
                .isEqualTo("officer@clet.edu.gh");
        org.assertj.core.api.Assertions.assertThat(command.getValue().idempotencyKey())
                .isEqualTo("idem-trip-1");
    }

    @Test
    @DisplayName("closure without evidence is rejected at the API contract boundary")
    void closure_without_evidence_is_validation_failure() throws Exception {
        mockMvc.perform(patch("/api/v1/fleet/trips/{tripId}/closure", TRIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "closureReason": "Delivered",
                                  "endOdometer": 42500
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FLEET_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("domain closure evidence failures keep the SRS error wording")
    void domain_closure_failure_uses_srs_wording() throws Exception {
        when(tripService.close(any(CloseTripCommand.class))).thenThrow(new ClosureEvidenceMissingException(
                Map.of("tripId", TRIP_ID.toString(), "missing", "closureEvidenceId")));

        mockMvc.perform(patch("/api/v1/fleet/trips/{tripId}/closure", TRIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-trip-2")
                        .content("""
                                {
                                  "closureReason": "Delivered",
                                  "closureEvidenceId": "44444444-4444-4444-4444-444444444444",
                                  "endOdometer": 42500
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("FLEET_CLOSURE_EVIDENCE_MISSING"))
                .andExpect(jsonPath("$.error.message")
                        .value(FleetErrorCode.FLEET_CLOSURE_EVIDENCE_MISSING.message()))
                .andExpect(jsonPath("$.error.correlationId").value("corr-trip-2"));
    }

    private static Trip assignedTrip() {
        return Trip.plan(TRIP_ID, "TRP-88888888", SiteCode.of("ACCRA"),
                        "Deliver examination materials", "Accra HQ", "Kumasi Centre",
                        OperatingMode.EXAMINATION,
                        DateTimeRange.of(FleetFixtures.NOW.plus(Duration.ofHours(2)),
                                FleetFixtures.NOW.plus(Duration.ofHours(6))),
                        FleetFixtures.metadata())
                .assign(VEHICLE_ID, DRIVER_ID, FleetFixtures.metadata());
    }

    private static String validCreateBody() {
        return """
                {
                  "vehicleId": "11111111-1111-1111-1111-111111111111",
                  "driverId": "22222222-2222-2222-2222-222222222222",
                  "siteCode": "ACCRA",
                  "purpose": "Deliver examination materials",
                  "origin": "Accra HQ",
                  "destination": "Kumasi Centre",
                  "operatingMode": "EXAMINATION",
                  "plannedStart": "2026-07-21T10:00:00Z",
                  "plannedEnd": "2026-07-21T14:00:00Z"
                }
                """;
    }
}
