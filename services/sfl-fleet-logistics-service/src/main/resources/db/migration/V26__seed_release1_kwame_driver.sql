-- =====================================================================================
-- Release 1 demo seed: the driver portal account must have a matching fleet driver record.
--
-- The UI seeded account is:
--   email/actor selection: driver@clet.gh
--   actor subject:         kwame.driver
--   display name:          Kwame Driver
--
-- Driver-only reads are narrowed through driver_profile_references.principal_subject, so the
-- trip assigned by a Fleet Manager only appears in "My driving day" when a profile is bound to
-- principal_subject = 'kwame.driver'.
-- =====================================================================================

DO $$
DECLARE
    existing_by_principal UUID;
    existing_by_staff UUID;
    chosen_id UUID;
BEGIN
    SELECT id
      INTO existing_by_principal
      FROM fleet_logistics.driver_profile_references
     WHERE principal_subject = 'kwame.driver'
       AND lifecycle_status <> 'ARCHIVED'
     ORDER BY created_at
     LIMIT 1;

    SELECT id
      INTO existing_by_staff
      FROM fleet_logistics.driver_profile_references
     WHERE site_code = 'CLET-HQ'
       AND upper(staff_reference) = upper('kwame.driver')
       AND lifecycle_status <> 'ARCHIVED'
     ORDER BY created_at
     LIMIT 1;

    chosen_id := COALESCE(existing_by_principal, existing_by_staff);

    IF chosen_id IS NULL THEN
        INSERT INTO fleet_logistics.driver_profile_references (
            id,
            staff_reference,
            display_name,
            licence_number,
            licence_class,
            licence_expires_on,
            medical_clearance_expires_on,
            site_code,
            responsible_unit,
            lifecycle_status,
            eligibility_status,
            suspension_reason,
            principal_subject,
            created_by,
            created_at,
            last_modified_by,
            last_modified_at,
            version,
            source_channel,
            audit_correlation_id
        ) VALUES (
            gen_random_uuid(),
            'KWAME.DRIVER',
            'Kwame Driver',
            'LIC-KWAME-RELEASE1',
            'B',
            DATE '2027-08-01',
            DATE '2027-08-01',
            'CLET-HQ',
            'Transportation and Logistics Unit',
            'ACTIVE',
            'ELIGIBLE',
            NULL,
            'kwame.driver',
            'release1.seed',
            now(),
            'release1.seed',
            now(),
            0,
            'MIGRATION',
            NULL
        );
    ELSE
        UPDATE fleet_logistics.driver_profile_references
           SET display_name = 'Kwame Driver',
               licence_class = 'B',
               licence_expires_on = GREATEST(licence_expires_on, DATE '2027-08-01'),
               medical_clearance_expires_on = GREATEST(
                   COALESCE(medical_clearance_expires_on, DATE '2027-08-01'),
                   DATE '2027-08-01'
               ),
               site_code = 'CLET-HQ',
               responsible_unit = 'Transportation and Logistics Unit',
               lifecycle_status = 'ACTIVE',
               eligibility_status = 'ELIGIBLE',
               suspension_reason = NULL,
               principal_subject = 'kwame.driver',
               last_modified_by = 'release1.seed',
               last_modified_at = now(),
               source_channel = 'MIGRATION'
         WHERE id = chosen_id;
    END IF;
END $$;
