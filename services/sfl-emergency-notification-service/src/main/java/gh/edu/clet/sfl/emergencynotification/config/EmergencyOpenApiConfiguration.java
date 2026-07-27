package gh.edu.clet.sfl.emergencynotification.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI documentation for S174 Emergency Mass Notification. */
@Configuration(proxyBeanMethods = false)
class EmergencyOpenApiConfiguration {

    private static final String HEADER_USER = "X-SFL-User";
    private static final String HEADER_DISPLAY_NAME = "X-SFL-Display-Name";
    private static final String HEADER_ROLES = "X-SFL-Roles";
    private static final String HEADER_SITES = "X-SFL-Sites";
    private static final String HEADER_SOURCE_CHANNEL = "X-SFL-Source-Channel";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    @Bean
    OpenAPI emergencyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SFL Emergency Mass Notification API")
                        .description("""
                                Phase 1 S174 Emergency Mass Notification service APIs.

                                Local development uses actor headers when SFL_SECURITY_ENABLED=false. In Swagger UI,
                                click Authorize and provide values such as:
                                X-SFL-User=emergency.coordinator
                                X-SFL-Display-Name=Emergency Coordinator
                                X-SFL-Roles=EMERGENCY_COORDINATOR
                                X-SFL-Sites=HQ,ACCRA
                                X-SFL-Source-Channel=WEB
                                X-Correlation-ID=swagger-local
                                Idempotency-Key=<unique value for mutating requests>

                                SFL governs activation, audience selection, incident linkage, acknowledgement tracking
                                and audit; certified life-safety actuation remains outside SFL (Arch guide 0E).
                                """)
                        .version("0.1.0-SNAPSHOT"))
                .components(new Components()
                        .addSecuritySchemes(HEADER_USER, headerScheme(HEADER_USER))
                        .addSecuritySchemes(HEADER_DISPLAY_NAME, headerScheme(HEADER_DISPLAY_NAME))
                        .addSecuritySchemes(HEADER_ROLES, headerScheme(HEADER_ROLES))
                        .addSecuritySchemes(HEADER_SITES, headerScheme(HEADER_SITES))
                        .addSecuritySchemes(HEADER_SOURCE_CHANNEL, headerScheme(HEADER_SOURCE_CHANNEL))
                        .addSecuritySchemes(HEADER_CORRELATION_ID, headerScheme(HEADER_CORRELATION_ID))
                        .addSecuritySchemes(HEADER_IDEMPOTENCY_KEY, headerScheme(HEADER_IDEMPOTENCY_KEY)))
                .addSecurityItem(new SecurityRequirement()
                        .addList(HEADER_USER).addList(HEADER_DISPLAY_NAME).addList(HEADER_ROLES).addList(HEADER_SITES)
                        .addList(HEADER_SOURCE_CHANNEL).addList(HEADER_CORRELATION_ID).addList(HEADER_IDEMPOTENCY_KEY))
                .addTagsItem(tag("Emergency Templates", "Reusable notification templates"))
                .addTagsItem(tag("Emergency Scenarios", "Declared emergency scenarios and defaults"))
                .addTagsItem(tag("Audience Groups", "Recipient audience groups"))
                .addTagsItem(tag("Recipient Zones", "Building/room/zone recipient scoping"))
                .addTagsItem(tag("Activations", "Emergency notification activation workflow"))
                .addTagsItem(tag("Break Glass", "Declared-emergency activation without pre-approval"))
                .addTagsItem(tag("Delivery and Acknowledgements", "Provider delivery-status and acknowledgement callbacks"))
                .addTagsItem(tag("Drills", "Notification drill runs and performance"))
                .addTagsItem(tag("Integrations", "Secure inbox, outbox health and privileged replay"))
                .addTagsItem(tag("Dashboards and Reports", "Emergency indicators, freshness and CSV exports"));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private static SecurityScheme headerScheme(String headerName) {
        return new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(headerName);
    }
}
