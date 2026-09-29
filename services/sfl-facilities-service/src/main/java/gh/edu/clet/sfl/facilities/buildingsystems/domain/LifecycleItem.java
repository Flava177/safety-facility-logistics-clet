package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * The dated lifecycle obligations a device carries - SRS-SFL-S156-04: "firmware/calibration due dates
 * raise reminders". Warranty expiry is included because the user story names warranty status and a
 * lapsed warranty discovered at the moment of a failure is the tribal-knowledge outcome the story exists
 * to prevent.
 */
public enum LifecycleItem {
    CALIBRATION,
    FIRMWARE_REVIEW,
    WARRANTY_EXPIRY
}
