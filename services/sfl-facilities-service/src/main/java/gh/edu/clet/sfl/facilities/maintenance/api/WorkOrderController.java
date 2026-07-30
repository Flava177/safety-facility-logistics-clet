package gh.edu.clet.sfl.facilities.maintenance.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.facilities.maintenance.application.AssignWorkOrderCommand;
import gh.edu.clet.sfl.facilities.maintenance.application.CloseWorkOrderCommand;
import gh.edu.clet.sfl.facilities.maintenance.application.CreateWorkOrderFromFaultCommand;
import gh.edu.clet.sfl.facilities.maintenance.application.WorkOrderService;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.shared.api.DevActorHeaderResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facilities/work-orders")
public class WorkOrderController {

    private final WorkOrderService service;
    private final DevActorHeaderResolver actorResolver;

    public WorkOrderController(WorkOrderService service, DevActorHeaderResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/from-fault")
    public ResponseEntity<ApiResponse<WorkOrder>> createFromFault(@Valid @RequestBody CreateWorkOrderFromFaultRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String userId,
            @RequestHeader(name = "X-SFL-Display-Name", required = false) String displayName,
            @RequestHeader(name = "X-SFL-Roles", required = false) String roles,
            @RequestHeader(name = "X-SFL-Sites", required = false) String sites,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        ActorContext actor = actorResolver.resolve(userId, displayName, roles, sites, correlationId);
        WorkOrder result = service.createFromFault(new CreateWorkOrderFromFaultCommand(request.facilityFaultId(), actor));
        return ResponseEntity.created(URI.create("/api/v1/facilities/work-orders/" + result.id())).body(ApiResponse.ok(result));
    }

    @PatchMapping("/{workOrderId}/assignment")
    public ApiResponse<WorkOrder> assign(@PathVariable UUID workOrderId, @Valid @RequestBody AssignWorkOrderRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String userId,
            @RequestHeader(name = "X-SFL-Display-Name", required = false) String displayName,
            @RequestHeader(name = "X-SFL-Roles", required = false) String roles,
            @RequestHeader(name = "X-SFL-Sites", required = false) String sites,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        ActorContext actor = actorResolver.resolve(userId, displayName, roles, sites, correlationId);
        return ApiResponse.ok(service.assign(new AssignWorkOrderCommand(workOrderId, request.assignedTo(), actor)));
    }

    @PatchMapping("/{workOrderId}/closure")
    public ApiResponse<WorkOrder> close(@PathVariable UUID workOrderId, @Valid @RequestBody CloseWorkOrderRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String userId,
            @RequestHeader(name = "X-SFL-Display-Name", required = false) String displayName,
            @RequestHeader(name = "X-SFL-Roles", required = false) String roles,
            @RequestHeader(name = "X-SFL-Sites", required = false) String sites,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        ActorContext actor = actorResolver.resolve(userId, displayName, roles, sites, correlationId);
        return ApiResponse.ok(service.close(new CloseWorkOrderCommand(workOrderId, request.closureNotes(), actor)));
    }

    @GetMapping
    public ApiResponse<List<WorkOrder>> findAll(
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String userId,
            @RequestHeader(name = "X-SFL-Display-Name", required = false) String displayName,
            @RequestHeader(name = "X-SFL-Roles", required = false) String roles,
            @RequestHeader(name = "X-SFL-Sites", required = false) String sites,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        ActorContext actor = actorResolver.resolve(userId, displayName, roles, sites, correlationId);
        return ApiResponse.ok(service.findAll(actor));
    }

    @GetMapping("/{workOrderId}")
    public ApiResponse<WorkOrder> findById(@PathVariable UUID workOrderId,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String userId,
            @RequestHeader(name = "X-SFL-Display-Name", required = false) String displayName,
            @RequestHeader(name = "X-SFL-Roles", required = false) String roles,
            @RequestHeader(name = "X-SFL-Sites", required = false) String sites,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        ActorContext actor = actorResolver.resolve(userId, displayName, roles, sites, correlationId);
        return ApiResponse.ok(service.findById(workOrderId, actor));
    }

    public record CreateWorkOrderFromFaultRequest(@NotNull UUID facilityFaultId) {
    }

    public record AssignWorkOrderRequest(@NotBlank @Size(max = 160) String assignedTo) {
    }

    public record CloseWorkOrderRequest(@NotBlank @Size(max = 2000) String closureNotes) {
    }
}