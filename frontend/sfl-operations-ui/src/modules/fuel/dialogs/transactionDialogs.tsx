import { FuelTransaction } from 'modules/fuel/api/dto';
import { CURRENCIES, FUEL_PRODUCTS, QUANTITY_UNITS } from 'modules/fuel/api/enums';
import { fuelTransactionsApi } from 'modules/fuel/api/fuelApi';
import {
  DriverSelect,
  TripSelect,
  VehicleSelect,
} from 'modules/fuel/components/FleetReferenceSelect';
import { formatMoney } from 'modules/fuel/components/fuelFormat';
import Alert from 'shared/components/Alert';
import FormDialog from 'shared/components/FormDialog';
import SiteSelect from 'shared/components/SiteSelect';
import { DateTimeField } from 'shared/components/DateField';
import { EnumSelect, NumberInput, TextAreaInput, TextInput } from 'shared/components/fields';
import { useFleetForm } from 'shared/validation/useFleetForm';
import {
  compose,
  integerAtLeast,
  maxLength,
  nonNegativeNumber,
  positiveNumber,
  required,
  validDateTime,
} from 'shared/validation/validators';

const twoColumn = 'grid gap-4 sm:grid-cols-2';
const threeColumn = 'grid gap-4 sm:grid-cols-3';

/**
 * `FuelTransaction` computes `quantity × unitPrice` at scale 2 and rejects a supplied `totalCost`
 * that disagrees, so the dashboard derives the same figure the same way rather than letting an
 * operator type a total that will be refused.
 */
const derivedTotal = (quantity: string, unitPrice: string): number | null => {
  const q = Number(quantity);
  const p = Number(unitPrice);
  if (!quantity || !unitPrice || !Number.isFinite(q) || !Number.isFinite(p)) {
    return null;
  }
  return Math.round(q * p * 100) / 100;
};

interface CaptureDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: (transaction: FuelTransaction) => void;
  defaultSiteCode: string;
}

/**
 * Manual capture — `POST /api/v1/fuel/transactions`.
 *
 * Two things are deliberately not editable. `sourceSystem` is fixed to `MANUAL`, because that is
 * what selects the `FUEL_TRANSACTION_CAPTURE` permission in the service (any other value routes to
 * the import or integration permission, which a dashboard operator will not hold), and provenance
 * should say where a record really came from. And `totalCost` is derived rather than typed, because
 * the domain refuses a total that is not `quantity × unitPrice` to two decimal places.
 *
 * This is the only fuel mutation that reads `Idempotency-Key`, which the shared client sends on
 * every `post` — so a double submission returns the first transaction rather than creating a second.
 */
export const CaptureTransactionDialog = ({
  open,
  onClose,
  onSaved,
  defaultSiteCode,
}: CaptureDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      siteCode: defaultSiteCode,
      vehicleId: '',
      driverId: '',
      tripId: '',
      occurredAt: '',
      vendorReference: '',
      stationReference: '',
      fuelProduct: 'DIESEL',
      quantity: '',
      quantityUnit: 'LITRE',
      unitPrice: '',
      currency: 'GHS',
      cardReference: '',
      odometerReading: '',
      receiptEvidenceId: '',
      providerTransactionId: '',
      comments: '',
    },
    schema: {
      siteCode: required('Site code'),
      vehicleId: required('Vehicle'),
      driverId: required('Driver'),
      occurredAt: compose(required('Occurred at'), validDateTime('Occurred at')),
      vendorReference: compose(required('Vendor'), maxLength('Vendor', 160)),
      stationReference: maxLength('Station', 160),
      fuelProduct: required('Fuel product'),
      quantity: compose(required('Quantity'), positiveNumber('Quantity')),
      quantityUnit: required('Quantity unit'),
      unitPrice: compose(required('Unit price'), nonNegativeNumber('Unit price')),
      currency: required('Currency'),
      odometerReading: compose(
        required('Odometer reading'),
        integerAtLeast('Odometer reading', 0),
      ),
      comments: maxLength('Comments', 1000),
    },
    onSubmit: async (values) => {
      const saved = await fuelTransactionsApi.capture({
        siteCode: values.siteCode.trim().toUpperCase(),
        providerTransactionId: values.providerTransactionId.trim() || null,
        sourceSystem: 'MANUAL',
        vehicleId: values.vehicleId,
        driverId: values.driverId,
        tripId: values.tripId || null,
        occurredAt: new Date(values.occurredAt).toISOString(),
        vendorReference: values.vendorReference.trim(),
        stationReference: values.stationReference.trim() || null,
        fuelProduct: values.fuelProduct.trim().toUpperCase(),
        quantity: Number(values.quantity),
        quantityUnit: values.quantityUnit,
        unitPrice: Number(values.unitPrice),
        totalCost: derivedTotal(values.quantity, values.unitPrice),
        currency: values.currency,
        cardReference: values.cardReference.trim() || null,
        odometerReading: Number(values.odometerReading),
        receiptEvidenceId: values.receiptEvidenceId.trim() || null,
        comments: values.comments.trim() || null,
      });
      onSaved(saved);
      onClose();
    },
  });

  const total = derivedTotal(form.values.quantity, form.values.unitPrice);

  return (
    <FormDialog
      open={open}
      title="Capture a fuel transaction"
      description="Recorded as RECEIVED. It stays outside the reconciled figures until reconciliation runs against the policy in force when it occurred."
      submitLabel="Capture transaction"
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
          onChange={(value) => {
            // The vehicle, driver and trip lists are scoped to the site, so a site change must
            // clear them — a reference from the previous site would be refused on submission.
            form.setValues({ siteCode: value, vehicleId: '', driverId: '', tripId: '' });
          }}
          {...form.fieldProps('siteCode')}
        />
        <DateTimeField
          label="Occurred at"
          required
          value={form.values.occurredAt}
          onChange={(value) => form.setValue('occurredAt', value)}
          {...form.fieldProps(
            'occurredAt',
            'Selects the policy version reconciliation will judge this against.',
          )}
        />
        <VehicleSelect
          required
          siteCode={form.values.siteCode}
          value={form.values.vehicleId}
          onChange={(value) => form.setValue('vehicleId', value)}
          {...form.fieldProps('vehicleId')}
        />
        <DriverSelect
          required
          siteCode={form.values.siteCode}
          value={form.values.driverId}
          onChange={(value) => form.setValue('driverId', value)}
          {...form.fieldProps('driverId')}
        />
        <TripSelect
          siteCode={form.values.siteCode}
          value={form.values.tripId}
          onChange={(value) => form.setValue('tripId', value)}
          {...form.fieldProps('tripId')}
        />
        <TextInput
          label="Vendor"
          required
          value={form.values.vendorReference}
          onChange={(value) => form.setValue('vendorReference', value)}
          {...form.fieldProps('vendorReference', 'Checked against the policy’s approved vendors.')}
        />
      </div>

      <div className={threeColumn}>
        <EnumSelect
          label="Fuel product"
          required
          value={form.values.fuelProduct as (typeof FUEL_PRODUCTS)[number]}
          options={FUEL_PRODUCTS}
          onChange={(value) => form.setValue('fuelProduct', value || 'DIESEL')}
          {...form.fieldProps('fuelProduct')}
        />
        <NumberInput
          label="Quantity"
          required
          step={0.001}
          value={form.values.quantity}
          onChange={(value) => form.setValue('quantity', value)}
          {...form.fieldProps('quantity')}
        />
        <EnumSelect
          label="Unit"
          required
          value={form.values.quantityUnit as (typeof QUANTITY_UNITS)[number]}
          options={QUANTITY_UNITS}
          onChange={(value) => form.setValue('quantityUnit', value || 'LITRE')}
          {...form.fieldProps('quantityUnit')}
        />
        <NumberInput
          label="Unit price"
          required
          step={0.0001}
          value={form.values.unitPrice}
          onChange={(value) => form.setValue('unitPrice', value)}
          {...form.fieldProps('unitPrice')}
        />
        <EnumSelect
          label="Currency"
          required
          value={form.values.currency as (typeof CURRENCIES)[number]}
          options={CURRENCIES}
          onChange={(value) => form.setValue('currency', value || 'GHS')}
          renderOptionLabel={(option) => option}
          {...form.fieldProps('currency')}
        />
        <NumberInput
          label="Odometer reading"
          required
          suffix="km"
          value={form.values.odometerReading}
          onChange={(value) => form.setValue('odometerReading', value)}
          {...form.fieldProps('odometerReading', 'A raw observation — Fleet owns the accepted value.')}
        />
      </div>

      <Alert variant="info" title="Total cost">
        {total === null
          ? 'Enter a quantity and a unit price to see the total this will be recorded with.'
          : `${formatMoney(total, form.values.currency)} — quantity × unit price, at two decimal
             places. The service computes the same figure and refuses a total that disagrees, so it
             is not entered by hand.`}
      </Alert>

      <div className={twoColumn}>
        <TextInput
          label="Station"
          value={form.values.stationReference}
          onChange={(value) => form.setValue('stationReference', value)}
          {...form.fieldProps('stationReference', 'Optional pump or forecourt reference.')}
        />
        <TextInput
          label="Card reference"
          value={form.values.cardReference}
          onChange={(value) => form.setValue('cardReference', value)}
          {...form.fieldProps('cardReference', 'Stored masked — only the last four digits are kept.')}
        />
        <TextInput
          label="Provider transaction reference"
          value={form.values.providerTransactionId}
          onChange={(value) => form.setValue('providerTransactionId', value)}
          {...form.fieldProps(
            'providerTransactionId',
            'Optional. Used to recognise a re-delivered provider record.',
          )}
        />
        <TextInput
          label="Receipt evidence reference"
          value={form.values.receiptEvidenceId}
          onChange={(value) => form.setValue('receiptEvidenceId', value)}
          {...form.fieldProps(
            'receiptEvidenceId',
            'Register the receipt under Evidence & audit, then paste its identifier.',
          )}
        />
      </div>

      <TextAreaInput
        label="Comments"
        rows={2}
        value={form.values.comments}
        onChange={(value) => form.setValue('comments', value)}
        {...form.fieldProps('comments')}
      />
    </FormDialog>
  );
};

interface VoidDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  transaction: FuelTransaction;
}

/**
 * Void — `POST /api/v1/fuel/transactions/{id}/void`.
 *
 * Privileged (`FUEL_TRANSACTION_VOID`) and irreversible: the record moves to `VOIDED` lifecycle and
 * `FuelTransaction.withStatus` refuses everything afterwards, so it can never be reconciled. The
 * reason is mandatory and **replaces the record's comments**, which is worth saying out loud before
 * an operator overwrites something they wanted to keep.
 */
export const VoidTransactionDialog = ({
  open,
  onClose,
  onSaved,
  transaction,
}: VoidDialogProps) => {
  const form = useFleetForm({
    initialValues: { reason: '' },
    schema: { reason: compose(required('Reason'), maxLength('Reason', 1000)) },
    onSubmit: async (values) => {
      await fuelTransactionsApi.void(transaction.id, { reason: values.reason.trim() });
      onSaved();
      onClose();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Void this transaction"
      description={`${transaction.vendorReference} · ${transaction.fuelProduct}`}
      submitLabel="Void transaction"
      submitting={form.submitting}
      formError={form.formError}
      destructive
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Alert variant="warning" title="This cannot be undone">
        The record moves to the voided lifecycle and can never be reconciled. It stays in the
        register and in the audit trail. Your reason replaces the transaction’s comments
        {transaction.comments ? `, which currently read “${transaction.comments}”.` : '.'}
      </Alert>
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
