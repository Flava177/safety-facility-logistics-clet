package gh.edu.clet.sfl.facilities.maintenance.application;

import java.util.UUID;

import gh.edu.clet.sfl.common.security.ActorContext;

public record CloseWorkOrderCommand(UUID workOrderId, String closureNotes, ActorContext actor) {
}