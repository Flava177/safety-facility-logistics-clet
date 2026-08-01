-- Release 1 demo closure: fleet compliance documents now use the same four-value
-- retention vocabulary as governed fleet evidence. Existing rows are translated
-- conservatively before the Java enum changes are applied.

UPDATE fleet_logistics.vehicle_compliance_documents
   SET retention_class = CASE retention_class
       WHEN 'OPERATIONAL_SHORT' THEN 'OPERATIONAL_1_YEAR'
       WHEN 'OPERATIONAL_STANDARD' THEN 'OPERATIONAL_1_YEAR'
       WHEN 'COMPLIANCE' THEN 'COMPLIANCE_7_YEARS'
       WHEN 'INCIDENT' THEN 'INCIDENT_10_YEARS'
       WHEN 'STATUTORY' THEN 'INCIDENT_10_YEARS'
       ELSE retention_class
   END
 WHERE retention_class IN (
       'OPERATIONAL_SHORT',
       'OPERATIONAL_STANDARD',
       'COMPLIANCE',
       'INCIDENT',
       'STATUTORY'
   );

ALTER TABLE fleet_logistics.vehicle_compliance_documents
    ADD CONSTRAINT ck_vehicle_compliance_retention_canonical
    CHECK (retention_class IN (
        'OPERATIONAL_1_YEAR',
        'COMPLIANCE_7_YEARS',
        'INCIDENT_10_YEARS',
        'LEGAL_HOLD'
    ));
