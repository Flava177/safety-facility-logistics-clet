package gh.edu.clet.sfl.facilities.buildingsystems.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.buildingsystems.application.TelemetryIngestionService;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The S156 telemetry endpoint's HTTP contract for a forged message - NFR-SEC2: "verified by a
 * forged-message rejection test per system". {@link gh.edu.clet.sfl.facilities.phase2.VendorMessageVerifierTest}
 * proves the shared verifier rejects, audits, SIEM-forwards and publishes for every module; this proves
 * this endpoint maps that rejection to the platform's forged-message contract - {@code 401} with
 * {@code VENDOR_MESSAGE_REJECTED}, the same sentence for every reason, and nothing else in the response
 * hints at what failed.
 *
 * <p>{@code S156MandatoryScenariosTest} proves the deeper behavioural half against the real verifier: no
 * reading is stored, no channel state moves, no alert is raised and no work order follows.
 */
@WebMvcTest(controllers = TelemetryIngestionController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(FacilitiesActorResolver.class)
class TelemetryIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelemetryIngestionService ingestion;

    @Test
    void a_forged_or_unauthenticated_message_is_rejected_with_401_and_never_actioned() throws Exception {
        willThrow(new FacilitiesException(FacilitiesErrorCode.VENDOR_MESSAGE_REJECTED))
                .given(ingestion).ingest(any());

        mockMvc.perform(post("/api/v1/facilities/building-systems/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "integration.bms")
                        .header("X-SFL-Roles", "SERVICE_INTEGRATION")
                        .header("X-SFL-Sites", "*")
                        .header("X-SFL-Source", "BMS-SIM")
                        .header("X-SFL-Signature", "00ff")
                        .header("X-SFL-Signed-At", "2026-09-28T08:00:00Z")
                        .content("""
                                {"messageType":"bms.telemetry","idempotencyKey":"forged-1","siteCode":"MAIN",
                                 "format":"sfl-bms-sim/v1","readings":[]}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("VENDOR_MESSAGE_REJECTED"))
                .andExpect(jsonPath("$.error.correlationId").exists());
    }
}
