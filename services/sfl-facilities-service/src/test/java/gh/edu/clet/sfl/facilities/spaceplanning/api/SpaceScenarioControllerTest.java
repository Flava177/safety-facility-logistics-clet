package gh.edu.clet.sfl.facilities.spaceplanning.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpaceScenarioService;
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
 * The S158 scenario write path's HTTP contract, following {@code BookingControllerTest}'s pattern:
 * status code, error envelope, correlation id, and the permission refusal proving 403.
 */
@WebMvcTest(controllers = SpaceScenarioController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(FacilitiesActorResolver.class)
class SpaceScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpaceScenarioService service;

    @Test
    void an_actor_without_the_permission_is_refused_with_403() throws Exception {
        willThrow(new FacilitiesException.UnauthorizedScopeException(
                "You are not authorised to access this site or record."))
                .given(service).create(any());

        mockMvc.perform(post("/api/v1/facilities/space-planning/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "occupant")
                        .header("X-SFL-Roles", "IFIMP_REQUESTER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"siteCode":"MAIN","name":"Q3 reorganisation"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_SCOPE"))
                .andExpect(jsonPath("$.error.correlationId").exists());
    }

    @Test
    void a_missing_name_is_400_with_the_field_named() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/space-planning/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "planner")
                        .header("X-SFL-Roles", "SPACE_PLANNING_OFFICER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"siteCode":"MAIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("name"));
    }

    @Test
    void committing_a_draft_with_no_outcome_state_is_a_422() throws Exception {
        java.util.UUID id = java.util.UUID.randomUUID();
        willThrow(new gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioUncommittedException(
                "SP-MAIN-000001 v1 is a draft; cannot apply it to the S152 register."))
                .given(service).applyToRegister(any(), any(), any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/facilities/space-planning/scenarios/" + id + "/apply")
                        .header("X-SFL-User", "planner")
                        .header("X-SFL-Roles", "SPACE_PLANNING_OFFICER")
                        .header("X-SFL-Sites", "MAIN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SPACE_SCENARIO_UNCOMMITTED"));
    }
}
