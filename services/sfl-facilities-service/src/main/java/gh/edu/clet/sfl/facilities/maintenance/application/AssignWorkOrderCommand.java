package gh.edu.clet.sfl.facilities.maintenance.application;

import java.util.UUID;

import gh.edu.clet.sfl.common.security.ActorContext;

public record AssignWorkOrderCommand(UUID workOrderId, String assignedTo, ActorContext actor) {
}