package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAuditService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelCardService;
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
 * Contract test for the fuel-card conflict path.
 *
 * <p>{@code saveCard} used to swallow a version conflict and echo the caller's own stale object back
 * as a successful save, so {@code OptimisticLockingFailureException} never reached
 * {@code FleetApiExceptionHandler} for this controller. Mirrors
 * {@code VehicleControllerTest.stale_version_returns_409} and {@code FuelPolicyControllerTest}.
 */
@WebMvcTest(controllers = FuelCardController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(FleetActorResolver.class)
class FuelCardControllerTest {

    private static final UUID CARD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FuelCardService service;

    @MockitoBean
    private FleetAuditService auditService;

    @Test
    @DisplayName("a stale card transition returns 409 so the client can reload rather than overwrite")
    void stale_version_returns_409() throws Exception {
        doThrow(new OptimisticLockingFailureException("FuelCard version conflict"))
                .when(service).transition(any());

        mockMvc.perform(post("/api/v1/fuel/cards/{id}/{action}", CARD_ID, "suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Lost card"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FLEET_RECORD_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.message")
                        .value(FleetErrorCode.FLEET_RECORD_VERSION_CONFLICT.message()));
    }
}
