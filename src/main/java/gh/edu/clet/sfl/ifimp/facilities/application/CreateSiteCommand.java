package gh.edu.clet.sfl.ifimp.facilities.application;

public record CreateSiteCommand(
        String siteCode,
        String name,
        String description,
        String actor,
        String correlationId) {
}
