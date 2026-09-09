package gh.edu.clet.sfl.facilities.booking.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gh.edu.clet.sfl.facilities.booking.application.BookableResourceService;
import gh.edu.clet.sfl.facilities.booking.application.BookingApplicationService;
import gh.edu.clet.sfl.facilities.booking.application.BookingSetupService;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
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
 * The S159 booking write path's HTTP contract, following the pattern
 * {@code FacilitiesMasterDataControllerTest} established: status code, error envelope, correlation id.
 */
@WebMvcTest(controllers = BookingController.class, excludeAutoConfiguration = {
        OAuth2ResourceServerAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({FacilitiesActorResolver.class, BookingControllerTest.FixedClock.class})
class BookingControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T09:00:00Z");
    private static final Instant NINE = Instant.parse("2026-08-10T09:00:00Z");
    private static final Instant TEN = Instant.parse("2026-08-10T10:00:00Z");

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
    private BookingApplicationService service;

    @MockitoBean
    private BookableResourceService resources;

    @MockitoBean
    private BookingSetupService setup;

    @Test
    void requesting_a_booking_returns_201_with_a_location_header() throws Exception {
        Booking booking = booking();
        given(service.request(any())).willReturn(booking);

        mockMvc.perform(post("/api/v1/facilities/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SFL-User", "requester")
                        .header("X-SFL-Roles", "FACILITIES_MANAGER")
                        .header("X-SFL-Sites", "MAIN")
                        .content("""
                                {"roomId":"%s","purpose":"LECTURE","title":"Contracts revision",
                                 "startsAt":"2026-08-10T09:00:00Z","endsAt":"2026-08-10T10:00:00Z",
                                 "expectedAttendees":40}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/facilities/bookings/" + booking.id()))
                .andExpect(jsonPath("$.data.bookingReference").value("BK-MAIN-000001"))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));
    }

    @Test
    void a_missing_room_id_is_400_with_the_field_named() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"LECTURE","title":"Contracts revision",
                                 "startsAt":"2026-08-10T09:00:00Z","endsAt":"2026-08-10T10:00:00Z",
                                 "expectedAttendees":40}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data[0].field").value("roomId"))
                .andExpect(jsonPath("$.error.correlationId").exists());
    }

    @Test
    void rescheduling_into_a_clash_is_409_with_the_booking_conflict_code() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new FacilitiesException.BookingConflictException(
                "HALL-A is already booked for part of that window."))
                .given(service).reschedule(any());

        mockMvc.perform(patch("/api/v1/facilities/bookings/" + id + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"2026-08-10T09:00:00Z","endsAt":"2026-08-10T10:00:00Z",
                                 "expectedVersion":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKING_CONFLICT"));
    }

    private static Booking booking() {
        return Booking.request(UUID.randomUUID(), "BK-MAIN-000001", "MAIN", UUID.randomUUID(), "HALL-A",
                BookingPurpose.LECTURE, "Contracts revision", null, BookingWindow.of(NINE, TEN), 40, null,
                false, null, "requester", NOW, SourceChannel.WEB, "corr-1");
    }
}
