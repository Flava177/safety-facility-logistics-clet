package gh.edu.clet.sfl.safetysecurity.emergency.domain.model;

/** Supported notification delivery channels. Vendor gateways deliver; SFL governs. */
public enum ChannelType {
    SMS,
    EMAIL,
    PUSH,
    VOICE,
    SIREN,
    DIGITAL_SIGNAGE
}
