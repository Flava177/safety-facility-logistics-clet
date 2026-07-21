package gh.edu.clet.sfl.facilities.masterdata.application;

public record CreateSiteCommand(
        String siteCode,
        String name,
        String description,
        String actor,
        String correlationId) {
}
