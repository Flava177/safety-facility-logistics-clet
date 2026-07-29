import { FuelPolicy } from 'modules/fuel/api/dto';
import { fuelPoliciesApi } from 'modules/fuel/api/fuelApi';
import Alert from 'shared/components/Alert';
import FormDialog from 'shared/components/FormDialog';
import SiteSelect from 'shared/components/SiteSelect';
import { DateTimeField } from 'shared/components/DateField';
import { Checkbox, NumberInput, TextInput } from 'shared/components/fields';
import { useFleetForm } from 'shared/validation/useFleetForm';
import {
  compose,
  dateRangeError,
  integerAtLeast,
  maxLength,
  nonNegativeNumber,
  positiveNumber,
  required,
  validDateTime,
} from 'shared/validation/validators';

const twoColumn = 'grid gap-4 sm:grid-cols-2';
const threeColumn = 'grid gap-4 sm:grid-cols-3';

/** `datetime-local` text to the ISO instant the service expects; blank stays blank. */
const toInstant = (value: string): string | null =>
  value ? new Date(value).toISOString() : null;

/** Comma-separated entry to the `Set<String>` the request takes. Upper-cased, as the domain does. */
const toSet = (value: string): string[] =>
  value
    .split(',')
    .map((entry) => entry.trim().toUpperCase())
    .filter(Boolean);

interface CreatePolicyDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: (policy: FuelPolicy) => void;
  defaultSiteCode: string;
}

/**
 * Create a fuel policy — `POST /api/v1/fuel/policies`.
 *
 * Every field on `PolicyRequest` is here, in the order the rules read them: identity and period,
 * then the limits reconciliation checks, then the exception settings. The four primitives
 * (`policyVersion`, `odometerJumpTolerance`, `receiptGraceHours`, `anomalySlaHours`) always send a
 * number — sending `null` for a Java `int`/`long` fails deserialisation *before* Bean Validation
 * runs, and the operator gets a Jackson message instead of a field error.
 */
export const CreatePolicyDialog = ({
  open,
  onClose,
  onSaved,
  defaultSiteCode,
}: CreatePolicyDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      siteCode: defaultSiteCode,
      name: '',
      effectiveFrom: '',
      effectiveTo: '',
      policyVersion: '1',
      maxPerTransaction: '',
      dailyLimit: '',
      monthlyLimit: '',
      tankCapacity: '',
      minConsumption: '',
      maxConsumption: '',
      odometerJumpTolerance: '500',
      receiptRequired: true,
      receiptGraceHours: '24',
      materialityAmount: '',
      anomalySlaHours: '24',
      allowedFuelProducts: '',
      approvedVendors: '',
    },
    schema: {
      siteCode: required('Site code'),
      name: compose(required('Policy name'), maxLength('Policy name', 160)),
      effectiveFrom: compose(required('Effective from'), validDateTime('Effective from')),
      effectiveTo: validDateTime('Effective to'),
      policyVersion: compose(required('Policy version'), integerAtLeast('Policy version', 1)),
      maxPerTransaction: compose(
        required('Maximum per transaction'),
        positiveNumber('Maximum per transaction'),
      ),
      dailyLimit: nonNegativeNumber('Daily limit'),
      monthlyLimit: nonNegativeNumber('Monthly limit'),
      tankCapacity: nonNegativeNumber('Tank capacity'),
      minConsumption: nonNegativeNumber('Minimum consumption'),
      maxConsumption: nonNegativeNumber('Maximum consumption'),
      odometerJumpTolerance: compose(
        required('Odometer jump tolerance'),
        integerAtLeast('Odometer jump tolerance', 0),
      ),
      receiptGraceHours: compose(
        required('Receipt grace period'),
        integerAtLeast('Receipt grace period', 0),
      ),
      materialityAmount: compose(
        required('Materiality amount'),
        nonNegativeNumber('Materiality amount'),
      ),
      anomalySlaHours: compose(required('Anomaly SLA'), integerAtLeast('Anomaly SLA', 1)),
    },
    crossFieldValidate: (values) => {
      const errors: Record<string, string> = {};
      // `FuelPolicy` refuses `effectiveTo` that does not strictly follow `effectiveFrom`.
      const range = dateRangeError(
        values.effectiveFrom,
        values.effectiveTo,
        'Effective from',
        'Effective to',
      );
      if (range) {
        errors.effectiveTo = range;
      }
      // The consumption range is only used when both bounds are set (`reconcile` checks for both),
      // and an inverted pair would make CONSUMPTION_RANGE fail every transaction.
      if (values.minConsumption && values.maxConsumption) {
        if (Number(values.minConsumption) > Number(values.maxConsumption)) {
          errors.maxConsumption = 'Maximum consumption must be at least the minimum.';
        }
      }
      return errors;
    },
    onSubmit: async (values) => {
      const saved = await fuelPoliciesApi.create({
        siteCode: values.siteCode.trim().toUpperCase(),
        name: values.name.trim(),
        effectiveFrom: toInstant(values.effectiveFrom) as string,
        effectiveTo: toInstant(values.effectiveTo),
        policyVersion: Number(values.policyVersion),
        maxPerTransaction: Number(values.maxPerTransaction),
        dailyLimit: values.dailyLimit === '' ? null : Number(values.dailyLimit),
        monthlyLimit: values.monthlyLimit === '' ? null : Number(values.monthlyLimit),
        tankCapacity: values.tankCapacity === '' ? null : Number(values.tankCapacity),
        minConsumption: values.minConsumption === '' ? null : Number(values.minConsumption),
        maxConsumption: values.maxConsumption === '' ? null : Number(values.maxConsumption),
        odometerJumpTolerance: Number(values.odometerJumpTolerance),
        receiptRequired: values.receiptRequired,
        receiptGraceHours: Number(values.receiptGraceHours),
        materialityAmount: Number(values.materialityAmount),
        anomalySlaHours: Number(values.anomalySlaHours),
        allowedFuelProducts: toSet(values.allowedFuelProducts),
        approvedVendors: toSet(values.approvedVendors),
      });
      onSaved(saved);
      onClose();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Create a fuel policy"
      description="Reconciliation reads the policy that was in effect when the transaction occurred, so the period matters as much as the limits."
      submitLabel="Create policy"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="lg"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Alert variant="info" title="Periods may not overlap">
        The service refuses a policy whose period overlaps an active one for this site, and names the
        policies it clashes with. Two active policies covering one instant would make the rules a
        transaction is judged against depend on which row the query returned.
      </Alert>

      <div className={twoColumn}>
        <SiteSelect
          required
          value={form.values.siteCode}
          onChange={(value) => form.setValue('siteCode', value)}
          {...form.fieldProps('siteCode')}
        />
        <TextInput
          label="Policy name"
          required
          value={form.values.name}
          onChange={(value) => form.setValue('name', value)}
          {...form.fieldProps('name')}
        />
        <DateTimeField
          label="Effective from"
          required
          value={form.values.effectiveFrom}
          onChange={(value) => form.setValue('effectiveFrom', value)}
          {...form.fieldProps('effectiveFrom')}
        />
        <DateTimeField
          label="Effective to"
          value={form.values.effectiveTo}
          onChange={(value) => form.setValue('effectiveTo', value)}
          {...form.fieldProps('effectiveTo', 'Leave blank for an open-ended policy.')}
        />
      </div>

      <div className={threeColumn}>
        <NumberInput
          label="Policy version"
          required
          min={1}
          value={form.values.policyVersion}
          onChange={(value) => form.setValue('policyVersion', value)}
          {...form.fieldProps('policyVersion', 'Recorded against every reconciliation.')}
        />
        <NumberInput
          label="Maximum per transaction"
          required
          step={0.001}
          value={form.values.maxPerTransaction}
          onChange={(value) => form.setValue('maxPerTransaction', value)}
          {...form.fieldProps('maxPerTransaction', 'Quantity, in the unit you dispense in.')}
        />
        <NumberInput
          label="Tank capacity"
          step={0.001}
          value={form.values.tankCapacity}
          onChange={(value) => form.setValue('tankCapacity', value)}
          {...form.fieldProps('tankCapacity', 'Optional. Skipped when blank.')}
        />
        <NumberInput
          label="Daily limit"
          step={0.001}
          value={form.values.dailyLimit}
          onChange={(value) => form.setValue('dailyLimit', value)}
          {...form.fieldProps('dailyLimit', 'Recorded; not yet checked by reconciliation.')}
        />
        <NumberInput
          label="Monthly limit"
          step={0.001}
          value={form.values.monthlyLimit}
          onChange={(value) => form.setValue('monthlyLimit', value)}
          {...form.fieldProps('monthlyLimit', 'Recorded; not yet checked by reconciliation.')}
        />
        <NumberInput
          label="Odometer jump tolerance"
          required
          suffix="km"
          value={form.values.odometerJumpTolerance}
          onChange={(value) => form.setValue('odometerJumpTolerance', value)}
          {...form.fieldProps('odometerJumpTolerance')}
        />
        <NumberInput
          label="Minimum consumption"
          step={0.0001}
          value={form.values.minConsumption}
          onChange={(value) => form.setValue('minConsumption', value)}
          {...form.fieldProps('minConsumption', 'Quantity per kilometre.')}
        />
        <NumberInput
          label="Maximum consumption"
          step={0.0001}
          value={form.values.maxConsumption}
          onChange={(value) => form.setValue('maxConsumption', value)}
          {...form.fieldProps('maxConsumption', 'Both bounds are needed for the rule to run.')}
        />
        <NumberInput
          label="Materiality amount"
          required
          step={0.01}
          value={form.values.materialityAmount}
          onChange={(value) => form.setValue('materialityAmount', value)}
          {...form.fieldProps('materialityAmount', 'At or above this, an anomaly is material.')}
        />
      </div>

      <div className={twoColumn}>
        <NumberInput
          label="Receipt grace period"
          required
          suffix="hrs"
          value={form.values.receiptGraceHours}
          onChange={(value) => form.setValue('receiptGraceHours', value)}
          {...form.fieldProps('receiptGraceHours')}
        />
        <NumberInput
          label="Anomaly SLA"
          required
          min={1}
          suffix="hrs"
          value={form.values.anomalySlaHours}
          onChange={(value) => form.setValue('anomalySlaHours', value)}
          {...form.fieldProps('anomalySlaHours', 'Time to resolve before the sweep escalates.')}
        />
        <TextInput
          label="Allowed fuel products"
          value={form.values.allowedFuelProducts}
          onChange={(value) => form.setValue('allowedFuelProducts', value)}
          {...form.fieldProps(
            'allowedFuelProducts',
            'Comma separated. Leave blank to allow any product.',
          )}
        />
        <TextInput
          label="Approved vendors"
          value={form.values.approvedVendors}
          onChange={(value) => form.setValue('approvedVendors', value)}
          {...form.fieldProps('approvedVendors', 'Comma separated. Blank allows any vendor.')}
        />
      </div>

      <Checkbox
        checked={form.values.receiptRequired}
        onChange={(checked) => form.setValue('receiptRequired', checked)}
        label="A receipt is required"
        hint="A transaction with no receipt raises a missing-receipt anomaly once the grace period has elapsed."
      />
    </FormDialog>
  );
};
