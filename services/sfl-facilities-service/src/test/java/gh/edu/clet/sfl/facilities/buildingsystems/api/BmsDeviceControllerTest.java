package gh.edu.clet.sfl.facilities.buildingsystems.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.buildingsystems.application.BmsDeviceService;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
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
 * The S156 device registration HTTP contract, following the pattern {@code BookingControllerTest} and
 * {@code WorkOrderControllerTest} established: status code, error envelope, correlation id.
 *
 * <p>Permission refusal is decided inside {@code BmsDeviceService} (only holders of
 * {@code FACILITIES_BMS_DEVICE_MANAGE} may register a device); this proves the HTTP contract that refusal
 * lands as - {@code 403} with {@code UNAUTHORIZED_SCOPE} - matching every other refusal in the platform.
 */
@WebMvcTest(controllers = BmsDeviceController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(FacilitiesActorResolver.class)
class BmsDeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BmsDeviceService devices;

    @Test
    void registering_a_device_without_the_permission_is_refused_with_403() throws Exception {
        willThrow(new FacilitiesException.UnauthorizedScopeException("Actor does not hold FACILITIES_BMS_DEVICE_MANAGE"))
                .given(devices).register(any());

        mockMvc.perform(post("/api/v1/facilities/building-systems/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "occupant")
                        .header("X-SFL-Roles", "IFIMP_REQUESTER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"siteCode":"MAIN","deviceCode":"AHU-01","avampAssetId":"AVAMP-1",
                                 "name":"AHU 1","systemType":"HVAC","kind":"SENSOR","buildingCode":"LAW",
                                 "expectedIntervalSeconds":300}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_SCOPE"))
                .andExpect(jsonPath("$.error.correlationId").exists());
    }

    @Test
    void a_missing_avamp_asset_id_is_400_with_the_field_named() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/building-systems/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteCode":"MAIN","deviceCode":"AHU-01","name":"AHU 1","systemType":"HVAC",
                                 "kind":"SENSOR","buildingCode":"LAW","expectedIntervalSeconds":300}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("avampAssetId"));
    }
}
