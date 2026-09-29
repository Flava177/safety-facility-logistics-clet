package gh.edu.clet.sfl.facilities.energy.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.energy.application.EnergyReadingService;
import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.ReadingStatus;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
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
import tools.jackson.databind.ObjectMapper;

/**
 * The S157 reading endpoints' HTTP contract - the pattern {@code BookingControllerTest} established:
 * status code, error envelope, correlation id. The forged-message rejection acceptance criterion
 * (NFR-SEC2) is proved through the real {@link gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier}
 * in {@code S157MandatoryScenariosTest}, not re-mocked here.
 */
@WebMvcTest(controllers = EnergyReadingController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(FacilitiesActorResolver.class)
class EnergyReadingControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private EnergyReadingService readings;

    @Test
    void a_plausible_manual_reading_returns_201() throws Exception {
        given(readings.enterManual(any())).willReturn(posted());

        mockMvc.perform(post("/api/v1/facilities/energy/readings/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "energy.officer")
                        .header("X-SFL-Roles", "ENERGY_SUSTAINABILITY_OFFICER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"meterId":"%s","registerValue":150}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void an_implausible_manual_reading_is_202_accepted_not_posted() throws Exception {
        given(readings.enterManual(any())).willReturn(held());

        mockMvc.perform(post("/api/v1/facilities/energy/readings/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"meterId":"%s","registerValue":9000}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.error.code").value("ENERGY_READING_IMPLAUSIBLE"));
    }

    @Test
    void a_permission_refusal_is_403() throws Exception {
        willThrow(new FacilitiesException.UnauthorizedScopeException("Actor does not hold FACILITIES_ENERGY_READING_ENTER"))
                .given(readings).enterManual(any());

        mockMvc.perform(post("/api/v1/facilities/energy/readings/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"meterId":"%s","registerValue":10}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_SCOPE"));
    }

    @Test
    void a_self_verification_attempt_is_403() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new FacilitiesException(gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.ENERGY_SELF_VERIFICATION))
                .given(readings).decide(any());

        mockMvc.perform(patch("/api/v1/facilities/energy/readings/" + id + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approve":true}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ENERGY_SELF_VERIFICATION"));
    }

    @Test
    void a_missing_meter_id_is_400_with_the_field_named() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/energy/readings/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registerValue":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("meterId"));
    }

    private static ConsumptionReading posted() {
        return new ConsumptionReading(UUID.randomUUID(), "MAIN", UUID.randomUUID(), "LAW", Utility.WATER,
                MeterSource.MANUAL, ReadingStatus.POSTED, NOW, NOW.minusSeconds(86400), java.math.BigDecimal.valueOf(150),
                java.math.BigDecimal.valueOf(50), null, null, null, true, java.math.BigDecimal.TEN,
                java.math.BigDecimal.ONE, java.math.BigDecimal.valueOf(20), null, "energy.officer", NOW, null, null,
                null, null, RecordMetadata.createdBy("energy.officer", NOW, gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel.WEB, "corr-1"));
    }

    private static ConsumptionReading held() {
        return new ConsumptionReading(UUID.randomUUID(), "MAIN", UUID.randomUUID(), "LAW", Utility.WATER,
                MeterSource.MANUAL, ReadingStatus.HELD, NOW, NOW.minusSeconds(86400), java.math.BigDecimal.valueOf(9000),
                java.math.BigDecimal.valueOf(8900), null, null, null, true, java.math.BigDecimal.TEN,
                java.math.BigDecimal.ONE, java.math.BigDecimal.valueOf(20), "Implausible Reading - outside the band",
                "energy.officer", NOW, null, null, null, null,
                RecordMetadata.createdBy("energy.officer", NOW, gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel.WEB, "corr-1"));
    }
}
