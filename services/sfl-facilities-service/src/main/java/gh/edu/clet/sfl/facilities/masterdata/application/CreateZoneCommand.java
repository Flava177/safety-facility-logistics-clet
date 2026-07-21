package gh.edu.clet.sfl.facilities.masterdata.application;

public record CreateZoneCommand(
        String siteCode,
        String zoneCode,
        String name,
        String purpose,
        String actor,
        String correlationId) {
}
