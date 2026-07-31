package gh.edu.clet.sfl.facilities.masterdata.domain;

/**
 * What kind of estate record a zone contains.
 *
 * <p>A zone is heterogeneous by nature: an evacuation zone covers buildings and floors, a CCTV zone
 * covers cameras, an examination zone covers halls. Modelling membership as (type, id) rather than as
 * four separate join tables keeps "what is in this zone" one query, which is what S162a life-safety
 * and S174 recipient-zone resolution will each need.
 */
public enum ZoneMemberType {
    BUILDING,
    FLOOR,
    ROOM,
    DEVICE
}
