package gh.edu.clet.sfl.fleetlogistics.assets.application;

import java.util.UUID;

public interface ServiceOutbox {

    void record(String eventType, int eventVersion, String aggregateType, UUID aggregateId,
            String siteScope, String correlationId, String causationId, Object payload);
}