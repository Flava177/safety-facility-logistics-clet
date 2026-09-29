package gh.edu.clet.sfl.facilities.shared.infrastructure.integration;

import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationCatalogPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** {@code sfl.facilities.vendor-integrations.*} - one entry per Phase 2 vendor integration. */
@Component
@ConfigurationProperties(prefix = "sfl.facilities")
public class ConfiguredVendorIntegrationCatalog implements VendorIntegrationCatalogPort {

    private Map<String, Entry> vendorIntegrations = new LinkedHashMap<>();

    @Override
    public List<VendorIntegration> integrations() {
        List<VendorIntegration> all = new ArrayList<>();
        vendorIntegrations.forEach((key, entry) -> all.add(new VendorIntegration(key, entry.system, entry.name,
                entry.classification, entry.adapter, entry.procurementGateEvidence)));
        return List.copyOf(all);
    }

    public Map<String, Entry> getVendorIntegrations() {
        return vendorIntegrations;
    }

    public void setVendorIntegrations(Map<String, Entry> vendorIntegrations) {
        this.vendorIntegrations = vendorIntegrations;
    }

    public static class Entry {
        private String system;
        private String name;
        private String classification;
        private String adapter;
        private String procurementGateEvidence;

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getClassification() {
            return classification;
        }

        public void setClassification(String classification) {
            this.classification = classification;
        }

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(String adapter) {
            this.adapter = adapter;
        }

        public String getProcurementGateEvidence() {
            return procurementGateEvidence;
        }

        public void setProcurementGateEvidence(String procurementGateEvidence) {
            this.procurementGateEvidence = procurementGateEvidence;
        }
    }
}
