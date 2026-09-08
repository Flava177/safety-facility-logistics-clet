package gh.edu.clet.sfl.facilities.maintenance.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.maintenance.application.FacilityFaultService;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
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
 * The S153 fault-reporting write path's HTTP contract, following the pattern
 * {@code FacilitiesMasterDataControllerTest} established: status code, error envelope, correlation id.
 */
@WebMvcTest(controllers = FacilityFaultController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({FacilitiesActorResolver.class, FacilityFaultControllerTest.FixedClock.class})
class FacilityFaultControllerTest {

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
    private FacilityFaultService service;

    @Test
    void reporting_a_fault_returns_201_with_a_location_header() throws Exception {
        FacilityFault fault = fault();
        given(service.report(any())).willReturn(fault);

        mockMvc.perform(post("/api/v1/facilities/faults")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "requester")
                        .header("X-SFL-Roles", "FACILITIES_MANAGER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"siteCode":"MAIN","roomId":"%s","title":"Projector will not start",
                                 "description":"No power light","priority":"LOW"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/facilities/faults/" + fault.id()))
                .andExpect(jsonPath("$.data.faultNumber").value("FLT-MAIN-000001"))
                .andExpect(jsonPath("$.data.status").value("REPORTED"));
    }

    @Test
    void a_missing_title_is_400_with_the_field_named() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/faults")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteCode":"MAIN","description":"No power light","priority":"LOW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("title"))
                .andExpect(jsonPath("$.error.correlationId").exists());
    }

    @Test
    void triaging_a_missing_fault_is_404() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new FacilitiesException.RecordNotFoundException("FacilityFault", id))
                .given(service).triage(any());

        mockMvc.perform(patch("/api/v1/facilities/faults/" + id + "/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority":"HIGH"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
    }

    private static FacilityFault fault() {
        return FacilityFault.report(UUID.randomUUID(), "FLT-MAIN-000001", "MAIN", UUID.randomUUID(), null,
                null, "Projector will not start", "No power light", null, FaultPriority.LOW, "requester", NOW,
                SourceChannel.WEB, "corr-1");
    }
}
