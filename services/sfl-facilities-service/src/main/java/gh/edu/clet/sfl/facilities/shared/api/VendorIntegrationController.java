package gh.edu.clet.sfl.facilities.shared.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationRegistry;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The truthful integration status of every Phase 2 vendor feed - SRS 2026/002 CORR-07. */
@RestController
@RequestMapping("/api/v1/facilities/vendor-integrations")
@Tag(name = "Vendor integrations", description = "Procurement-gate status of each vendor integration")
public class VendorIntegrationController {

    private final VendorIntegrationRegistry registry;

    public VendorIntegrationController(VendorIntegrationRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "Procurement-gate status of each vendor integration",
            description = "Never reports an integration as integrated: the strongest status is that SRS §5.2 "
                    + "evidence is on file.")
    public ApiResponse<List<VendorIntegrationRegistry.IntegrationStatus>> statuses(ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(registry.statuses(actor, channel));
    }
}
