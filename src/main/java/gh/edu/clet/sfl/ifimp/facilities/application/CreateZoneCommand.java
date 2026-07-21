package gh.edu.clet.sfl.ifimp.facilities.application;

public record CreateZoneCommand(
        String siteCode,
        String zoneCode,
        String name,
        String purpose,
        String actor,
        String correlationId) {
}
