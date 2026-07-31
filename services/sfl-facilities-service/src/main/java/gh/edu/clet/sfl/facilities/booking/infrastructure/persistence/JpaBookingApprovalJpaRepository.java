package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBookingApprovalJpaRepository extends JpaRepository<BookingApprovalRecord, UUID> {

    List<BookingApprovalRecord> findByBookingIdOrderByDecidedAtAsc(UUID bookingId);
}
