package gh.edu.clet.sfl.facilities.cleaning.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.cleaning.application.CleaningFeedbackService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningScheduleService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningTaskService;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
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
 * The S169 write path's HTTP contract, following {@code BookingControllerTest}'s pattern: a permission
 * refusal from the application layer reaches the caller as 403 with the {@code UNAUTHORIZED_SCOPE}
 * error code and a correlation id, not a 500 or a silently-empty response.
 */
@WebMvcTest(controllers = CleaningController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({FacilitiesActorResolver.class, CleaningControllerTest.FixedClock.class})
class CleaningControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");

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
    private CleaningScheduleService schedules;

    @MockitoBean
    private CleaningTaskService tasks;

    @MockitoBean
    private CleaningFeedbackService feedback;

    @Test
    void raising_a_reactive_request_without_the_permission_is_403_with_the_unauthorized_scope_code() throws Exception {
        willThrow(new FacilitiesException.UnauthorizedScopeException(
                "You are not authorised to access this site or record."))
                .given(tasks).request(any());

        mockMvc.perform(post("/api/v1/facilities/cleaning/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "someone.else")
                        .header("X-SFL-Roles", "ENERGY_SUSTAINABILITY_OFFICER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"roomId":"%s","description":"Spilled coffee"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_SCOPE"))
                .andExpect(jsonPath("$.error.correlationId").exists());
    }

    @Test
    void raising_a_reactive_request_with_no_description_is_400_naming_the_field() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/cleaning/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("description"));
    }
}
