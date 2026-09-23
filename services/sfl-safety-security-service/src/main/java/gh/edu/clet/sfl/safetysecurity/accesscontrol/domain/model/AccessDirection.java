package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

/** Entry/exit direction of a {@code GRANTED} event, where the vendor system reports one - the basis
 * for occupancy counting and anti-passback detection (S160a-06). {@code UNKNOWN} for a vendor system
 * (or event kind) that carries no direction; such events are ignored for occupancy purposes. */
public enum AccessDirection {
    ENTRY,
    EXIT,
    UNKNOWN
}
