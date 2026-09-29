package gh.edu.clet.sfl.facilities.construction.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.construction.application.ConstructionProjectService;
import gh.edu.clet.sfl.facilities.construction.application.VariationService;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.UUID;
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
 * The S176 write path's HTTP contract - status code, error envelope, correlation id - following the
 * pattern {@code BookingControllerTest} established.
 *
 * <p>No forged-message test: S176 has no vendor-inbound endpoint (no BMS/IoT, metering or scan-hardware
 * traffic reaches this module), so NFR-SEC2's per-system requirement does not apply here - recorded in
 * the S176 gap report.
 */
@WebMvcTest(controllers = ConstructionProjectController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(FacilitiesActorResolver.class)
class ConstructionProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConstructionProjectService service;

    @MockitoBean
    private VariationService variations;

    @Test
    void registering_without_facilities_project_manage_is_403() throws Exception {
        willThrow(new FacilitiesException.UnauthorizedScopeException(
                "You are not authorised to access this site or record."))
                .given(service).register(any());

        mockMvc.perform(post("/api/v1/facilities/construction/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "auditor")
                        .header("X-SFL-Roles", "COMPLIANCE_OFFICER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"siteCode":"MAIN","title":"New wing","scope":"Extension",
                                 "workTypes":["FIT_OUT"],"budgetBaseline":500000,"currency":"GHS",
                                 "fundingSourceReference":"FUND-1",
                                 "milestones":[{"code":"PC","name":"Practical completion","targetDate":"2026-12-01"}]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_SCOPE"))
                .andExpect(jsonPath("$.error.correlationId").exists());
    }

    @Test
    void a_missing_title_is_400_with_the_field_named() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/construction/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteCode":"MAIN","scope":"Extension","workTypes":["FIT_OUT"],
                                 "budgetBaseline":500000,"currency":"GHS","fundingSourceReference":"FUND-1",
                                 "milestones":[{"code":"PC","name":"Practical completion","targetDate":"2026-12-01"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("title"));
    }

    @Test
    void starting_without_a_recorded_approval_is_422_with_the_srs_error_code() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal(
                gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.PROJECT_APPROVAL_MISSING, null))
                .given(service).start(any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/facilities/construction/projects/" + id + "/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PROJECT_APPROVAL_MISSING"));
    }
}
