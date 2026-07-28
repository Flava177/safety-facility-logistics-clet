import { Alert, Box, Stack, Typography } from '@mui/material';
import { VehicleResponse } from 'modules/fleet/api/dto';
import {
  COMPLIANCE_DOCUMENT_TYPES,
  ComplianceDocumentType,
  MANDATORY_COMPLIANCE_DOCUMENT_TYPES,
  OPERATING_MODES,
  RETENTION_CLASSES,
  RetentionClass,
  SERVICE_OUTCOMES,
  SERVICE_TYPES,
  ServiceOutcome,
  ServiceType,
  VEHICLE_CATEGORIES,
  VEHICLE_LIFECYCLE_STATUSES,
  VehicleCategory,
  VehicleLifecycleStatus,
  humanise,
} from 'modules/fleet/api/enums';
import { vehiclesApi } from 'modules/fleet/api/fleetApi';
import FormDialog from 'shared/components/FormDialog';
import { DateInput, EnumSelect, NumberInput, TextInput } from 'shared/components/fields';
import { todayIsoDate } from 'shared/components/format';
import { useFleetForm } from 'shared/validation/useFleetForm';
import {
  compose,
  maxLength,
  nonNegativeInteger,
  numberBetween,
  odometerNotBelow,
  required,
} from 'shared/validation/validators';

interface BaseDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

const twoColumn = {
  display: 'grid',
  gap: 2,
  gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
} as const;

/* ---------------------------------------------------------------------------------------------
 * Register a vehicle — POST /api/v1/fleet/vehicles
 * ------------------------------------------------------------------------------------------- */

interface RegisterVehicleDialogProps extends BaseDialogProps {
  defaultSiteCode: string;
}

export const RegisterVehicleDialog = ({
  open,
  onClose,
  onSaved,
  defaultSiteCode,
}: RegisterVehicleDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      registrationNumber: '',
      vin: '',
      make: '',
      model: '',
      manufactureYear: String(new Date().getFullYear()),
      category: '' as VehicleCategory | '',
      capacity: '5',
      siteCode: defaultSiteCode,
      responsibleUnit: '',
      operationalOwner: '',
      acquisitionReference: '',
      initialOdometer: '0',
      emergencyOnly: 'false',
    },
    schema: {
      registrationNumber: compose(
        required('Registration number'),
        maxLength('Registration number', 40),
      ),
      vin: maxLength('VIN', 40),
      make: compose(required('Make'), maxLength('Make', 80)),
      model: compose(required('Model'), maxLength('Model', 80)),
      manufactureYear: compose(
        required('Manufacture year'),
        numberBetween('Manufacture year', 1950, 2100),
      ),
      category: required('Category'),
      capacity: compose(required('Capacity'), numberBetween('Capacity', 1, 200)),
      siteCode: compose(required('Site code'), maxLength('Site code', 40)),
      responsibleUnit: compose(required('Responsible unit'), maxLength('Responsible unit', 160)),
      operationalOwner: compose(required('Operational owner'), maxLength('Operational owner', 160)),
      acquisitionReference: maxLength('Acquisition reference', 120),
      initialOdometer: compose(
        required('Initial odometer'),
        nonNegativeInteger('Initial odometer'),
      ),
    },
    onSubmit: async (values) => {
      await vehiclesApi.register({
        registrationNumber: values.registrationNumber.trim(),
        vin: values.vin.trim() || null,
        make: values.make.trim(),
        model: values.model.trim(),
        manufactureYear: Number(values.manufactureYear),
        category: values.category as VehicleCategory,
        capacity: Number(values.capacity),
        siteCode: values.siteCode.trim().toUpperCase(),
        responsibleUnit: values.responsibleUnit.trim(),
        operationalOwner: values.operationalOwner.trim(),
        acquisitionReference: values.acquisitionReference.trim() || null,
        initialOdometer: Number(values.initialOdometer),
        emergencyOnly: values.emergencyOnly === 'true',
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Register a vehicle"
      description="Adds a vehicle to the register for this site. Registration number must be unique for the site."
      submitLabel="Register vehicle"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Box sx={twoColumn}>
        <TextInput
          label="Registration number"
          required
          value={form.values.registrationNumber}
          onChange={(value) => form.setValue('registrationNumber', value)}
          {...form.fieldProps('registrationNumber')}
        />
        <TextInput
          label="VIN"
          value={form.values.vin}
          onChange={(value) => form.setValue('vin', value)}
          {...form.fieldProps('vin')}
        />
        <TextInput
          label="Make"
          required
          value={form.values.make}
          onChange={(value) => form.setValue('make', value)}
          {...form.fieldProps('make')}
        />
        <TextInput
          label="Model"
          required
          value={form.values.model}
          onChange={(value) => form.setValue('model', value)}
          {...form.fieldProps('model')}
        />
        <NumberInput
          label="Manufacture year"
          required
          min={1950}
          value={form.values.manufactureYear}
          onChange={(value) => form.setValue('manufactureYear', value)}
          {...form.fieldProps('manufactureYear')}
        />
        <EnumSelect
          label="Category"
          required
          value={form.values.category}
          options={VEHICLE_CATEGORIES}
          onChange={(value) => form.setValue('category', value)}
          {...form.fieldProps('category')}
        />
        <NumberInput
          label="Capacity"
          required
          min={1}
          value={form.values.capacity}
          onChange={(value) => form.setValue('capacity', value)}
          {...form.fieldProps('capacity')}
        />
        <NumberInput
          label="Initial odometer (km)"
          required
          value={form.values.initialOdometer}
          onChange={(value) => form.setValue('initialOdometer', value)}
          {...form.fieldProps('initialOdometer')}
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
        <TextInput
          label="Operational owner"
          required
          value={form.values.operationalOwner}
          onChange={(value) => form.setValue('operationalOwner', value)}
          {...form.fieldProps('operationalOwner')}
        />
        <TextInput
          label="Acquisition reference"
          value={form.values.acquisitionReference}
          onChange={(value) => form.setValue('acquisitionReference', value)}
          {...form.fieldProps('acquisitionReference')}
        />
        <EnumSelect
          label="Emergency use only"
          value={form.values.emergencyOnly}
          options={['false', 'true'] as const}
          onChange={(value) => form.setValue('emergencyOnly', value || 'false')}
          renderOptionLabel={(option) => (option === 'true' ? 'Yes' : 'No')}
        />
      </Box>
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Lifecycle transition — PATCH /api/v1/fleet/vehicles/{id}/lifecycle
 * ------------------------------------------------------------------------------------------- */

interface LifecycleDialogProps extends BaseDialogProps {
  vehicle: VehicleResponse;
}

export const ChangeVehicleLifecycleDialog = ({
  open,
  onClose,
  onSaved,
  vehicle,
}: LifecycleDialogProps) => {
  const form = useFleetForm({
    initialValues: { targetStatus: '' as VehicleLifecycleStatus | '', reason: '' },
    schema: {
      targetStatus: required('Target status'),
      reason: compose(required('Reason'), maxLength('Reason', 1000)),
    },
    onSubmit: async (values) => {
      await vehiclesApi.changeLifecycle(vehicle.id, {
        targetStatus: values.targetStatus as VehicleLifecycleStatus,
        reason: values.reason.trim(),
        expectedVersion: vehicle.version,
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Change vehicle lifecycle status"
      description={`${vehicle.registrationNumber} is currently ${humanise(vehicle.lifecycleStatus)}. The service rejects transitions that are not permitted from this status.`}
      submitLabel="Apply transition"
      submitting={form.submitting}
      formError={form.formError}
      destructive
      onClose={onClose}
      onSubmit={form.submit}
    >
      <EnumSelect
        label="Target status"
        required
        value={form.values.targetStatus}
        options={VEHICLE_LIFECYCLE_STATUSES.filter((status) => status !== vehicle.lifecycleStatus)}
        onChange={(value) => form.setValue('targetStatus', value)}
        {...form.fieldProps('targetStatus')}
      />
      <TextInput
        label="Reason"
        required
        multiline
        minRows={3}
        value={form.values.reason}
        onChange={(value) => form.setValue('reason', value)}
        {...form.fieldProps('reason')}
      />
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Compliance document — POST /api/v1/fleet/vehicles/{id}/compliance-documents
 * ------------------------------------------------------------------------------------------- */

interface ComplianceDialogProps extends BaseDialogProps {
  vehicleId: string;
}

export const RegisterComplianceDocumentDialog = ({
  open,
  onClose,
  onSaved,
  vehicleId,
}: ComplianceDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      documentType: '' as ComplianceDocumentType | '',
      documentReference: '',
      issuingAuthority: '',
      issuedOn: todayIsoDate(),
      expiresOn: '',
      evidenceId: '',
      retentionClass: 'COMPLIANCE' as RetentionClass,
    },
    schema: {
      documentType: required('Document type'),
      documentReference: compose(
        required('Document reference'),
        maxLength('Document reference', 160),
      ),
      issuingAuthority: compose(required('Issuing authority'), maxLength('Issuing authority', 160)),
      issuedOn: required('Issued on'),
      expiresOn: required('Expires on'),
      retentionClass: required('Retention class'),
    },
    crossFieldValidate: (values) =>
      values.issuedOn && values.expiresOn && values.expiresOn <= values.issuedOn
        ? { expiresOn: 'Expiry must be after the issue date.' }
        : {},
    onSubmit: async (values) => {
      await vehiclesApi.registerComplianceDocument(vehicleId, {
        documentType: values.documentType as ComplianceDocumentType,
        documentReference: values.documentReference.trim(),
        issuingAuthority: values.issuingAuthority.trim(),
        issuedOn: values.issuedOn,
        expiresOn: values.expiresOn,
        evidenceId: values.evidenceId.trim() || null,
        retentionClass: values.retentionClass,
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  const isMandatory =
    form.values.documentType !== '' &&
    MANDATORY_COMPLIANCE_DOCUMENT_TYPES.includes(form.values.documentType);

  return (
    <FormDialog
      open={open}
      title="Register a compliance document"
      description="A retention class is mandatory on every fleet compliance record."
      submitLabel="Register document"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Box sx={twoColumn}>
        <EnumSelect
          label="Document type"
          required
          value={form.values.documentType}
          options={COMPLIANCE_DOCUMENT_TYPES}
          onChange={(value) => form.setValue('documentType', value)}
          {...form.fieldProps('documentType')}
        />
        <TextInput
          label="Document reference"
          required
          value={form.values.documentReference}
          onChange={(value) => form.setValue('documentReference', value)}
          {...form.fieldProps('documentReference')}
        />
        <TextInput
          label="Issuing authority"
          required
          value={form.values.issuingAuthority}
          onChange={(value) => form.setValue('issuingAuthority', value)}
          {...form.fieldProps('issuingAuthority')}
        />
        <EnumSelect
          label="Retention class"
          required
          value={form.values.retentionClass}
          options={RETENTION_CLASSES}
          onChange={(value) =>
            form.setValue('retentionClass', (value || 'COMPLIANCE') as RetentionClass)
          }
          {...form.fieldProps('retentionClass')}
        />
        <DateInput
          label="Issued on"
          required
          value={form.values.issuedOn}
          onChange={(value) => form.setValue('issuedOn', value)}
          {...form.fieldProps('issuedOn')}
        />
        <DateInput
          label="Expires on"
          required
          value={form.values.expiresOn}
          onChange={(value) => form.setValue('expiresOn', value)}
          {...form.fieldProps('expiresOn')}
        />
        <TextInput
          label="Evidence reference ID"
          value={form.values.evidenceId}
          onChange={(value) => form.setValue('evidenceId', value)}
          helperText="Optional. Register the evidence first under Evidence & audit."
          {...form.fieldProps('evidenceId')}
        />
      </Box>

      {isMandatory && (
        <Alert severity="info">
          {humanise(form.values.documentType)} is a mandatory document — while it is missing or
          expired the vehicle carries a blocking readiness blocker.
        </Alert>
      )}
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Service record — POST /api/v1/fleet/vehicles/{id}/service-records
 * ------------------------------------------------------------------------------------------- */

interface ServiceDialogProps extends BaseDialogProps {
  vehicleId: string;
  currentOdometer: number;
}

export const RecordServiceDialog = ({
  open,
  onClose,
  onSaved,
  vehicleId,
  currentOdometer,
}: ServiceDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      serviceType: '' as ServiceType | '',
      performedOn: todayIsoDate(),
      odometerAtService: String(currentOdometer),
      nextDueOn: '',
      nextDueOdometer: '',
      providerReference: '',
      workSummary: '',
      outcome: '' as ServiceOutcome | '',
      evidenceId: '',
    },
    schema: {
      serviceType: required('Service type'),
      performedOn: required('Performed on'),
      odometerAtService: compose(
        required('Odometer at service'),
        nonNegativeInteger('Odometer at service'),
      ),
      nextDueOdometer: nonNegativeInteger('Next due odometer'),
      providerReference: maxLength('Provider reference', 160),
      workSummary: compose(required('Work summary'), maxLength('Work summary', 2000)),
      outcome: required('Outcome'),
    },
    onSubmit: async (values) => {
      await vehiclesApi.recordService(vehicleId, {
        serviceType: values.serviceType as ServiceType,
        performedOn: values.performedOn,
        odometerAtService: Number(values.odometerAtService),
        nextDueOn: values.nextDueOn || null,
        nextDueOdometer: values.nextDueOdometer ? Number(values.nextDueOdometer) : null,
        providerReference: values.providerReference.trim() || null,
        workSummary: values.workSummary.trim(),
        outcome: values.outcome as ServiceOutcome,
        evidenceId: values.evidenceId.trim() || null,
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  const regression = odometerNotBelow(form.values.odometerAtService, currentOdometer);

  return (
    <FormDialog
      open={open}
      title="Record a service event"
      description="Service history drives the vehicle's service status and its readiness blockers."
      submitLabel="Record service"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Box sx={twoColumn}>
        <EnumSelect
          label="Service type"
          required
          value={form.values.serviceType}
          options={SERVICE_TYPES}
          onChange={(value) => form.setValue('serviceType', value)}
          {...form.fieldProps('serviceType')}
        />
        <EnumSelect
          label="Outcome"
          required
          value={form.values.outcome}
          options={SERVICE_OUTCOMES}
          onChange={(value) => form.setValue('outcome', value)}
          {...form.fieldProps('outcome')}
        />
        <DateInput
          label="Performed on"
          required
          value={form.values.performedOn}
          onChange={(value) => form.setValue('performedOn', value)}
          {...form.fieldProps('performedOn')}
        />
        <NumberInput
          label="Odometer at service (km)"
          required
          value={form.values.odometerAtService}
          onChange={(value) => form.setValue('odometerAtService', value)}
          {...form.fieldProps('odometerAtService')}
        />
        <DateInput
          label="Next due on"
          value={form.values.nextDueOn}
          onChange={(value) => form.setValue('nextDueOn', value)}
          {...form.fieldProps('nextDueOn')}
        />
        <NumberInput
          label="Next due odometer (km)"
          value={form.values.nextDueOdometer}
          onChange={(value) => form.setValue('nextDueOdometer', value)}
          {...form.fieldProps('nextDueOdometer')}
        />
        <TextInput
          label="Provider reference"
          value={form.values.providerReference}
          onChange={(value) => form.setValue('providerReference', value)}
          {...form.fieldProps('providerReference')}
        />
        <TextInput
          label="Evidence reference ID"
          value={form.values.evidenceId}
          onChange={(value) => form.setValue('evidenceId', value)}
          {...form.fieldProps('evidenceId')}
        />
      </Box>
      <TextInput
        label="Work summary"
        required
        multiline
        minRows={3}
        value={form.values.workSummary}
        onChange={(value) => form.setValue('workSummary', value)}
        {...form.fieldProps('workSummary')}
      />
      {regression && (
        <Alert severity="warning">
          {regression} The service will reject this with FLEET_ODOMETER_REGRESSION. Use an
          authorised odometer correction instead.
        </Alert>
      )}
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Odometer correction — POST /api/v1/fleet/vehicles/{id}/odometer-corrections
 * ------------------------------------------------------------------------------------------- */

interface OdometerDialogProps extends BaseDialogProps {
  vehicle: VehicleResponse;
}

export const CorrectOdometerDialog = ({ open, onClose, onSaved, vehicle }: OdometerDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      correctedReading: String(vehicle.odometerValue),
      reason: '',
      evidenceId: '',
    },
    schema: {
      correctedReading: compose(
        required('Corrected reading'),
        nonNegativeInteger('Corrected reading'),
      ),
      reason: compose(required('Reason'), maxLength('Reason', 1000)),
      evidenceId: required('Evidence reference ID'),
    },
    onSubmit: async (values) => {
      await vehiclesApi.correctOdometer(vehicle.id, {
        correctedReading: Number(values.correctedReading),
        reason: values.reason.trim(),
        evidenceId: values.evidenceId.trim(),
        expectedVersion: vehicle.version,
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Correct the odometer"
      description="The one operation allowed to move a reading backwards. Reason and evidence are both mandatory."
      submitLabel="Apply correction"
      submitting={form.submitting}
      formError={form.formError}
      destructive
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Stack spacing={0.5}>
        <Typography variant="caption" color="text.secondary">
          Current reading
        </Typography>
        <Typography variant="body2" fontWeight={700}>
          {vehicle.odometerValue.toLocaleString()} km · source {humanise(vehicle.odometerSource)}
        </Typography>
      </Stack>
      <NumberInput
        label="Corrected reading (km)"
        required
        value={form.values.correctedReading}
        onChange={(value) => form.setValue('correctedReading', value)}
        {...form.fieldProps('correctedReading')}
      />
      <TextInput
        label="Reason"
        required
        multiline
        minRows={3}
        value={form.values.reason}
        onChange={(value) => form.setValue('reason', value)}
        {...form.fieldProps('reason')}
      />
      <TextInput
        label="Evidence reference ID"
        required
        value={form.values.evidenceId}
        onChange={(value) => form.setValue('evidenceId', value)}
        helperText="Register the supporting evidence under Evidence & audit first."
        {...form.fieldProps('evidenceId')}
      />
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Edit vehicle — PATCH /api/v1/fleet/vehicles/{id}
 * ------------------------------------------------------------------------------------------- */

interface EditVehicleDialogProps extends BaseDialogProps {
  vehicle: VehicleResponse;
}

export const EditVehicleDialog = ({ open, onClose, onSaved, vehicle }: EditVehicleDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      vin: vehicle.vinMasked ? '' : (vehicle.vin ?? ''),
      make: vehicle.make,
      model: vehicle.model,
      manufactureYear: String(vehicle.manufactureYear),
      category: vehicle.category as VehicleCategory | '',
      capacity: String(vehicle.capacity),
      responsibleUnit: vehicle.responsibleUnit,
      operationalOwner: vehicle.operationalOwner,
      acquisitionReference: vehicle.acquisitionReference ?? '',
      emergencyOnly: vehicle.emergencyOnly ? 'true' : 'false',
    },
    schema: {
      vin: maxLength('VIN', 40),
      make: compose(required('Make'), maxLength('Make', 80)),
      model: compose(required('Model'), maxLength('Model', 80)),
      manufactureYear: compose(
        required('Manufacture year'),
        numberBetween('Manufacture year', 1950, 2100),
      ),
      category: required('Category'),
      capacity: compose(required('Capacity'), numberBetween('Capacity', 1, 200)),
      responsibleUnit: compose(required('Responsible unit'), maxLength('Responsible unit', 160)),
      operationalOwner: compose(required('Operational owner'), maxLength('Operational owner', 160)),
      acquisitionReference: maxLength('Acquisition reference', 120),
    },
    onSubmit: async (values) => {
      await vehiclesApi.update(vehicle.id, {
        vin: values.vin.trim() || null,
        make: values.make.trim(),
        model: values.model.trim(),
        manufactureYear: Number(values.manufactureYear),
        category: values.category as VehicleCategory,
        capacity: Number(values.capacity),
        responsibleUnit: values.responsibleUnit.trim(),
        operationalOwner: values.operationalOwner.trim(),
        acquisitionReference: values.acquisitionReference.trim() || null,
        emergencyOnly: values.emergencyOnly === 'true',
        expectedVersion: vehicle.version,
      });
      onSaved();
      onClose();
    },
  });

  return (
    <FormDialog
      open={open}
      title={`Edit ${vehicle.registrationNumber}`}
      description="Submitting sends the version you loaded, so a concurrent edit is refused rather than silently overwritten."
      submitLabel="Save changes"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      {vehicle.vinMasked && (
        <Alert severity="warning">
          The VIN is masked for your role. Leaving this field blank clears the stored VIN — only
          fill it in if you hold the real value.
        </Alert>
      )}
      <Box sx={twoColumn}>
        <TextInput
          label="VIN"
          value={form.values.vin}
          onChange={(value) => form.setValue('vin', value)}
          {...form.fieldProps('vin')}
        />
        <EnumSelect
          label="Category"
          required
          value={form.values.category}
          options={VEHICLE_CATEGORIES}
          onChange={(value) => form.setValue('category', value)}
          {...form.fieldProps('category')}
        />
        <TextInput
          label="Make"
          required
          value={form.values.make}
          onChange={(value) => form.setValue('make', value)}
          {...form.fieldProps('make')}
        />
        <TextInput
          label="Model"
          required
          value={form.values.model}
          onChange={(value) => form.setValue('model', value)}
          {...form.fieldProps('model')}
        />
        <NumberInput
          label="Manufacture year"
          required
          min={1950}
          value={form.values.manufactureYear}
          onChange={(value) => form.setValue('manufactureYear', value)}
          {...form.fieldProps('manufactureYear')}
        />
        <NumberInput
          label="Capacity"
          required
          min={1}
          value={form.values.capacity}
          onChange={(value) => form.setValue('capacity', value)}
          {...form.fieldProps('capacity')}
        />
        <TextInput
          label="Responsible unit"
          required
          value={form.values.responsibleUnit}
          onChange={(value) => form.setValue('responsibleUnit', value)}
          {...form.fieldProps('responsibleUnit')}
        />
        <TextInput
          label="Operational owner"
          required
          value={form.values.operationalOwner}
          onChange={(value) => form.setValue('operationalOwner', value)}
          {...form.fieldProps('operationalOwner')}
        />
        <TextInput
          label="Acquisition reference"
          value={form.values.acquisitionReference}
          onChange={(value) => form.setValue('acquisitionReference', value)}
          {...form.fieldProps('acquisitionReference')}
        />
        <EnumSelect
          label="Emergency use only"
          value={form.values.emergencyOnly}
          options={['false', 'true'] as const}
          onChange={(value) => form.setValue('emergencyOnly', value || 'false')}
          renderOptionLabel={(option) => (option === 'true' ? 'Yes' : 'No')}
        />
      </Box>
      <Typography variant="caption" color="text.secondary">
        Allowed operating modes are managed by the service; the current set is{' '}
        {(vehicle.allowedOperatingModes ?? OPERATING_MODES).map(humanise).join(', ')}.
      </Typography>
    </FormDialog>
  );
};
