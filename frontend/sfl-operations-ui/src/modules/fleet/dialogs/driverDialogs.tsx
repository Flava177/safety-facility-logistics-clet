import { Alert, Box } from '@mui/material';
import { DriverResponse } from 'modules/fleet/api/dto';
import {
  DRIVER_LIFECYCLE_STATUSES,
  DriverLifecycleStatus,
  LICENCE_CLASSES,
  LicenceClass,
} from 'modules/fleet/api/enums';
import { driversApi } from 'modules/fleet/api/fleetApi';
import FormDialog from 'shared/components/FormDialog';
import { DateInput, EnumSelect, TextInput } from 'shared/components/fields';
import { useFleetForm } from 'shared/validation/useFleetForm';
import { compose, maxLength, required } from 'shared/validation/validators';

const twoColumn = {
  display: 'grid',
  gap: 2,
  gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
} as const;

interface BaseProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

/* ---------------------------------------------------------------------------------------------
 * Register a driver — POST /api/v1/fleet/drivers
 * ------------------------------------------------------------------------------------------- */

export const RegisterDriverDialog = ({
  open,
  onClose,
  onSaved,
  defaultSiteCode,
}: BaseProps & { defaultSiteCode: string }) => {
  const form = useFleetForm({
    initialValues: {
      staffReference: '',
      displayName: '',
      licenceNumber: '',
      licenceClass: '' as LicenceClass | '',
      licenceExpiresOn: '',
      medicalClearanceExpiresOn: '',
      siteCode: defaultSiteCode,
      responsibleUnit: '',
    },
    schema: {
      staffReference: compose(required('Staff reference'), maxLength('Staff reference', 80)),
      displayName: compose(required('Display name'), maxLength('Display name', 200)),
      licenceNumber: compose(required('Licence number'), maxLength('Licence number', 80)),
      licenceClass: required('Licence class'),
      licenceExpiresOn: required('Licence expiry'),
      siteCode: compose(required('Site code'), maxLength('Site code', 40)),
      responsibleUnit: compose(required('Responsible unit'), maxLength('Responsible unit', 160)),
    },
    onSubmit: async (values) => {
      await driversApi.register({
        staffReference: values.staffReference.trim(),
        displayName: values.displayName.trim(),
        licenceNumber: values.licenceNumber.trim(),
        licenceClass: values.licenceClass as LicenceClass,
        licenceExpiresOn: values.licenceExpiresOn,
        medicalClearanceExpiresOn: values.medicalClearanceExpiresOn || null,
        siteCode: values.siteCode.trim().toUpperCase(),
        responsibleUnit: values.responsibleUnit.trim(),
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Register a driver"
      description="Creates the HRMS-backed driver profile reference this service assigns trips against."
      submitLabel="Register driver"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Box sx={twoColumn}>
        <TextInput
          label="Staff reference"
          required
          value={form.values.staffReference}
          onChange={(value) => form.setValue('staffReference', value)}
          {...form.fieldProps('staffReference')}
        />
        <TextInput
          label="Display name"
          required
          value={form.values.displayName}
          onChange={(value) => form.setValue('displayName', value)}
          {...form.fieldProps('displayName')}
        />
        <TextInput
          label="Licence number"
          required
          value={form.values.licenceNumber}
          onChange={(value) => form.setValue('licenceNumber', value)}
          {...form.fieldProps('licenceNumber')}
        />
        <EnumSelect
          label="Licence class"
          required
          value={form.values.licenceClass}
          options={LICENCE_CLASSES}
          onChange={(value) => form.setValue('licenceClass', value)}
          renderOptionLabel={(option) => `Class ${option}`}
          {...form.fieldProps('licenceClass')}
        />
        <DateInput
          label="Licence expires on"
          required
          value={form.values.licenceExpiresOn}
          onChange={(value) => form.setValue('licenceExpiresOn', value)}
          {...form.fieldProps('licenceExpiresOn')}
        />
        <DateInput
          label="Medical clearance expires on"
          value={form.values.medicalClearanceExpiresOn}
          onChange={(value) => form.setValue('medicalClearanceExpiresOn', value)}
          {...form.fieldProps('medicalClearanceExpiresOn')}
        />
        <TextInput
          label="Site code"
          required
          value={form.values.siteCode}
          onChange={(value) => form.setValue('siteCode', value)}
          {...form.fieldProps('siteCode')}
        />
        <TextInput
          label="Responsible unit"
          required
          value={form.values.responsibleUnit}
          onChange={(value) => form.setValue('responsibleUnit', value)}
          {...form.fieldProps('responsibleUnit')}
        />
      </Box>
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Update a driver — PATCH /api/v1/fleet/drivers/{id}
 * ------------------------------------------------------------------------------------------- */

export const UpdateDriverDialog = ({
  open,
  onClose,
  onSaved,
  driver,
}: BaseProps & { driver: DriverResponse }) => {
  const form = useFleetForm({
    initialValues: {
      displayName: driver.displayName,
      licenceNumber: driver.licenceNumberMasked ? '' : (driver.licenceNumber ?? ''),
      licenceClass: driver.licenceClass as LicenceClass | '',
      licenceExpiresOn: driver.licenceExpiresOn,
      medicalClearanceExpiresOn: driver.medicalClearanceExpiresOn ?? '',
      responsibleUnit: driver.responsibleUnit,
      targetLifecycleStatus: '' as DriverLifecycleStatus | '',
      lifecycleReason: '',
    },
    schema: {
      displayName: compose(required('Display name'), maxLength('Display name', 200)),
      licenceNumber: compose(required('Licence number'), maxLength('Licence number', 80)),
      licenceClass: required('Licence class'),
      licenceExpiresOn: required('Licence expiry'),
      responsibleUnit: compose(required('Responsible unit'), maxLength('Responsible unit', 160)),
      lifecycleReason: maxLength('Lifecycle reason', 1000),
    },
    crossFieldValidate: (values) =>
      values.targetLifecycleStatus === 'SUSPENDED' && !values.lifecycleReason.trim()
        ? { lifecycleReason: 'A reason is required when suspending a driver.' }
        : {},
    onSubmit: async (values) => {
      await driversApi.update(driver.id, {
        displayName: values.displayName.trim(),
        licenceNumber: values.licenceNumber.trim(),
        licenceClass: values.licenceClass as LicenceClass,
        licenceExpiresOn: values.licenceExpiresOn,
        medicalClearanceExpiresOn: values.medicalClearanceExpiresOn || null,
        responsibleUnit: values.responsibleUnit.trim(),
        targetLifecycleStatus: values.targetLifecycleStatus || null,
        lifecycleReason: values.lifecycleReason.trim() || null,
        expectedVersion: driver.version,
      });
      onSaved();
      onClose();
    },
  });

  return (
    <FormDialog
      open={open}
      title={`Update ${driver.displayName}`}
      description="Licence details and lifecycle status both feed the eligibility assessment."
      submitLabel="Save changes"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      {driver.licenceNumberMasked && (
        <Alert severity="warning">
          The licence number is masked for your role. Submitting will overwrite the stored value —
          only fill this in if you hold the real number.
        </Alert>
      )}
      <Box sx={twoColumn}>
        <TextInput
          label="Display name"
          required
          value={form.values.displayName}
          onChange={(value) => form.setValue('displayName', value)}
          {...form.fieldProps('displayName')}
        />
        <TextInput
          label="Licence number"
          required
          value={form.values.licenceNumber}
          onChange={(value) => form.setValue('licenceNumber', value)}
          {...form.fieldProps('licenceNumber')}
        />
        <EnumSelect
          label="Licence class"
          required
          value={form.values.licenceClass}
          options={LICENCE_CLASSES}
          onChange={(value) => form.setValue('licenceClass', value)}
          renderOptionLabel={(option) => `Class ${option}`}
          {...form.fieldProps('licenceClass')}
        />
        <DateInput
          label="Licence expires on"
          required
          value={form.values.licenceExpiresOn}
          onChange={(value) => form.setValue('licenceExpiresOn', value)}
          {...form.fieldProps('licenceExpiresOn')}
        />
        <DateInput
          label="Medical clearance expires on"
          value={form.values.medicalClearanceExpiresOn}
          onChange={(value) => form.setValue('medicalClearanceExpiresOn', value)}
          {...form.fieldProps('medicalClearanceExpiresOn')}
        />
        <TextInput
          label="Responsible unit"
          required
          value={form.values.responsibleUnit}
          onChange={(value) => form.setValue('responsibleUnit', value)}
          {...form.fieldProps('responsibleUnit')}
        />
        <EnumSelect
          label="Change lifecycle status"
          value={form.values.targetLifecycleStatus}
          options={DRIVER_LIFECYCLE_STATUSES.filter((status) => status !== driver.lifecycleStatus)}
          onChange={(value) => form.setValue('targetLifecycleStatus', value)}
          allowEmpty
          emptyLabel="Leave unchanged"
          {...form.fieldProps('targetLifecycleStatus')}
        />
        <TextInput
          label="Lifecycle reason"
          value={form.values.lifecycleReason}
          onChange={(value) => form.setValue('lifecycleReason', value)}
          helperText="Quoted back to the dispatcher in the eligibility assessment."
          {...form.fieldProps('lifecycleReason')}
        />
      </Box>
    </FormDialog>
  );
};
