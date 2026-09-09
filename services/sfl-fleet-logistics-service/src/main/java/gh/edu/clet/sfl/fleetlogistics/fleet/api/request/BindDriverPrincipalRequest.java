package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/v1/fleet/drivers/{driverId}/principal}.
 *
 * <p>{@code principalSubject} is the identity provider's subject claim ({@code sub}) for the person who
 * signs in as this driver - the platform's OIDC provider is Zitadel, but nothing here reads a
 * provider-specific shape out of it. Null or blank unlinks the profile, which is the supported way to
 * revoke a leaver's access to the record without archiving a profile that still has trips attached to
 * it.
 *
 * <p>Not {@code @NotBlank}, therefore, and that is deliberate rather than an oversight: unlinking has
 * to be expressible, and an endpoint that can only ever set a value leaves no way to undo a mistake.
 */
public record BindDriverPrincipalRequest(
        @Size(max = 160) String principalSubject,
        Long expectedVersion) {
}
