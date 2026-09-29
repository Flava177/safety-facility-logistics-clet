package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFrequency;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningSchedule;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA mapping for {@link CleaningSchedule}. Days and times are stored as comma-separated text (see V19),
 * so the mapping needs no custom type; this class is the one place that parses and writes them.
 */
@Entity
@Table(name = "cleaning_schedules", schema = "facilities")
public class CleaningScheduleRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false, length = 40)
    private SpaceType spaceType;
    @Column(name = "room_id")
    private UUID roomId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CleaningFrequency frequency;
    @Column(name = "days_of_week", length = 120)
    private String daysOfWeek;
    @Column(name = "times_of_day", nullable = false, length = 200)
    private String timesOfDay;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(nullable = false)
    private boolean active;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningScheduleRecord() {
    }

    static CleaningScheduleRecord empty() {
        return new CleaningScheduleRecord();
    }

    public void apply(CleaningSchedule schedule) {
        id = schedule.id();
        siteCode = schedule.siteCode();
        name = schedule.name();
        spaceType = schedule.spaceType();
        roomId = schedule.roomId();
        frequency = schedule.frequency();
        daysOfWeek = schedule.daysOfWeek().isEmpty() ? null
                : schedule.daysOfWeek().stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
        timesOfDay = schedule.timesOfDay().stream().map(LocalTime::toString).collect(Collectors.joining(","));
        durationMinutes = schedule.durationMinutes();
        active = schedule.active();
        metadata = RecordMetadataEmbeddable.from(schedule.metadata());
    }

    public CleaningSchedule toDomain() {
        Set<DayOfWeek> days = daysOfWeek == null || daysOfWeek.isBlank() ? Set.of()
                : Arrays.stream(daysOfWeek.split(",")).map(String::strip).map(DayOfWeek::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        List<LocalTime> times = Arrays.stream(timesOfDay.split(",")).map(String::strip).map(LocalTime::parse).toList();
        return new CleaningSchedule(id, siteCode, name, spaceType, roomId, frequency, days, times, durationMinutes,
                active, metadata.toDomain(recordVersion()));
    }
}
