package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands.ReceiveIntegrationMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetIntegrationApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateIntegrationMessageException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Signed provider webhook: inbox acceptance precedes the idempotent domain command. */
@RestController @RequestMapping("/api/v1/fuel/integrations") @io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Integrations")
public class FuelProviderIntegrationController {
    private static final TypeReference<Map<String,Object>> MAP=new TypeReference<>(){};
    private final FleetIntegrationApplicationService inbox;private final FuelApplicationService fuel;private final FleetActorResolver actors;private final ObjectMapper json;
    public FuelProviderIntegrationController(FleetIntegrationApplicationService i,FuelApplicationService f,FleetActorResolver a,ObjectMapper j){inbox=i;fuel=f;actors=a;json=j;}
    @PostMapping("/providers/{provider}/transactions") public ApiResponse<FuelTransaction> receive(@PathVariable String provider,@RequestHeader("X-SFL-Integration-Signature")String signature,@RequestHeader("X-SFL-Integration-Timestamp")Instant signedAt,@RequestBody String raw,HttpServletRequest h)throws JacksonException{JsonNode root=json.readTree(raw);Map<String,Object> p=json.convertValue(root.path("payload"),MAP);String key=actors.resolveIdempotencyKey(h);var actor=actors.resolve(h);try{inbox.receive(new ReceiveIntegrationMessage(provider,key,text(root,"eventType"),text(root,"siteCode"),Instant.parse(text(root,"occurredAt")),signature,signedAt,raw,p,actor,SourceChannel.INTEGRATION));}catch(DuplicateIntegrationMessageException ignored){/* Domain idempotency returns the original result below. */}return ApiResponse.ok(fuel.capture(new FuelApplicationService.CaptureFuel(text(root,"siteCode"),str(p,"providerTransactionId"),provider,uuid(p,"vehicleId"),uuid(p,"driverId"),nullableUuid(p.get("tripId")),Instant.parse(str(p,"occurredAt")),str(p,"vendorReference"),nullable(p.get("stationReference")),str(p,"fuelProduct"),decimal(p,"quantity"),str(p,"quantityUnit"),decimal(p,"unitPrice"),nullableDecimal(p.get("totalCost")),str(p,"currency"),nullable(p.get("cardReference")),Long.parseLong(str(p,"odometerReading")),nullableUuid(p.get("receiptEvidenceId")),nullable(p.get("comments")),key,actor,SourceChannel.INTEGRATION)));}
    @GetMapping("/health") public ApiResponse<FleetIntegrationApplicationService.IntegrationHealth> health(HttpServletRequest h){return ApiResponse.ok(inbox.health(actors.resolve(h)));}
    @GetMapping("/outbox/health") public ApiResponse<gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelOutboxAdminPort.OutboxHealth> outboxHealth(HttpServletRequest h){return ApiResponse.ok(fuel.integrationHealth(actors.resolve(h)));}
    @PostMapping("/outbox/{messageId}/replay") public ApiResponse<Map<String,Object>> replay(@PathVariable UUID messageId,HttpServletRequest h){boolean requeued=fuel.replayIntegration(messageId,actors.resolve(h),SourceChannel.INTEGRATION);return ApiResponse.ok(Map.<String,Object>of("messageId",messageId,"requeued",requeued));}
    private static String text(JsonNode n,String f){return n.path(f).asText();}private static String str(Map<String,Object> p,String f){Object v=p.get(f);if(v==null||String.valueOf(v).isBlank())throw new IllegalArgumentException(f+" is required");return String.valueOf(v);}private static String nullable(Object v){return v==null||String.valueOf(v).isBlank()?null:String.valueOf(v);}private static UUID uuid(Map<String,Object> p,String f){return UUID.fromString(str(p,f));}private static UUID nullableUuid(Object v){String s=nullable(v);return s==null?null:UUID.fromString(s);}private static BigDecimal decimal(Map<String,Object> p,String f){return new BigDecimal(str(p,f));}private static BigDecimal nullableDecimal(Object v){String s=nullable(v);return s==null?null:new BigDecimal(s);}
}
