import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Divider,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { TripResponse } from 'modules/fleet/api/dto';
import {
  DEFECT_SEVERITIES,
  DefectSeverity,
  INSPECTION_TYPES,
  InspectionType,
  OPERATING_MODES,
  OperatingMode,
  humanise,
} from 'modules/fleet/api/enums';
import { driversApi, tripsApi, vehiclesApi } from 'modules/fleet/api/fleetApi';
import BlockerList from 'shared/components/BlockerList';
import FormDialog from 'shared/components/FormDialog';
import { DateInput, EnumSelect, NumberInput, TextInput } from 'shared/components/fields';
import { formatNumber, fromLocalInputValue, nowLocalInputValue } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { useFleetForm } from 'shared/validation/useFleetForm';
import {
  compose,
  dateRangeError,
  maxLength,
  nonNegativeInteger,
  odometerNotBelow,
  required,
} from 'shared/validation/validators';
import IconifyIcon from 'components/base/IconifyIcon';

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

/**
 * Vehicle and driver pickers.
 *
 * Loaded from the register rather than typed as raw UUIDs — an operator picks a registration
 * number, and the eligibility of the pick is then previewed against the real policy.
 */
const useAssignableOptions = (siteCode: string | undefined, enabled: boolean) => {
  const vehicles = useApiQuery(
    (signal) =>
      enabled
        ? vehiclesApi.search(
            { siteCode, status: 'ACTIVE', size: 200, sort: 'registrationNumber' },
            signal,
          )
        : Promise.resolve(undefined),
    [siteCode, enabled],
  );
  const drivers = useApiQuery(
    (signal) =>
      enabled
        ? driversApi.search({ siteCode, status: 'ACTIVE', size: 200, sort: 'displayName' }, signal)
        : Promise.resolve(undefined),
    [siteCode, enabled],
  );
  return { vehicles, drivers };
};

/**
 * Readiness preview for a candidate vehicle/driver pair.
 *
 * Rendered before submission so an operator sees why an assignment would be refused instead of
 * discovering it from a 422.
 */
const AssignmentPreview = ({
  vehicleId,
  driverId,
  from,
  to,
  operatingMode,
}: {
  vehicleId: string;
  driverId?: string;
  from?: string;
  to?: string;
  operatingMode?: OperatingMode;
}) => {
  const preview = useApiQuery(
    (signal) =>
      vehicleId
        ? tripsApi.assignmentPreview(
            { vehicleId, driverId: driverId || undefined, from, to, operatingMode },
            signal,
          )
        : Promise.resolve(undefined),
    [vehicleId, driverId, from, to, operatingMode],
  );

  if (!vehicleId) {
    return (
      <Alert severity="info" variant="outlined">
        Choose a vehicle to see readiness blockers before submitting.
      </Alert>
    );
  }

  if (preview.loading) {
    return (
      <Typography variant="body2" color="text.secondary">
        Checking readiness…
      </Typography>
    );
  }

  if (preview.error) {
    return <Alert severity="warning">{preview.error.message}</Alert>;
  }

  if (!preview.data) {
    return null;
  }

  return <BlockerList blockers={preview.data.blockers} dense />;
};

/* ---------------------------------------------------------------------------------------------
 * Create a trip — POST /api/v1/fleet/trips
 * ------------------------------------------------------------------------------------------- */

export const CreateTripDialog = ({
  open,
  onClose,
  onSaved,
  defaultSiteCode,
}: BaseProps & { defaultSiteCode: string }) => {
  const form = useFleetForm({
    initialValues: {
      siteCode: defaultSiteCode,
      purpose: '',
      origin: '',
      destination: '',
      operatingMode: 'ROUTINE' as OperatingMode,
      plannedStart: nowLocalInputValue(1),
      plannedEnd: nowLocalInputValue(4),
      vehicleId: '',
      driverId: '',
    },
    schema: {
      siteCode: compose(required('Site code'), maxLength('Site code', 40)),
      purpose: compose(required('Purpose'), maxLength('Purpose', 500)),
      origin: compose(required('Origin'), maxLength('Origin', 200)),
      destination: compose(required('Destination'), maxLength('Destination', 200)),
      operatingMode: required('Operating mode'),
      plannedStart: required('Planned start'),
      plannedEnd: required('Planned end'),
    },
    crossFieldValidate: (values) => {
      const rangeError = dateRangeError(
        values.plannedStart,
        values.plannedEnd,
        'Planned start',
        'Planned end',
      );
      return rangeError ? { plannedEnd: rangeError } : {};
    },
    onSubmit: async (values) => {
      await tripsApi.create({
        siteCode: values.siteCode.trim().toUpperCase(),
        purpose: values.purpose.trim(),
        origin: values.origin.trim(),
        destination: values.destination.trim(),
        operatingMode: values.operatingMode,
        plannedStart: fromLocalInputValue(values.plannedStart),
        plannedEnd: fromLocalInputValue(values.plannedEnd),
        vehicleId: values.vehicleId || null,
        driverId: values.driverId || null,
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  const { vehicles, drivers } = useAssignableOptions(form.values.siteCode || undefined, open);

  return (
    <FormDialog
      open={open}
      title="Plan a trip"
      description="A trip may be planned first and assigned later. Supplying both a vehicle and a driver assigns it immediately."
      submitLabel="Create trip"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Box sx={twoColumn}>
        <TextInput
          label="Site code"
          required
          value={form.values.siteCode}
          onChange={(value) => form.setValue('siteCode', value)}
          {...form.fieldProps('siteCode')}
        />
        <EnumSelect
          label="Operating mode"
          required
          value={form.values.operatingMode}
          options={OPERATING_MODES}
          onChange={(value) =>
            form.setValue('operatingMode', (value || 'ROUTINE') as OperatingMode)
          }
          {...form.fieldProps('operatingMode')}
        />
        <TextInput
          label="Origin"
          required
          value={form.values.origin}
          onChange={(value) => form.setValue('origin', value)}
          {...form.fieldProps('origin')}
        />
        <TextInput
          label="Destination"
          required
          value={form.values.destination}
          onChange={(value) => form.setValue('destination', value)}
          {...form.fieldProps('destination')}
        />
        <DateInput
          label="Planned start"
          required
          withTime
          value={form.values.plannedStart}
          onChange={(value) => form.setValue('plannedStart', value)}
          {...form.fieldProps('plannedStart')}
        />
        <DateInput
          label="Planned end"
          required
          withTime
          value={form.values.plannedEnd}
          onChange={(value) => form.setValue('plannedEnd', value)}
          {...form.fieldProps('plannedEnd')}
        />
      </Box>

      <TextInput
        label="Purpose"
        required
        multiline
        minRows={2}
        value={form.values.purpose}
        onChange={(value) => form.setValue('purpose', value)}
        {...form.fieldProps('purpose')}
      />

      <Divider />
      <Typography variant="subtitle2" fontWeight={700}>
        Assignment (optional)
      </Typography>

      <Box sx={twoColumn}>
        <TextField
          select
          fullWidth
          size="small"
          label="Vehicle"
          value={form.values.vehicleId}
          onChange={(event) => form.setValue('vehicleId', event.target.value)}
          helperText={vehicles.error ? vehicles.error.message : 'Active vehicles for this site'}
          error={Boolean(vehicles.error)}
        >
          <MenuItem value="">
            <em>Assign later</em>
          </MenuItem>
          {(vehicles.data?.content ?? []).map((vehicle) => (
            <MenuItem key={vehicle.id} value={vehicle.id}>
              {vehicle.registrationNumber} — {vehicle.make} {vehicle.model} (
              {humanise(vehicle.availabilityStatus)})
            </MenuItem>
          ))}
        </TextField>

        <TextField
          select
          fullWidth
          size="small"
          label="Driver"
          value={form.values.driverId}
          onChange={(event) => form.setValue('driverId', event.target.value)}
          helperText={drivers.error ? drivers.error.message : 'Active drivers for this site'}
          error={Boolean(drivers.error)}
        >
          <MenuItem value="">
            <em>Assign later</em>
          </MenuItem>
          {(drivers.data?.content ?? []).map((driver) => (
            <MenuItem key={driver.id} value={driver.id}>
              {driver.displayName} — class {driver.licenceClass} (
              {humanise(driver.eligibilityStatus)})
            </MenuItem>
          ))}
        </TextField>
      </Box>

      {form.values.vehicleId && (
        <AssignmentPreview
          vehicleId={form.values.vehicleId}
          driverId={form.values.driverId}
          from={
            form.values.plannedStart ? fromLocalInputValue(form.values.plannedStart) : undefined
          }
          to={form.values.plannedEnd ? fromLocalInputValue(form.values.plannedEnd) : undefined}
          operatingMode={form.values.operatingMode}
        />
      )}
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Assign or reassign — PATCH /api/v1/fleet/trips/{id}/assignment
 * ------------------------------------------------------------------------------------------- */

export const AssignTripDialog = ({
  open,
  onClose,
  onSaved,
  trip,
}: BaseProps & { trip: TripResponse }) => {
  const form = useFleetForm({
    initialValues: {
      vehicleId: trip.vehicleId ?? '',
      driverId: trip.driverId ?? '',
      reason: '',
    },
    schema: {
      vehicleId: required('Vehicle'),
      driverId: required('Driver'),
      reason: maxLength('Reason', 1000),
    },
    onSubmit: async (values) => {
      await tripsApi.assign(trip.id, {
        vehicleId: values.vehicleId,
        driverId: values.driverId,
        reason: values.reason.trim() || null,
        expectedVersion: trip.version,
      });
      onSaved();
      onClose();
    },
  });

  const { vehicles, drivers } = useAssignableOptions(trip.siteCode, open);

  return (
    <FormDialog
      open={open}
      title={trip.vehicleId ? 'Reassign trip' : 'Assign trip'}
      description={`${trip.tripNumber} · ${trip.origin} → ${trip.destination}. Blockers below are what the service will apply.`}
      submitLabel={trip.vehicleId ? 'Reassign' : 'Assign'}
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Box sx={twoColumn}>
        <TextField
          select
          fullWidth
          size="small"
          required
          label="Vehicle"
          value={form.values.vehicleId}
          onChange={(event) => form.setValue('vehicleId', event.target.value)}
          error={form.fieldProps('vehicleId').error}
          helperText={form.fieldProps('vehicleId').helperText}
          onBlur={form.fieldProps('vehicleId').onBlur}
        >
          {(vehicles.data?.content ?? []).map((vehicle) => (
            <MenuItem key={vehicle.id} value={vehicle.id}>
              {vehicle.registrationNumber} — {humanise(vehicle.availabilityStatus)}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          select
          fullWidth
          size="small"
          required
          label="Driver"
          value={form.values.driverId}
          onChange={(event) => form.setValue('driverId', event.target.value)}
          error={form.fieldProps('driverId').error}
          helperText={form.fieldProps('driverId').helperText}
          onBlur={form.fieldProps('driverId').onBlur}
        >
          {(drivers.data?.content ?? []).map((driver) => (
            <MenuItem key={driver.id} value={driver.id}>
              {driver.displayName} — {humanise(driver.eligibilityStatus)}
            </MenuItem>
          ))}
        </TextField>
      </Box>

      <TextInput
        label="Reason"
        value={form.values.reason}
        onChange={(value) => form.setValue('reason', value)}
        helperText="Recorded on the trip's transition history."
        {...form.fieldProps('reason')}
      />

      {form.values.vehicleId && (
        <AssignmentPreview
          vehicleId={form.values.vehicleId}
          driverId={form.values.driverId}
          from={trip.plannedStart}
          to={trip.plannedEnd}
          operatingMode={trip.operatingMode}
        />
      )}
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Start — PATCH /api/v1/fleet/trips/{id}/start
 * ------------------------------------------------------------------------------------------- */

export const StartTripDialog = ({
  open,
  onClose,
  onSaved,
  trip,
  vehicleOdometer,
}: BaseProps & { trip: TripResponse; vehicleOdometer?: number }) => {
  const form = useFleetForm({
    initialValues: { startOdometer: vehicleOdometer ? String(vehicleOdometer) : '' },
    schema: {
      startOdometer: compose(required('Start odometer'), nonNegativeInteger('Start odometer')),
    },
    onSubmit: async (values) => {
      await tripsApi.start(trip.id, {
        startOdometer: Number(values.startOdometer),
        expectedVersion: trip.version,
      });
      onSaved();
      onClose();
    },
  });

  const regression = odometerNotBelow(form.values.startOdometer, vehicleOdometer);

  return (
    <FormDialog
      open={open}
      title="Start trip"
      description={`${trip.tripNumber}. The service gates this on a valid pre-trip inspection.`}
      submitLabel="Start trip"
      submitting={form.submitting}
      formError={form.formError}
      onClose={onClose}
      onSubmit={form.submit}
    >
      {vehicleOdometer !== undefined && (
        <Typography variant="body2" color="text.secondary">
          Last recorded vehicle odometer: {formatNumber(vehicleOdometer)} km
        </Typography>
      )}
      <NumberInput
        label="Start odometer (km)"
        required
        value={form.values.startOdometer}
        onChange={(value) => form.setValue('startOdometer', value)}
        {...form.fieldProps('startOdometer')}
      />
      {regression && <Alert severity="warning">{regression}</Alert>}
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Close — PATCH /api/v1/fleet/trips/{id}/closure
 * ------------------------------------------------------------------------------------------- */

export const CloseTripDialog = ({
  open,
  onClose,
  onSaved,
  trip,
}: BaseProps & { trip: TripResponse }) => {
  const form = useFleetForm({
    initialValues: {
      closureReason: '',
      closureEvidenceId: '',
      endOdometer: trip.startOdometer ? String(trip.startOdometer) : '',
    },
    schema: {
      closureReason: compose(required('Closure reason'), maxLength('Closure reason', 1000)),
      closureEvidenceId: required('Closure evidence'),
      endOdometer: compose(required('End odometer'), nonNegativeInteger('End odometer')),
    },
    crossFieldValidate: (values) => {
      const message = odometerNotBelow(
        values.endOdometer,
        trip.startOdometer ?? undefined,
        'the start odometer',
      );
      return message ? { endOdometer: message } : {};
    },
    onSubmit: async (values) => {
      await tripsApi.close(trip.id, {
        closureReason: values.closureReason.trim(),
        closureEvidenceId: values.closureEvidenceId.trim(),
        endOdometer: Number(values.endOdometer),
        expectedVersion: trip.version,
      });
      onSaved();
      onClose();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Close trip"
      description="Closure reason and closure evidence are both mandatory — the service refuses closure without them."
      submitLabel="Close trip"
      submitting={form.submitting}
      formError={form.formError}
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Alert severity="info" variant="outlined">
        Register the closure evidence under Evidence &amp; audit first, then paste its reference ID
        here. Closing without evidence is refused with FLEET_CLOSURE_EVIDENCE_MISSING.
      </Alert>
      <NumberInput
        label="End odometer (km)"
        required
        value={form.values.endOdometer}
        onChange={(value) => form.setValue('endOdometer', value)}
        helperText={
          trip.startOdometer !== null
            ? `Start odometer was ${formatNumber(trip.startOdometer)} km`
            : undefined
        }
        {...form.fieldProps('endOdometer')}
      />
      <TextInput
        label="Closure evidence reference ID"
        required
        value={form.values.closureEvidenceId}
        onChange={(value) => form.setValue('closureEvidenceId', value)}
        {...form.fieldProps('closureEvidenceId')}
      />
      <TextInput
        label="Closure reason"
        required
        multiline
        minRows={3}
        value={form.values.closureReason}
        onChange={(value) => form.setValue('closureReason', value)}
        {...form.fieldProps('closureReason')}
      />
    </FormDialog>
  );
};

/* ---------------------------------------------------------------------------------------------
 * Hold / resume and cancel
 * ------------------------------------------------------------------------------------------- */

export const HoldTripDialog = ({
  open,
  onClose,
  onSaved,
  trip,
  action,
}: BaseProps & { trip: TripResponse; action: 'HOLD' | 'RESUME' }) => {
  const form = useFleetForm({
    initialValues: { reason: '' },
    schema: { reason: maxLength('Reason', 1000) },
    onSubmit: async (values) => {
      await tripsApi.holdOrResume(trip.id, {
        action,
        reason: values.reason.trim() || null,
        expectedVersion: trip.version,
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title={action === 'HOLD' ? 'Place trip on hold' : 'Resume trip'}
      description={trip.tripNumber}
      submitLabel={action === 'HOLD' ? 'Place on hold' : 'Resume'}
      submitting={form.submitting}
      formError={form.formError}
      onClose={onClose}
      onSubmit={form.submit}
    >
      <TextInput
        label="Reason"
        multiline
        minRows={3}
        value={form.values.reason}
        onChange={(value) => form.setValue('reason', value)}
        {...form.fieldProps('reason')}
      />
    </FormDialog>
  );
};

export const CancelTripDialog = ({
  open,
  onClose,
  onSaved,
  trip,
}: BaseProps & { trip: TripResponse }) => {
  const form = useFleetForm({
    initialValues: { reason: '' },
    schema: { reason: compose(required('Reason'), maxLength('Reason', 1000)) },
    onSubmit: async (values) => {
      await tripsApi.cancel(trip.id, {
        reason: values.reason.trim(),
        expectedVersion: trip.version,
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Cancel trip"
      description={`${trip.tripNumber} will be cancelled. This cannot be undone — the record stays in history.`}
      submitLabel="Cancel trip"
      submitting={form.submitting}
      formError={form.formError}
      destructive
      onClose={onClose}
      onSubmit={form.submit}
    >
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
 * Record an inspection — POST /api/v1/fleet/trips/{id}/inspections
 * ------------------------------------------------------------------------------------------- */

interface FindingDraft {
  checkCode: string;
  description: string;
  severity: DefectSeverity;
}

export const RecordInspectionDialog = ({
  open,
  onClose,
  onSaved,
  trip,
  vehicleOdometer,
}: BaseProps & { trip: TripResponse; vehicleOdometer?: number }) => {
  const [findings, setFindings] = useState<FindingDraft[]>([]);
  const [findingErrors, setFindingErrors] = useState<string | undefined>(undefined);

  const form = useFleetForm({
    initialValues: {
      inspectionType: 'PRE_TRIP' as InspectionType,
      odometerReading: vehicleOdometer ? String(vehicleOdometer) : '',
      evidenceId: '',
      notes: '',
    },
    schema: {
      inspectionType: required('Inspection type'),
      odometerReading: compose(
        required('Odometer reading'),
        nonNegativeInteger('Odometer reading'),
      ),
      notes: maxLength('Notes', 2000),
    },
    onSubmit: async (values) => {
      await tripsApi.recordInspection(trip.id, {
        inspectionType: values.inspectionType,
        odometerReading: Number(values.odometerReading),
        evidenceId: values.evidenceId.trim() || null,
        findings: findings.map((finding) => ({
          checkCode: finding.checkCode.trim(),
          description: finding.description.trim(),
          severity: finding.severity,
        })),
        notes: values.notes.trim() || null,
      });
      onSaved();
      onClose();
      form.reset();
      setFindings([]);
    },
  });

  const addFinding = () =>
    setFindings((current) => [...current, { checkCode: '', description: '', severity: 'MINOR' }]);

  const updateFinding = (index: number, patch: Partial<FindingDraft>) =>
    setFindings((current) =>
      current.map((finding, position) => (position === index ? { ...finding, ...patch } : finding)),
    );

  const removeFinding = (index: number) =>
    setFindings((current) => current.filter((_finding, position) => position !== index));

  const incompleteFinding = findings.some(
    (finding) => !finding.checkCode.trim() || !finding.description.trim(),
  );
  const hasCritical = findings.some((finding) => finding.severity === 'CRITICAL');

  const submit = () => {
    if (incompleteFinding) {
      setFindingErrors('Every finding needs a check code and a description.');
      return;
    }
    setFindingErrors(undefined);
    void form.submit();
  };

  const predictedResult =
    findings.length === 0 ? 'PASSED' : hasCritical ? 'FAILED' : 'PASSED_WITH_DEFECTS';

  return (
    <FormDialog
      open={open}
      title="Record an inspection"
      description={`${trip.tripNumber}. Findings decide the result — and a critical defect blocks the vehicle from use.`}
      submitLabel="Record inspection"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={submit}
    >
      <Box sx={twoColumn}>
        <EnumSelect
          label="Inspection type"
          required
          value={form.values.inspectionType}
          options={INSPECTION_TYPES}
          onChange={(value) =>
            form.setValue('inspectionType', (value || 'PRE_TRIP') as InspectionType)
          }
          {...form.fieldProps('inspectionType')}
        />
        <NumberInput
          label="Odometer reading (km)"
          required
          value={form.values.odometerReading}
          onChange={(value) => form.setValue('odometerReading', value)}
          {...form.fieldProps('odometerReading')}
        />
        <TextInput
          label="Evidence reference ID"
          value={form.values.evidenceId}
          onChange={(value) => form.setValue('evidenceId', value)}
          helperText="Optional for a trip inspection."
          {...form.fieldProps('evidenceId')}
        />
      </Box>

      <Divider />

      <Stack direction="row" alignItems="center" justifyContent="space-between">
        <Typography variant="subtitle2" fontWeight={700}>
          Findings ({findings.length})
        </Typography>
        <Button
          size="small"
          variant="soft"
          color="neutral"
          onClick={addFinding}
          startIcon={<IconifyIcon icon="material-symbols:add-rounded" />}
        >
          Add finding
        </Button>
      </Stack>

      {findings.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No findings recorded — this inspection will pass.
        </Typography>
      ) : (
        <Stack spacing={1.5}>
          {findings.map((finding, index) => (
            <Stack
              key={index}
              direction={{ xs: 'column', sm: 'row' }}
              spacing={1.5}
              alignItems={{ sm: 'flex-start' }}
              sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 1.5 }}
            >
              <TextInput
                label="Check code"
                required
                value={finding.checkCode}
                onChange={(value) => updateFinding(index, { checkCode: value })}
                sx={{ maxWidth: { sm: 160 } }}
              />
              <TextInput
                label="Description"
                required
                value={finding.description}
                onChange={(value) => updateFinding(index, { description: value })}
              />
              <EnumSelect
                label="Severity"
                value={finding.severity}
                options={DEFECT_SEVERITIES}
                onChange={(value) =>
                  updateFinding(index, { severity: (value || 'MINOR') as DefectSeverity })
                }
                sx={{ maxWidth: { sm: 150 } }}
              />
              <IconButton
                onClick={() => removeFinding(index)}
                aria-label="Remove finding"
                sx={{ mt: { sm: 0.5 } }}
              >
                <IconifyIcon icon="material-symbols:delete-outline-rounded" />
              </IconButton>
            </Stack>
          ))}
        </Stack>
      )}

      {findingErrors && <Alert severity="error">{findingErrors}</Alert>}

      <TextInput
        label="Notes"
        multiline
        minRows={2}
        value={form.values.notes}
        onChange={(value) => form.setValue('notes', value)}
        {...form.fieldProps('notes')}
      />

      <Alert severity={hasCritical ? 'error' : findings.length > 0 ? 'warning' : 'success'}>
        Expected result: <strong>{humanise(predictedResult)}</strong>
        {hasCritical && ' — a critical defect blocks the vehicle from use until it is resolved.'}
      </Alert>
    </FormDialog>
  );
};
