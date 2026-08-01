package gh.edu.clet.sfl.assetvisibility.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import gh.edu.clet.sfl.assetvisibility.application.AssetVisibilityService;
import gh.edu.clet.sfl.assetvisibility.application.MoveAssetCommand;
import gh.edu.clet.sfl.assetvisibility.application.RegisterAssetCommand;
import gh.edu.clet.sfl.assetvisibility.domain.AssetCategory;
import gh.edu.clet.sfl.assetvisibility.domain.AssetReference;
import gh.edu.clet.sfl.assetvisibility.domain.AssetStatus;
import gh.edu.clet.sfl.assetvisibility.domain.LocationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AssetReferenceController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
// The actor resolver is a real collaborator, not a mock: what it resolves *to* is the thing the
// controller records, and a mock would let the header path regress unnoticed.
@Import(AssetVisibilityActorResolver.class)
class AssetReferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetVisibilityService service;

    @Test
    void register_asset_returns_created_contract() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(service.register(any(RegisterAssetCommand.class))).thenReturn(asset(id, "CAM-001", "MAIN", "ROOM-A"));

        mockMvc.perform(post("/api/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "operator@sfl.local")
                        .header("X-Correlation-ID", "corr-1")
                        .content("""
                                {
                                  "assetCode": "cam-001",
                                  "name": "Main Gate Camera",
                                  "category": "CCTV_CAMERA",
                                  "siteCode": "main",
                                  "locationType": "ROOM",
                                  "locationReference": "room-a",
                                  "custodianReference": "Security",
                                  "externalReference": "vms-1001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/assets/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.assetCode").value("CAM-001"))
                .andExpect(jsonPath("$.category").value("CCTV_CAMERA"))
                .andExpect(jsonPath("$.siteCode").value("MAIN"))
                .andExpect(jsonPath("$.locationReference").value("ROOM-A"));

        ArgumentCaptor<RegisterAssetCommand> command = ArgumentCaptor.forClass(RegisterAssetCommand.class);
        verify(service).register(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().actor()).isEqualTo("operator@sfl.local");
        org.assertj.core.api.Assertions.assertThat(command.getValue().correlationId()).isEqualTo("corr-1");
    }

    @Test
    void list_assets_by_site_returns_stable_array_contract() throws Exception {
        when(service.findAll("MAIN")).thenReturn(List.of(
                asset(UUID.fromString("11111111-1111-1111-1111-111111111111"), "CAM-001", "MAIN", "ROOM-A"),
                asset(UUID.fromString("22222222-2222-2222-2222-222222222222"), "READER-001", "MAIN", "MAIN-GATE")));

        mockMvc.perform(get("/api/v1/assets").param("siteCode", "MAIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].assetCode").value("CAM-001"))
                .andExpect(jsonPath("$[1].assetCode").value("READER-001"));
    }

    @Test
    void lookup_assets_by_location_returns_matching_assets() throws Exception {
        when(service.findByLocation("MAIN", LocationType.ROOM, "ROOM-A"))
                .thenReturn(List.of(asset(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "CAM-001", "MAIN", "ROOM-A")));

        mockMvc.perform(get("/api/v1/assets/by-location")
                        .param("siteCode", "MAIN")
                        .param("locationType", "ROOM")
                        .param("locationReference", "ROOM-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].locationType").value("ROOM"))
                .andExpect(jsonPath("$[0].locationReference").value("ROOM-A"));
    }

    @Test
    void move_asset_returns_updated_location_contract() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(service.move(any(MoveAssetCommand.class))).thenReturn(asset(id, "CAM-001", "MAIN", "MAIN-GATE"));

        mockMvc.perform(patch("/api/v1/assets/{assetId}/location", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationType": "ZONE",
                                  "locationReference": "MAIN-GATE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.locationReference").value("MAIN-GATE"));

        ArgumentCaptor<MoveAssetCommand> command = ArgumentCaptor.forClass(MoveAssetCommand.class);
        verify(service).move(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().assetId()).isEqualTo(id);
        org.assertj.core.api.Assertions.assertThat(command.getValue().locationType()).isEqualTo(LocationType.ZONE);
    }

    @Test
    void register_asset_validation_errors_use_error_contract() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetCode": "",
                                  "name": "Main Gate Camera",
                                  "category": "CCTV_CAMERA",
                                  "siteCode": "MAIN",
                                  "locationType": "ROOM",
                                  "locationReference": "ROOM-A"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private AssetReference asset(UUID id, String assetCode, String siteCode, String locationReference) {
        return new AssetReference(id, assetCode, "Main Gate Camera", AssetCategory.CCTV_CAMERA, AssetStatus.ACTIVE,
                siteCode, locationReference.equals("ROOM-A") ? LocationType.ROOM : LocationType.ZONE,
                locationReference, "Security", "vms-1001", null,
                Instant.parse("2026-07-13T08:00:00Z"), Instant.parse("2026-07-13T08:00:00Z"));
    }
}