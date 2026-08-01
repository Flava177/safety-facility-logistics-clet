import { useEffect, useState } from 'react';
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
import Alert from 'shared/components/Alert';
import BlockerList from 'shared/components/BlockerList';
import Button, { IconButton } from 'shared/components/Button';
import { DateTimeField } from 'shared/components/DateField';
import FormDialog from 'shared/components/FormDialog';
import SiteSelect from 'shared/components/SiteSelect';
import {
  EnumSelect,
  NumberInput,
  SelectInput,
  TextAreaInput,
  TextInput,
} from 'shared/components/fields';
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

const twoColumn = 'grid gap-4 sm:grid-cols-2';

/** Divider plus heading for a subsection of a dialog body. */
const sectionHeading = 'border-t border-gray-200 pt-4 text-theme-sm font-semibold text-brand-900';

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
  onPermitsAssignmentChange,
}: {
  vehicleId: string;
  driverId?: string;
  from?: string;
  to?: string;
  operatingMode?: OperatingMode;
  /** Reports the service's own verdict so the caller can refuse a submission it will reject. */
  onPermitsAssignmentChange?: (permits: boolean) => void;
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

  const permitsAssignment = preview.data?.permitsAssignment;

  useEffect(() => {
    // Undefined while the preview is loading or unavailable — an unknown must never block the form.
    onPermitsAssignmentChange?.(permitsAssignment !== false);
  }, [permitsAssignment, onPermitsAssignmentChange]);

  if (!vehicleId) {
    return (
      <Alert variant="info">Choose a vehicle to see readiness blockers before submitting.</Alert>
    );
  }

  if (preview.loading) {
    return <p className="text-theme-sm text-gray-500">Checking readiness…</p>;
  }

  if (preview.error) {
    return <Alert variant="warning">{preview.error.message}</Alert>;
  }

  if (!preview.data) {
    return null;
  }

  return <BlockerList blockers={preview.data.blockers} />;
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
  const [assignmentPermitted, setAssignmentPermitted] = useState(true);

  return (
    <FormDialog
      open={open}
      title="Plan a trip"
      description="A trip may be planned first and assigned later. Supplying both a vehicle and a driver assigns it immediately."
      submitLabel="Create trip"
      submitting={form.submitting}
      // An immediate assignment carries the same readiness policy the assignment endpoint applies.
      submitDisabled={Boolean(form.values.vehicleId) && !assignmentPermitted}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <div className={twoColumn}>
        <SiteSelect
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
        <DateTimeField
          label="Planned start"
          required
          value={form.values.plannedStart}
          onChange={(value) => form.setValue('plannedStart', value)}
          {...form.fieldProps('plannedStart')}
        />
        <DateTimeField
          label="Planned end"
          required
          value={form.values.plannedEnd}
          onChange={(value) => form.setValue('plannedEnd', value)}
          {...form.fieldProps('plannedEnd')}
        />
      </div>

      <TextAreaInput
        label="Purpose"
        required
        rows={2}
        value={form.values.purpose}
        onChange={(value) => form.setValue('purpose', value)}
        {...form.fieldProps('purpose')}
      />

      <h3 className={sectionHeading}>Assignment (optional)</h3>

      <div className={twoColumn}>
        <SelectInput
          label="Vehicle"
          value={form.values.vehicleId}
          onChange={(value) => form.setValue('vehicleId', value)}
          options={(vehicles.data?.content ?? []).map((vehicle) => ({
            value: vehicle.id,
            label: `${vehicle.registrationNumber} — ${vehicle.make} ${vehicle.model} (${humanise(
              vehicle.availabilityStatus,
            )})`,
          }))}
          allowEmpty
          emptyLabel="Assign later"
          error={Boolean(vehicles.error)}
          helperText={vehicles.error ? vehicles.error.message : 'Active vehicles for this site'}
        />

        <SelectInput
          label="Driver"
          value={form.values.driverId}
          onChange={(value) => form.setValue('driverId', value)}
          options={(drivers.data?.content ?? []).map((driver) => ({
            value: driver.id,
            label: `${driver.displayName} — class ${driver.licenceClass} (${humanise(
              driver.eligibilityStatus,
            )})`,
          }))}
          allowEmpty
          emptyLabel="Assign later"
          error={Boolean(drivers.error)}
          helperText={drivers.error ? drivers.error.message : 'Active drivers for this site'}
        />
      </div>

      {form.values.vehicleId && (
        <AssignmentPreview
          vehicleId={form.values.vehicleId}
          driverId={form.values.driverId}
          from={
            form.values.plannedStart ? fromLocalInputValue(form.values.plannedStart) : undefined
          }
          to={form.values.plannedEnd ? fromLocalInputValue(form.values.plannedEnd) : undefined}
          operatingMode={form.values.operatingMode}
          onPermitsAssignmentChange={setAssignmentPermitted}
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
  const [assignmentPermitted, setAssignmentPermitted] = useState(true);

  return (
    <FormDialog
      open={open}
      title={trip.vehicleId ? 'Reassign trip' : 'Assign trip'}
      description={`${trip.tripNumber} · ${trip.origin} → ${trip.destination}. Blockers below are what the service will apply.`}
      submitLabel={trip.vehicleId ? 'Reassign' : 'Assign'}
      submitting={form.submitting}
      // The preview runs the assignment's own policy: a blocking verdict is a certain refusal.
      submitDisabled={Boolean(form.values.vehicleId) && !assignmentPermitted}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <div className={twoColumn}>
        <SelectInput
          label="Vehicle"
          required
          value={form.values.vehicleId}
          onChange={(value) => form.setValue('vehicleId', value)}
          options={(vehicles.data?.content ?? []).map((vehicle) => ({
            value: vehicle.id,
            label: `${vehicle.registrationNumber} — ${humanise(vehicle.availabilityStatus)}`,
          }))}
          {...form.fieldProps('vehicleId')}
        />

        <SelectInput
          label="Driver"
          required
          value={form.values.driverId}
          onChange={(value) => form.setValue('driverId', value)}
          options={(drivers.data?.content ?? []).map((driver) => ({
            value: driver.id,
            label: `${driver.displayName} — ${humanise(driver.eligibilityStatus)}`,
          }))}
          {...form.fieldProps('driverId')}
        />
      </div>

      <TextInput
        label="Reason"
        value={form.values.reason}
        onChange={(value) => form.setValue('reason', value)}
        {...form.fieldProps('reason', "Recorded on the trip's transition history.")}
      />

      {form.values.vehicleId && (
        <AssignmentPreview
          vehicleId={form.values.vehicleId}
          driverId={form.values.driverId}
          from={trip.plannedStart}
          to={trip.plannedEnd}
          operatingMode={trip.operatingMode}
          onPermitsAssignmentChange={setAssignmentPermitted}
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
    // A newly registered vehicle legitimately reads 0, which is not the same as "no reading".
    initialValues: { startOdometer: vehicleOdometer === undefined ? '' : String(vehicleOdometer) },
    schema: {
      startOdometer: compose(required('Start odometer'), nonNegativeInteger('Start odometer')),
    },
    // A reading below the vehicle's last recorded one is refused with FLEET_ODOMETER_REGRESSION,
    // so it is a validation failure here rather than an advisory the operator can submit past.
    crossFieldValidate: (values) => {
      const message = odometerNotBelow(values.startOdometer, vehicleOdometer);
      return message ? { startOdometer: message } : {};
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
        <p className="text-theme-sm text-gray-500">
          Last recorded vehicle odometer: {formatNumber(vehicleOdometer)} km
        </p>
      )}
      <NumberInput
        label="Start odometer (km)"
        required
        value={form.values.startOdometer}
        onChange={(value) => form.setValue('startOdometer', value)}
        {...form.fieldProps('startOdometer')}
      />
      {regression && <Alert variant="warning">{regression}</Alert>}
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
      // A trip that started on a zeroed odometer still has a start reading to seed from.
      endOdometer: trip.startOdometer === null ? '' : String(trip.startOdometer),
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
      <Alert variant="info">
        Register the closure evidence under Evidence &amp; audit first, then paste its reference ID
        here. Closing without evidence is refused with FLEET_CLOSURE_EVIDENCE_MISSING.
      </Alert>
      <NumberInput
        label="End odometer (km)"
        required
        value={form.values.endOdometer}
        onChange={(value) => form.setValue('endOdometer', value)}
        {...form.fieldProps(
          'endOdometer',
          trip.startOdometer !== null
            ? `Start odometer was ${formatNumber(trip.startOdometer)} km`
            : undefined,
        )}
      />
      <TextInput
        label="Closure evidence reference ID"
        required
        value={form.values.closureEvidenceId}
        onChange={(value) => form.setValue('closureEvidenceId', value)}
        {...form.fieldProps('closureEvidenceId')}
      />
      <TextAreaInput
        label="Closure reason"
        required
        rows={3}
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
      <TextAreaInput
        label="Reason"
        rows={3}
        value={form.values.reason}
        onChange={(value) => form.setValue('reason', value)}
        {...form.fieldProps('reason')}
      />
    </FormDialog>
  );
};

/**
 * The assigned driver answers for their trip (SRS-SFL-S166-02).
 *
 * Confirming needs no dialog of its own — there is nothing to collect — but deferring does, because
 * the reason is what a dispatcher acts on. Both are handled here so the two answers are written the
 * same way and cannot drift apart.
 *
 * The reason field is required only when deferring, and the requirement is stated in the label as
 * well as enforced: a driver who cannot take a trip is usually in a hurry, and a form that refuses on
 * submit without having said why is a form that gets abandoned.
 */
export const AcknowledgeTripDialog = ({
  open,
  onClose,
  onSaved,
  trip,
  answer,
}: BaseProps & { trip: TripResponse; answer: 'CONFIRMED' | 'DEFERRED' }) => {
  const deferring = answer === 'DEFERRED';
  const form = useFleetForm({
    initialValues: { reason: '' },
    schema: {
      reason: deferring
        ? compose(required('A reason'), maxLength('Reason', 1000))
        : maxLength('Reason', 1000),
    },
    onSubmit: async (values) => {
      await tripsApi.acknowledge(trip.id, {
        answer,
        // Sent as null when confirming: the service drops a reason on a confirmation rather than
        // storing it, and sending one anyway would imply it is kept.
        reason: deferring ? values.reason.trim() : null,
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
      title={deferring ? 'Defer this trip' : 'Confirm this trip'}
      description={trip.tripNumber}
      submitLabel={deferring ? 'Defer with reason' : 'Confirm'}
      submitting={form.submitting}
      formError={form.formError}
      onClose={onClose}
      onSubmit={form.submit}
    >
      {deferring ? (
        <>
          <Alert variant="info">
            The trip stays assigned to you and keeps its vehicle. Deferring tells the dispatcher you
            cannot take it as scheduled — it does not release the trip.
          </Alert>
          <TextAreaInput
            label="Why you cannot take this trip"
            rows={3}
            value={form.values.reason}
            onChange={(value) => form.setValue('reason', value)}
            {...form.fieldProps('reason')}
          />
        </>
      ) : (
        <p className="text-theme-sm text-gray-700">
          Confirming tells the dispatcher you will take {trip.tripNumber} as scheduled. The trip stays
          assigned; starting it is a separate step.
        </p>
      )}
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
      <TextAreaInput
        label="Reason"
        required
        rows={3}
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
      // A newly registered vehicle legitimately reads 0, which is not the same as "no reading".
      odometerReading: vehicleOdometer === undefined ? '' : String(vehicleOdometer),
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
      <div className={twoColumn}>
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
          {...form.fieldProps('evidenceId', 'Optional for a trip inspection.')}
        />
      </div>

      <div className={`flex items-center justify-between gap-3 ${sectionHeading}`}>
        <h3>Findings ({findings.length})</h3>
        <Button size="sm" variant="outline" startIcon="plus" onClick={addFinding}>
          Add finding
        </Button>
      </div>

      {findings.length === 0 ? (
        <p className="text-theme-sm text-gray-500">
          No findings recorded — this inspection will pass.
        </p>
      ) : (
        <div className="space-y-3">
          {findings.map((finding, index) => (
            <div
              key={index}
              className="flex flex-col gap-3 rounded-xl border border-gray-200 p-3 sm:flex-row sm:items-start"
            >
              <TextInput
                label="Check code"
                required
                value={finding.checkCode}
                onChange={(value) => updateFinding(index, { checkCode: value })}
                className="sm:w-40"
              />
              <TextInput
                label="Description"
                required
                value={finding.description}
                onChange={(value) => updateFinding(index, { description: value })}
                className="flex-1"
              />
              <EnumSelect
                label="Severity"
                value={finding.severity}
                options={DEFECT_SEVERITIES}
                onChange={(value) =>
                  updateFinding(index, { severity: (value || 'MINOR') as DefectSeverity })
                }
                className="sm:w-40"
              />
              {/* Aligned to the controls rather than the labels, which sit above them. */}
              <IconButton
                name="close"
                label="Remove finding"
                onClick={() => removeFinding(index)}
                className="shrink-0 self-end sm:mt-6 sm:self-auto"
              />
            </div>
          ))}
        </div>
      )}

      {findingErrors && <Alert variant="error">{findingErrors}</Alert>}

      <TextAreaInput
        label="Notes"
        rows={2}
        value={form.values.notes}
        onChange={(value) => form.setValue('notes', value)}
        {...form.fieldProps('notes')}
      />

      <Alert variant={hasCritical ? 'error' : findings.length > 0 ? 'warning' : 'success'}>
        Expected result: <strong>{humanise(predictedResult)}</strong>
        {hasCritical && ' — a critical defect blocks the vehicle from use until it is resolved.'}
      </Alert>
    </FormDialog>
  );
};
