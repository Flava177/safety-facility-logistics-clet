package gh.edu.clet.sfl.facilities.masterdata.domain;

/**
 * The kind of fixed plant or equipment a facility asset is.
 *
 * <p>This is the "facility system / utility reference" the build brief asked for, delivered as a
 * category on one aggregate rather than as a second aggregate beside {@link FacilityAsset} (gap
 * report C-03). A chiller and the HVAC system it belongs to are the same kind of record with
 * different scope; two aggregates would overlap on every field and force maintenance to raise work
 * orders against either.
 *
 * <p>Distinct from {@link DeviceReferenceType}, and the distinction is load-bearing:
 * a {@code DeviceReference} is an <em>integration endpoint</em> — something a vendor system reports
 * on, like a camera or a card reader. A {@code FacilityAsset} is <em>fixed plant maintenance is
 * raised against</em>. A fire panel is both, which is why an asset may carry a device reference.
 */
public enum AssetCategory {

    HVAC,
    ELECTRICAL,
    /** Distribution boards, changeover switches, sub-metering. */
    POWER_DISTRIBUTION,
    GENERATOR,
    UPS,
    PLUMBING,
    WATER_SYSTEM,
    LIFT,
    FIRE_SYSTEM,
    SECURITY_SYSTEM,
    /** Structure, roofing, doors, glazing — what a building inspection reports on. */
    BUILDING_FABRIC,
    /** Projectors, screens, PA — the equipment a hall's readiness depends on. */
    AUDIO_VISUAL,
    IT_INFRASTRUCTURE,
    FURNITURE,
    GROUNDS,
    OTHER
}
