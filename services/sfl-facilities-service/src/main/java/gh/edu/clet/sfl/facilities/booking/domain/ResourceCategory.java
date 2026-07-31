package gh.edu.clet.sfl.facilities.booking.domain;

/**
 * What kind of thing a bookable resource is.
 *
 * <p>Distinct from S152's {@code AssetCategory} on purpose. An asset is fixed plant whose condition
 * feeds a space's readiness — a generator, a chiller. A resource is a thing you book alongside a
 * room and that can be in only one place at a time. A projector permanently mounted in a hall is an
 * asset; one wheeled between halls is a resource, and the same physical object can move between the
 * two registers over its life.
 */
public enum ResourceCategory {
    PROJECTOR,
    AUDIO_VISUAL,
    PUBLIC_ADDRESS,
    FURNITURE_SET,
    COMPUTING,
    RECORDING,
    CATERING,
    OTHER
}
