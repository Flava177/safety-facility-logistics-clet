package gh.edu.clet.sfl.ifimp.facilities.application;

import java.util.UUID;

import gh.edu.clet.sfl.ifimp.facilities.domain.DeviceReferenceType;

public record RegisterDeviceReferenceCommand(
        String siteCode,
        String deviceCode,
        String name,
        DeviceReferenceType type,
        UUID roomId,
        String locationCode,
        String vendor,
        String actor,
        String correlationId) {
}
