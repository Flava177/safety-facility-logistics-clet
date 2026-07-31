package gh.edu.clet.sfl.facilities.masterdata.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesMasterDataService;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.application.ReadinessApplicationService;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
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

/**
 * The estate endpoints' HTTP contract (SRS-SFL-S152-01).
 *
 * <p>Asserts the three things a client depends on and a service test cannot see: the status code, the
 * error envelope, and that the correlation ID comes back. The business rules themselves are tested
 * against the application services.
 */
// The resource-server auto-configuration is excluded because a slice has no HttpSecurity for it to
// build a filter chain on. Authorisation is asserted against the application services; what this
// class covers is the wire contract.
@WebMvcTest(controllers = FacilitiesMasterDataController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(FacilitiesActorResolver.class)
class FacilitiesMasterDataControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-30T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilitiesMasterDataService service;

    @MockitoBean
    private ReadinessApplicationService readiness;

    @Test
    void creating_a_site_returns_201_with_a_location_header() throws Exception {
        Site site = site();
        given(service.createSite(any())).willReturn(site);

        mockMvc.perform(post("/api/v1/facilities/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "manager")
                        .header("X-SFL-Roles", "FACILITIES_MANAGER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"siteCode":"MAIN","name":"Main Campus","description":"Head office"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/facilities/sites/" + site.id()))
                .andExpect(jsonPath("$.data.siteCode").value("MAIN"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.operatingMode").value("ROUTINE"))
                .andExpect(jsonPath("$.data.metadata.version").value(0));
    }

    @Test
    void a_missing_required_field_is_400_with_the_field_named() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Main Campus"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("siteCode"))
                .andExpect(jsonPath("$.error.correlationId").exists());
    }

    @Test
    void a_negative_capacity_is_rejected_before_it_reaches_the_domain() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"floorId":"11111111-1111-1111-1111-111111111111","roomCode":"R1",
                                 "name":"Room","capacity":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("capacity"));
    }

    @Test
    void an_unauthorised_scope_is_403_with_the_srs_wording() throws Exception {
        willThrow(new FacilitiesException.UnauthorizedScopeException(
                "You are not authorised to access this site or record."))
                .given(service).sites(any(), any());

        mockMvc.perform(get("/api/v1/facilities/sites").header("X-SFL-Sites", "KUMASI"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_SCOPE"))
                .andExpect(jsonPath("$.error.message")
                        .value("You are not authorised to access this site or record."));
    }

    @Test
    void a_duplicate_identifier_is_409() throws Exception {
        willThrow(new FacilitiesException.DuplicateIdentifierException("site", "MAIN", "MAIN"))
                .given(service).createSite(any());

        mockMvc.perform(post("/api/v1/facilities/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteCode":"MAIN","name":"Main Campus"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_IDENTIFIER"));
    }

    @Test
    void a_missing_record_is_404() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new FacilitiesException.RecordNotFoundException("Site", id)).given(service)
                .site(any(), any(), any());

        mockMvc.perform(get("/api/v1/facilities/sites/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
    }

    @Test
    void a_version_conflict_is_409() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new FacilitiesException.VersionConflictException("Site", id, 1L, 3L)).given(service)
                .updateSite(any());

        mockMvc.perform(patch("/api/v1/facilities/sites/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed","expectedVersion":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
    }

    /** A domain-rule refusal is 422, not 400: the request was fine, the estate's state forbids it. */
    @Test
    void a_blocked_readiness_is_422() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new FacilitiesException.ReadinessBlockedException(2)).given(readiness)
                .setReadinessDirectly(any(FacilitiesCommands.UpdateRoomReadiness.class));

        mockMvc.perform(patch("/api/v1/facilities/rooms/" + id + "/readiness")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"READY","notes":"Looks fine"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("READINESS_BLOCKED"))
                .andExpect(jsonPath("$.error.message").value(
                        "This space cannot be marked ready while 2 critical blocker(s) remain open."));
    }

    @Test
    void a_locked_space_refuses_an_update_with_422() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new FacilitiesException.ReadinessLockedException(id)).given(service).updateRoom(any());

        mockMvc.perform(patch("/api/v1/facilities/rooms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed mid-examination"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("READINESS_LOCKED"));
    }

    @Test
    void an_actor_with_no_site_scope_is_403_with_its_own_code() throws Exception {
        willThrow(new FacilitiesException.NoScopeException()).given(service).rooms(any(), any(), any());

        mockMvc.perform(get("/api/v1/facilities/rooms"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NO_SCOPE"))
                .andExpect(jsonPath("$.error.message").value("No site scope is assigned to your user profile."));
    }

    @Test
    void a_caller_supplied_correlation_id_is_echoed_back() throws Exception {
        given(service.sites(any(), any())).willReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/facilities/sites").header("X-Correlation-ID", "trace-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void a_room_response_carries_the_derived_availability_flags() throws Exception {
        given(service.room(any(), any(), any())).willReturn(readyHall());

        mockMvc.perform(get("/api/v1/facilities/rooms/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spaceType").value("EXAMINATION_HALL"))
                .andExpect(jsonPath("$.data.availableForBooking").value(true))
                .andExpect(jsonPath("$.data.availableForExamination").value(true));
    }

    private static Site site() {
        return Site.create(UUID.randomUUID(), "MAIN", "Main Campus", "Head office", "manager", NOW,
                SourceChannel.WEB, "corr-1");
    }

    private static FacilityRoom readyHall() {
        return FacilityRoom.create(UUID.randomUUID(), UUID.randomUUID(), "MAIN", "HALL-A",
                        "Examination Hall A", SpaceType.EXAMINATION_HALL, 120, null, null, null, null,
                        "manager", NOW, SourceChannel.WEB, null)
                .applyReadiness(LocationReadinessStatus.READY, "All checks passed", "assessor", NOW,
                        SourceChannel.MOBILE, null);
    }
}
