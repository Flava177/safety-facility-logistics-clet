package gh.edu.clet.sfl.facilities.masterdata.domain;

/**
 * What a space is for.
 *
 * <p>The existing model carried room type as free text, which cannot answer the questions S159 and
 * S152-05 ask of it: which spaces are bookable, which can host an examination, which are back-of-house
 * and should never appear in either list. A typed set can.
 *
 * <p>{@link #isBookableByDefault()} and {@link #isExaminationCapableByDefault()} are defaults, not
 * rules — a space overrides both, because a lecture hall under refurbishment is not bookable and a
 * particular meeting room may be approved for examinations. The type sets the sensible starting point
 * so an operator registering two hundred rooms is not answering the same two questions two hundred
 * times.
 *
 * <p>Named for the CLET estate: {@link #MOOT_COURTROOM} is a real space type here, and lumping it
 * into "other" would hide it from the booking screens S159 will build.
 */
public enum SpaceType {

    OFFICE(false, false),
    MEETING_ROOM(true, false),
    LECTURE_HALL(true, true),
    MOOT_COURTROOM(true, true),
    EXAMINATION_HALL(true, true),
    LABORATORY(true, false),
    LIBRARY(false, false),
    AUDITORIUM(true, true),
    /** Consumable and equipment storage. */
    STORE(false, false),
    /** Plant, switch and server rooms — back-of-house, never bookable. */
    PLANT_ROOM(false, false),
    /** Corridors, lobbies, stairwells. Modelled because zones and devices attach to them. */
    CIRCULATION(false, false),
    SANITARY(false, false),
    RECEPTION(false, false),
    CAFETERIA(false, false),
    ACCOMMODATION(false, false),
    OTHER(false, false);

    private final boolean bookableByDefault;
    private final boolean examinationCapableByDefault;

    SpaceType(boolean bookableByDefault, boolean examinationCapableByDefault) {
        this.bookableByDefault = bookableByDefault;
        this.examinationCapableByDefault = examinationCapableByDefault;
    }

    /** Whether a space of this type is offered for booking unless told otherwise. */
    public boolean isBookableByDefault() {
        return bookableByDefault;
    }

    /** Whether a space of this type can host an examination unless told otherwise. */
    public boolean isExaminationCapableByDefault() {
        return examinationCapableByDefault;
    }
}
