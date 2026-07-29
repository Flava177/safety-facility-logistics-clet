import { DriverLogbook } from 'modules/fuel/api/dto';
import { LOGBOOK_USE_CLASSIFICATIONS, LogbookUseClassification } from 'modules/fuel/api/enums';
import { LogbookTransition, driverLogbooksApi } from 'modules/fuel/api/fuelApi';
import {
  LOGBOOK_RULES,
  logbookSubmissionBlockers,
  logbookSubmitTarget,
} from 'modules/fuel/api/workflow';
import {
  DriverSelect,
  TripSelect,
  VehicleSelect,
} from 'modules/fuel/components/FleetReferenceSelect';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import FormDialog from 'shared/components/FormDialog';
import SiteSelect from 'shared/components/SiteSelect';
import { DateField, DateTimeField } from 'shared/components/DateField';
import { Checkbox, EnumSelect, NumberInput, TextAreaInput, TextInput } from 'shared/components/fields';
import { useFleetForm } from 'shared/validation/useFleetForm';
import {
  compose,
  integerAtLeast,
  maxLength,
  required,
  validDateTime,
} from 'shared/validation/validators';

const twoColumn = 'grid gap-4 sm:grid-cols-2';

interface CreateLogbookDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: (logbook: DriverLogbook) => void;
  defaultSiteCode: string;
}

/**
 * Create a draft logbook — `POST /api/v1/fuel/logbooks`.
 *
 * The record is always created as `DRAFT`; there is no create-and-submit. That is deliberate on the
 * service side and it shapes this form: the end of the journey (end time, closing odometer,
 * declaration) is optional here, because a driver opens the logbook when the journey starts. It
 * becomes mandatory at submission, and this dialog says so rather than demanding it up front.
 *
 * The two cross-field rules are the record's own: `endTime` may not precede `startTime`, and
 * `endOdometer` may not be below `startOdometer`. Both are enforced in `DriverLogbook`'s compact
 * constructor, so a violation would be a 400 rather than a field error — checking here turns it
 * into an inline message on the field that caused it.
 */
export const CreateLogbookDialog = ({
  open,
  onClose,
  onSaved,
  defaultSiteCode,
}: CreateLogbookDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      siteCode: defaultSiteCode,
      driverId: '',
      vehicleId: '',
      tripId: '',
      journeyDate: '',
      startTime: '',
      endTime: '',
      origin: '',
      destination: '',
      routeNotes: '',
      useClassification: 'OFFICIAL' as LogbookUseClassification,
      purpose: '',
      passengerLoadNotes: '',
      startOdometer: '',
      endOdometer: '',
      declarationAccepted: false,
      evidenceId: '',
    },
    schema: {
      siteCode: required('Site code'),
      driverId: required('Driver'),
      vehicleId: required('Vehicle'),
      journeyDate: required('Journey date'),
      startTime: compose(required('Start time'), validDateTime('Start time')),
      endTime: validDateTime('End time'),
      origin: compose(required('Origin'), maxLength('Origin', 200)),
      destination: compose(required('Destination'), maxLength('Destination', 200)),
      useClassification: required('Use classification'),
      purpose: compose(required('Purpose'), maxLength('Purpose', 500)),
      routeNotes: maxLength('Route notes', 1000),
      passengerLoadNotes: maxLength('Passenger and load notes', 1000),
      startOdometer: compose(
        required('Opening odometer'),
        integerAtLeast('Opening odometer', 0),
      ),
      endOdometer: integerAtLeast('Closing odometer', 0),
    },
    crossFieldValidate: (values) => {
      const errors: Record<string, string> = {};
      if (values.startTime && values.endTime) {
        const start = new Date(values.startTime).getTime();
        const end = new Date(values.endTime).getTime();
        // The record allows equality; only a genuinely earlier end is refused.
        if (!Number.isNaN(start) && !Number.isNaN(end) && end < start) {
          errors.endTime = 'End time cannot be before the start time.';
        }
      }
      if (values.startOdometer !== '' && values.endOdometer !== '') {
        if (Number(values.endOdometer) < Number(values.startOdometer)) {
          errors.endOdometer = 'Closing odometer cannot be lower than the opening reading.';
        }
      }
      return errors;
    },
    onSubmit: async (values) => {
      const saved = await driverLogbooksApi.create({
        siteCode: values.siteCode.trim().toUpperCase(),
        driverId: values.driverId,
        vehicleId: values.vehicleId,
        tripId: values.tripId || null,
        journeyDate: values.journeyDate,
        startTime: new Date(values.startTime).toISOString(),
        endTime: values.endTime ? new Date(values.endTime).toISOString() : null,
        origin: values.origin.trim(),
        destination: values.destination.trim(),
        routeNotes: values.routeNotes.trim() || null,
        useClassification: values.useClassification,
        purpose: values.purpose.trim(),
        passengerLoadNotes: values.passengerLoadNotes.trim() || null,
        startOdometer: Number(values.startOdometer),
        endOdometer: values.endOdometer === '' ? null : Number(values.endOdometer),
        declarationAccepted: values.declarationAccepted,
        evidenceId: values.evidenceId.trim() || null,
      });
      onSaved(saved);
      onClose();
    },
  });

  const incomplete =
    !form.values.endTime || form.values.endOdometer === '' || !form.values.declarationAccepted;

  return (
    <FormDialog
      open={open}
      title="Create a driver logbook"
      description="Created as a draft. It can be completed and submitted later."
      submitLabel="Create draft"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="lg"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <div className={twoColumn}>
        <SiteSelect
          required
          value={form.values.siteCode}
          onChange={(value) =>
            form.setValues({ siteCode: value, driverId: '', vehicleId: '', tripId: '' })
          }
          {...form.fieldProps('siteCode')}
        />
        <DateField
          label="Journey date"
          required
          value={form.values.journeyDate}
          onChange={(value) => form.setValue('journeyDate', value)}
          {...form.fieldProps('journeyDate')}
        />
        <DriverSelect
          required
          siteCode={form.values.siteCode}
          value={form.values.driverId}
          onChange={(value) => form.setValue('driverId', value)}
          {...form.fieldProps('driverId')}
        />
        <VehicleSelect
          required
          siteCode={form.values.siteCode}
          value={form.values.vehicleId}
          onChange={(value) => form.setValue('vehicleId', value)}
          {...form.fieldProps('vehicleId')}
        />
        <DateTimeField
          label="Start time"
          required
          value={form.values.startTime}
          onChange={(value) => form.setValue('startTime', value)}
          {...form.fieldProps('startTime')}
        />
        <DateTimeField
          label="End time"
          value={form.values.endTime}
          onChange={(value) => form.setValue('endTime', value)}
          {...form.fieldProps('endTime', 'Required before the logbook can be submitted.')}
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
        <NumberInput
          label="Opening odometer"
          required
          suffix="km"
          value={form.values.startOdometer}
          onChange={(value) => form.setValue('startOdometer', value)}
          {...form.fieldProps('startOdometer')}
        />
        <NumberInput
          label="Closing odometer"
          suffix="km"
          value={form.values.endOdometer}
          onChange={(value) => form.setValue('endOdometer', value)}
          {...form.fieldProps('endOdometer', 'Required before the logbook can be submitted.')}
        />
        <EnumSelect
          label="Use classification"
          required
          value={form.values.useClassification}
          options={LOGBOOK_USE_CLASSIFICATIONS}
          onChange={(value) =>
            form.setValue('useClassification', (value || 'OFFICIAL') as LogbookUseClassification)
          }
          {...form.fieldProps('useClassification')}
        />
        <TripSelect
          siteCode={form.values.siteCode}
          value={form.values.tripId}
          onChange={(value) => form.setValue('tripId', value)}
          {...form.fieldProps(
            'tripId',
            'Optional. A completed trip with no logbook raises an anomaly.',
          )}
        />
      </div>

      <TextInput
        label="Purpose"
        required
        value={form.values.purpose}
        onChange={(value) => form.setValue('purpose', value)}
        {...form.fieldProps('purpose')}
      />
      <TextAreaInput
        label="Route notes"
        rows={2}
        value={form.values.routeNotes}
        onChange={(value) => form.setValue('routeNotes', value)}
        {...form.fieldProps('routeNotes')}
      />
      <TextAreaInput
        label="Passenger and load notes"
        rows={2}
        value={form.values.passengerLoadNotes}
        onChange={(value) => form.setValue('passengerLoadNotes', value)}
        {...form.fieldProps('passengerLoadNotes')}
      />
      <TextInput
        label="Evidence reference"
        value={form.values.evidenceId}
        onChange={(value) => form.setValue('evidenceId', value)}
        {...form.fieldProps('evidenceId', 'Optional. Register it under Evidence & audit first.')}
      />

      <Checkbox
        checked={form.values.declarationAccepted}
        onChange={(checked) => form.setValue('declarationAccepted', checked)}
        label="The driver declares this record to be accurate"
        hint="Required before submission. It can be accepted now or when the journey is completed."
      />

      {incomplete && (
        <Alert variant="info" title="This draft cannot be submitted yet">
          Submission needs an end time, a closing odometer reading and the driver declaration. The
          draft saves without them.
        </Alert>
      )}
    </FormDialog>
  );
};

interface LogbookTransitionDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  logbook: DriverLogbook;
  transition: LogbookTransition;
}

/**
 * The six logbook transitions in one dialog.
 *
 * `POST /logbooks/{id}/{submit|review|return|approve|reopen|cancel}` is a single service endpoint
 * taking a single optional `comment`, so this is one dialog rather than six near-identical ones.
 * What differs per transition is what the service does with the comment and whether it is required:
 * `return`, `reopen` and `cancel` all reach a `require(...)` in the domain record and fail with a
 * 400 without it. `approve`'s comment is genuinely optional and is not asked for as though it were.
 */
export const LogbookTransitionDialog = ({
  open,
  onClose,
  onSaved,
  logbook,
  transition,
}: LogbookTransitionDialogProps) => {
  const rule = LOGBOOK_RULES[transition];
  const mandatory = rule.requiredField !== null;
  const blockers = transition === 'submit' ? logbookSubmissionBlockers(logbook) : [];

  const copy: Record<LogbookTransition, { title: string; submit: string; note?: string }> = {
    submit: {
      title: logbook.status === 'RETURNED' ? 'Resubmit this logbook' : 'Submit this logbook',
      submit: logbook.status === 'RETURNED' ? 'Resubmit' : 'Submit',
      note: `It moves to ${humanise(logbookSubmitTarget(logbook.status)).toLowerCase()} and is no longer editable by the driver.`,
    },
    review: {
      title: 'Start reviewing this logbook',
      submit: 'Start review',
      note: 'It moves to under review, from where it can be approved or returned.',
    },
    return: {
      title: 'Return this logbook to the driver',
      submit: 'Return',
      note: 'Your comment is what the driver sees. It is the only guidance they get.',
    },
    approve: {
      title: 'Approve this logbook',
      submit: 'Approve',
      note: 'An approved logbook is locked. Changing it afterwards needs a privileged reopen.',
    },
    reopen: {
      title: 'Reopen this approved logbook',
      submit: 'Reopen',
      note: 'Privileged — needs FUEL_LOGBOOK_REOPEN. The reason is recorded against the record.',
    },
    cancel: {
      title: 'Cancel this logbook',
      submit: 'Cancel logbook',
      note: 'The record stays in the register and in history. It cannot be revived.',
    },
  };

  const form = useFleetForm({
    initialValues: { comment: '' },
    schema: {
      comment: mandatory
        ? compose(required(commentLabel(transition)), maxLength(commentLabel(transition), 1000))
        : maxLength(commentLabel(transition), 1000),
    },
    onSubmit: async (values) => {
      await driverLogbooksApi.transition(logbook.id, transition, {
        comment: values.comment.trim() || null,
      });
      onSaved();
      onClose();
    },
  });

  return (
    <FormDialog
      open={open}
      title={copy[transition].title}
      description={`${logbook.logbookNumber} · ${logbook.origin} → ${logbook.destination}`}
      submitLabel={copy[transition].submit}
      submitting={form.submitting}
      // Blocked outright where the record already fails the service's own precondition: submitting
      // an incomplete draft can only produce "completed journey and driver declaration are required".
      submitDisabled={blockers.length > 0}
      formError={form.formError}
      destructive={transition === 'cancel'}
      onClose={onClose}
      onSubmit={form.submit}
    >
      {blockers.length > 0 && (
        <Alert variant="error" title="The service will refuse this submission">
          <ul className="mt-1 list-disc space-y-1 pl-4">
            {blockers.map((blocker) => (
              <li key={blocker}>{blocker}</li>
            ))}
          </ul>
          <p className="mt-2">
            A draft cannot be edited from this dashboard — the fuel service exposes no logbook update
            endpoint — so this record has to be cancelled and recreated with the missing detail.
          </p>
        </Alert>
      )}

      {copy[transition].note && <Alert variant="info">{copy[transition].note}</Alert>}

      {(mandatory || transition === 'approve') && (
        <TextAreaInput
          label={commentLabel(transition)}
          required={mandatory}
          rows={3}
          value={form.values.comment}
          onChange={(value) => form.setValue('comment', value)}
          {...form.fieldProps(
            'comment',
            mandatory ? undefined : 'Optional. Recorded as the review comment.',
          )}
        />
      )}
    </FormDialog>
  );
};

/** What the single `comment` field is called for each transition, in the operator's terms. */
const commentLabel = (transition: LogbookTransition): string => {
  switch (transition) {
    case 'return':
      return 'What the driver must correct';
    case 'approve':
      return 'Approval comment';
    case 'reopen':
      return 'Reason for reopening';
    case 'cancel':
      return 'Reason for cancelling';
    default:
      return 'Comment';
  }
};
