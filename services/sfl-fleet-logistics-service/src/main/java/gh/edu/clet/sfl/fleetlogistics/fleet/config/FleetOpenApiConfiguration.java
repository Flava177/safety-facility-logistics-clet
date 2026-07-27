package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI documentation for S166 and S168_fuel in the FTLMP service boundary. */
@Configuration(proxyBeanMethods = false)
class FleetOpenApiConfiguration {

    private static final String HEADER_USER = "X-SFL-User";
    private static final String HEADER_DISPLAY_NAME = "X-SFL-Display-Name";
    private static final String HEADER_ROLES = "X-SFL-Roles";
    private static final String HEADER_SITES = "X-SFL-Sites";
    private static final String HEADER_SOURCE_CHANNEL = "X-SFL-Source-Channel";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    @Bean
    OpenAPI fleetOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SFL Fleet, Fuel, Driver and Dispatch Operations API")
                        .description("""
                                Phase 1 S166 Fleet and Vehicle Management, S168_fuel Fuel Management and Driver
                                Logbooks, and S171 Mailroom / Courier and Dispatch Tracking APIs.

                                Local development uses actor headers when SFL_SECURITY_ENABLED=false. In Swagger UI,
                                click Authorize and provide values such as:
                                X-SFL-User=fleet.manager
                                X-SFL-Display-Name=Fleet Manager
                                X-SFL-Roles=FLEET_MANAGER,FLEET_OFFICER
                                X-SFL-Sites=HQ,ACCRA
                                X-SFL-Source-Channel=SWAGGER
                                X-Correlation-ID=swagger-local
                                Idempotency-Key=<unique value for mutating requests>
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
                        .addList(HEADER_USER)
                        .addList(HEADER_DISPLAY_NAME)
                        .addList(HEADER_ROLES)
                        .addList(HEADER_SITES)
                        .addList(HEADER_SOURCE_CHANNEL)
                        .addList(HEADER_CORRELATION_ID)
                        .addList(HEADER_IDEMPOTENCY_KEY))
                .addTagsItem(new Tag().name("System").description("Service status and platform metadata"))
                .addTagsItem(new Tag().name("Vehicles").description("Vehicle register, compliance and service history"))
                .addTagsItem(new Tag().name("Drivers").description("Driver register and eligibility checks"))
                .addTagsItem(new Tag().name("Trips").description("Trip planning, assignment, inspections and closure"))
                .addTagsItem(new Tag().name("Workflow").description("Fleet workflow execution, SLA and transitions"))
                .addTagsItem(new Tag().name("Evidence").description("Evidence references and export requests"))
                .addTagsItem(new Tag().name("Audit").description("Audit records and hash-chain verification"))
                .addTagsItem(new Tag().name("Integrations").description("Secure integration inbox and health"))
                .addTagsItem(new Tag().name("Dashboards and Reports")
                        .description("Operational dashboards, drill-downs and readiness reports"))
                .addTagsItem(new Tag().name("Fuel Transactions").description("Manual, provider and imported fuel records"))
                .addTagsItem(new Tag().name("Driver Logbooks").description("Driver journey logbook and review workflow"))
                .addTagsItem(new Tag().name("Fuel Reconciliation").description("Policy-versioned reconciliation"))
                .addTagsItem(new Tag().name("Fuel Anomalies").description("Fuel exception investigation workflow"))
                .addTagsItem(new Tag().name("Fuel Policies").description("Effective-dated fuel limits and rules"))
                .addTagsItem(new Tag().name("Fuel Integrations").description("Provider imports, inbox and health"))
                .addTagsItem(new Tag().name("Fuel Dashboards and Reports").description("Fuel KPIs, drilldown and exports"))
                .addTagsItem(new Tag().name("Dispatch Items")
                        .description("Mailroom / Courier and Dispatch Tracking: courier item register and lifecycle"))
                .addTagsItem(new Tag().name("Inbound Mail")
                        .description("Inbound mail registration and internal distribution with acknowledgement"))
                .addTagsItem(new Tag().name("Dispatch Manifests")
                        .description("Dispatch manifests: items, seal IDs, counts, trip link and dispatch"))
                .addTagsItem(new Tag().name("Chain of Custody")
                        .description("Unbroken chain-of-custody handovers and gap detection"))
                .addTagsItem(new Tag().name("Dispatch Receipts")
                        .description("Destination receipt confirmation and variance handling (edge-capable)"))
                .addTagsItem(new Tag().name("Return Reconciliation")
                        .description("Return-leg / reverse-logistics reconciliation against the manifest"))
                .addTagsItem(new Tag().name("Dispatch Exceptions")
                        .description("Accountable dispatch exception/case workflow and SLA"))
                .addTagsItem(new Tag().name("Dispatch Integrations")
                        .description("Optional scanner/carrier ingestion, secure inbox and outbox health/replay"))
                .addTagsItem(new Tag().name("Dispatch Dashboards and Reports")
                        .description("Mailroom / Courier and Dispatch Tracking dashboards and CSV exports"));
    }

    private static SecurityScheme headerScheme(String headerName) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName);
    }
}
