package gh.edu.clet.sfl.common.security;

public record ActorContext(SiteScopedPrincipal principal, String correlationId) {

    public ActorContext {
        if (principal == null) {
            throw new IllegalArgumentException("principal is required");
        }
    }

    public String actorId() {
        return principal.subjectId();
    }
}