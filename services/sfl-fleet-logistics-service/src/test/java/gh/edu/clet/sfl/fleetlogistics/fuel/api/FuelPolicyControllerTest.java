package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAuditService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test for the fuel-policy conflict path.
 *
 * <p>{@code savePolicy} used to be an unconditional upsert that could never throw
 * {@link OptimisticLockingFailureException}, so this exact wiring - the exception reaching
 * {@code FleetApiExceptionHandler} and coming back as 409 {@code FLEET_RECORD_VERSION_CONFLICT} - was
 * never exercised for this controller. Mirrors {@code VehicleControllerTest.stale_version_returns_409}.
 */
@WebMvcTest(controllers = FuelPolicyController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(FleetActorResolver.class)
class FuelPolicyControllerTest {

    private static final UUID POLICY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FuelApplicationService service;

    @MockitoBean
    private FleetAuditService auditService;

    @Test
    @DisplayName("a stale policy revision returns 409 so the client can reload rather than overwrite")
    void stale_version_returns_409() throws Exception {
        doThrow(new OptimisticLockingFailureException("FuelPolicy version conflict"))
                .when(service).updatePolicy(any());

        mockMvc.perform(put("/api/v1/fuel/policies/{id}", POLICY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Revised limits",
                                  "effectiveFrom": "2026-01-01T00:00:00Z",
                                  "policyVersion": 1,
                                  "maxPerTransaction": 500,
                                  "odometerJumpTolerance": 0,
                                  "receiptRequired": true,
                                  "receiptGraceHours": 24,
                                  "materialityAmount": 400,
                                  "anomalySlaHours": 8,
                                  "costVarianceTolerance": 0.3,
                                  "repeatedPatternWindowHours": 720,
                                  "repeatedPatternThreshold": 3
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FLEET_RECORD_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.message")
                        .value(FleetErrorCode.FLEET_RECORD_VERSION_CONFLICT.message()));
    }
}
