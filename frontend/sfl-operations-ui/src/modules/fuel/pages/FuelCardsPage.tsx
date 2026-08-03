import { useMemo, useState } from 'react';
import type { FuelCard } from 'modules/fuel/api/dto';
import { FUEL_CARD_STATUSES, type FuelCardStatus } from 'modules/fuel/api/enums';
import { fuelCardsApi } from 'modules/fuel/api/fuelApi';
import { useClampPage, useServerPage } from 'modules/fuel/components/useServerPage';
import { DriverSelect, VehicleSelect } from 'modules/fuel/components/FleetReferenceSelect';
import { shortId, siteOf } from 'modules/fuel/components/fuelFormat';
import { canManageFuelCards } from 'modules/fleet/api/access';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import { DateField } from 'shared/components/DateField';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, type Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import FormDialog from 'shared/components/FormDialog';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import {
  EnumSelect,
  NumberInput,
  SelectInput,
  TextAreaInput,
  TextInput,
  type SelectOption,
} from 'shared/components/fields';
import { formatDate, formatDateTime, formatNumber, todayIsoDate } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fuelPaths } from 'shared/layout/navigation';
import { useFleetForm } from 'shared/validation/useFleetForm';
import { compose, maxLength, positiveNumber, required } from 'shared/validation/validators';

type CardAction = 'assign' | 'suspend' | 'reinstate' | 'cancel';

const maskedCard = (value: unknown): string | undefined => {
  if (typeof value !== 'string' || value.trim() === '') {
    return undefined;
  }
  const stripped = value.trim();
  if (/\d{12,}/.test(stripped.replace(/\s+/g, ''))) {
    return 'Enter only the masked provider reference, never a full card number.';
  }
  return /^\*{2,}\d{4}$/.test(stripped)
    ? undefined
    : 'Use the masked form from the provider, e.g. ****1234.';
};

const asNumber = (value: string): number | null => (value === '' ? null : Number(value));

const GHANA_FUEL_CARD_PROVIDERS: SelectOption[] = [
  { value: 'GOIL GHANA', label: 'GOIL Ghana' },
  { value: 'TOTALENERGIES GHANA', label: 'TotalEnergies Ghana' },
  { value: 'SHELL / VIVO ENERGY GHANA', label: 'Shell / Vivo Energy Ghana' },
  { value: 'PUMA ENERGY GHANA', label: 'Puma Energy Ghana' },
  { value: 'STAR OIL GHANA', label: 'Star Oil Ghana' },
  { value: 'ZEN PETROLEUM', label: 'Zen Petroleum' },
  { value: 'ENGEN GHANA', label: 'Engen Ghana' },
  { value: 'ALLIED OIL GHANA', label: 'Allied Oil Ghana' },
  { value: 'FRIMPS OIL', label: 'Frimps Oil' },
  { value: 'CLET FUEL CARDS', label: 'CLET Fuel Cards / internal demo' },
];

const demoMaskedReference = (): string => `****${String(Date.now()).slice(-4)}`;

/**
 * S168 fuel-card register.
 *
 * The backend already stores only masked references; this page keeps the same rule in the browser.
 * Managers can issue and move cards through their lifecycle, while read-only operators can still
 * see why reconciliation treats a card transaction as allowed, mismatched, over-limit or unknown.
 */
const FuelCardsPage = () => {
  const { notifySuccess, notifyError } = useNotifier();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const [status, setStatus] = useState<FuelCardStatus | ''>('');
  const [maskedReference, setMaskedReference] = useState('');
  const [selected, setSelected] = useState<FuelCard | null>(null);
  const [issuing, setIssuing] = useState(false);
  const [action, setAction] = useState<{ card: FuelCard; action: CardAction } | null>(null);

  const mayManage = canManageFuelCards();
  const filterKey = `${siteCode}|${status}|${maskedReference}`;
  const paging = useServerPage(filterKey);

  const query = useApiQuery(
    (signal) =>
      fuelCardsApi.search(
        {
          siteCode,
          status,
          maskedReference: maskedReference.trim() || undefined,
          page: paging.page,
          size: paging.size,
        },
        signal,
      ),
    [filterKey, paging.page, paging.size],
  );

  useClampPage(paging.page, query.data?.totalPages, paging.setPage);

  const cards = useMemo(() => query.data?.content ?? [], [query.data]);
  const activeCards = cards.filter((card) => card.status === 'ACTIVE').length;

  const columns = useMemo<Column<FuelCard>[]>(
    () => [
      {
        key: 'card',
        header: 'Card',
        width: 240,
        cell: (row) => (
          <CellStack
            primary={row.maskedReference}
            secondary={`${row.provider} · ${siteOf(row.siteCode)}`}
          />
        ),
      },
      {
        key: 'assignment',
        header: 'Assigned to',
        width: 230,
        cell: (row) => (
          <CellStack
            primary={row.vehicleId ? `Vehicle ${shortId(row.vehicleId)}` : 'No vehicle'}
            secondary={row.driverId ? `Driver ${shortId(row.driverId)}` : 'No driver'}
          />
        ),
      },
      {
        key: 'limits',
        header: 'Limits',
        width: 240,
        hideBelowLg: true,
        cell: (row) => (
          <CellStack
            primary={`Txn ${row.perTransactionLimit === null ? 'policy' : formatNumber(row.perTransactionLimit)}`}
            secondary={`Daily ${row.dailyLimit === null ? 'policy' : formatNumber(row.dailyLimit)} · Monthly ${
              row.monthlyLimit === null ? 'policy' : formatNumber(row.monthlyLimit)
            }`}
          />
        ),
      },
      {
        key: 'expiry',
        header: 'Validity',
        width: 170,
        hideBelowLg: true,
        cell: (row) => (
          <CellStack
            primary={`Issued ${formatDate(row.issuedOn)}`}
            secondary={row.expiresOn ? `Expires ${formatDate(row.expiresOn)}` : 'No expiry'}
          />
        ),
      },
      {
        key: 'status',
        header: 'Status',
        width: 140,
        align: 'right',
        cell: (row) => <StatusChip value={row.status} />,
      },
    ],
    [],
  );

  return (
    <div>
      <PageHeader
        title="Fuel cards"
        subtitle="Masked card register, assignment and spending limits used by reconciliation."
        crumbs={[{ label: 'Fuel', to: fuelPaths.dashboard }, { label: 'Fuel cards' }]}
        actions={
          <>
            {mayManage && (
              <Button variant="primary" startIcon="plus" onClick={() => setIssuing(true)}>
                Issue card
              </Button>
            )}
            <Button variant="outline" startIcon="refresh" onClick={query.refetch}>
              Refresh
            </Button>
          </>
        }
      />

      <SectionCard flush>
        <FilterBar
          onReset={() => {
            setStatus('');
            setMaskedReference('');
          }}
          resetDisabled={!status && !maskedReference}
        >
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Status"
            value={status}
            options={FUEL_CARD_STATUSES}
            allowEmpty
            emptyLabel="Any status"
            onChange={(value) => setStatus((value || '') as FuelCardStatus | '')}
          />
          <TextInput
            label="Masked reference"
            value={maskedReference}
            onChange={setMaskedReference}
            placeholder="****1234"
            helperText="Contains-match on the provider-masked reference."
          />
        </FilterBar>
      </SectionCard>

      <div className="mt-5 grid gap-5 xl:grid-cols-[1.4fr_0.9fr]">
        <SectionCard
          title="Card register"
          subtitle={`${formatNumber(query.data?.totalElements ?? 0)} cards · ${formatNumber(activeCards)} active on this page`}
          flush
        >
          <DataState
            loading={query.initialising}
            error={query.error}
            empty={cards.length === 0}
            emptyTitle="No fuel cards match the filter"
            emptyHint="Issue a card or clear the filters. Only masked references are stored."
            onRetry={query.refetch}
            minHeight={320}
          >
            <DataTable
              rows={cards}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={setSelected}
              caption="Fuel cards at this site, including assignment, card limits, expiry and status."
              page={query.data?.page ?? paging.page}
              pageSize={query.data?.size ?? paging.size}
              totalElements={query.data?.totalElements ?? 0}
              onPageChange={paging.setPage}
              onPageSizeChange={paging.setSize}
            />
          </DataState>
        </SectionCard>

        <SectionCard
          title={selected ? selected.maskedReference : 'Card detail'}
          subtitle={
            selected
              ? 'The assignment and ceilings reconciliation reads.'
              : 'Select a card to inspect or manage it.'
          }
        >
          {selected ? (
            <div className="space-y-4">
              <KeyValueGrid
                columns={2}
                items={[
                  { label: 'Provider', value: selected.provider },
                  { label: 'Status', value: humanise(selected.status) },
                  { label: 'Site', value: siteOf(selected.siteCode) },
                  { label: 'Issued on', value: formatDate(selected.issuedOn) },
                  { label: 'Expires on', value: selected.expiresOn ? formatDate(selected.expiresOn) : 'No expiry' },
                  { label: 'Vehicle', value: selected.vehicleId ? shortId(selected.vehicleId) : 'Not assigned' },
                  { label: 'Driver', value: selected.driverId ? shortId(selected.driverId) : 'Not assigned' },
                  {
                    label: 'Transaction limit',
                    value:
                      selected.perTransactionLimit === null
                        ? 'Policy fallback'
                        : formatNumber(selected.perTransactionLimit),
                  },
                  {
                    label: 'Daily limit',
                    value: selected.dailyLimit === null ? 'Policy fallback' : formatNumber(selected.dailyLimit),
                  },
                  {
                    label: 'Monthly limit',
                    value:
                      selected.monthlyLimit === null ? 'Policy fallback' : formatNumber(selected.monthlyLimit),
                  },
                  { label: 'Last changed', value: formatDateTime(selected.metadata.lastModifiedAt) },
                  { label: 'Version', value: selected.metadata.version },
                ]}
              />

              {selected.suspensionReason && (
                <Alert variant="warning" title="Lifecycle reason">
                  {selected.suspensionReason}
                </Alert>
              )}

              {selected.notes && (
                <div className="rounded-md border border-gray-200 bg-gray-50 px-4 py-3 text-theme-sm text-gray-700">
                  {selected.notes}
                </div>
              )}

              {mayManage ? (
                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    startIcon="driver"
                    disabled={selected.status === 'CANCELLED'}
                    onClick={() => setAction({ card: selected, action: 'assign' })}
                  >
                    Assign
                  </Button>
                  {selected.status !== 'SUSPENDED' && (
                    <Button
                      size="sm"
                      variant="outline"
                      startIcon="stop"
                      disabled={selected.status === 'CANCELLED'}
                      onClick={() => setAction({ card: selected, action: 'suspend' })}
                    >
                      Suspend
                    </Button>
                  )}
                  {selected.status === 'SUSPENDED' && (
                    <Button
                      size="sm"
                      variant="accent"
                      startIcon="check-circle"
                      onClick={() => setAction({ card: selected, action: 'reinstate' })}
                    >
                      Reinstate
                    </Button>
                  )}
                  <Button
                    size="sm"
                    variant="danger"
                    startIcon="close"
                    disabled={selected.status === 'CANCELLED'}
                    onClick={() => setAction({ card: selected, action: 'cancel' })}
                  >
                    Cancel
                  </Button>
                </div>
              ) : (
                <Alert variant="info" title="Read-only">
                  You can inspect the card register, but issuing and lifecycle changes require
                  FUEL_CARD_MANAGE.
                </Alert>
              )}
            </div>
          ) : (
            <Alert variant="info" title="No full card numbers">
              SFL stores and displays only the provider-masked reference, such as ****1234. The card
              platform remains outside SFL.
            </Alert>
          )}
        </SectionCard>
      </div>

      {issuing && (
        <IssueCardDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setIssuing(false)}
          onSaved={(card) => {
            notifySuccess(`${card.maskedReference} issued.`, 'The card is now available to reconciliation.');
            setSelected(card);
            query.refetch();
          }}
        />
      )}

      {action && (
        <CardActionDialog
          open
          card={action.card}
          action={action.action}
          onClose={() => setAction(null)}
          onSaved={(card) => {
            notifySuccess(`${card.maskedReference} ${pastTense(action.action)}.`);
            setSelected(card);
            query.refetch();
          }}
          onError={notifyError}
        />
      )}
    </div>
  );
};

interface IssueCardDialogProps {
  open: boolean;
  defaultSiteCode: string;
  onClose: () => void;
  onSaved: (card: FuelCard) => void;
}

const IssueCardDialog = ({ open, defaultSiteCode, onClose, onSaved }: IssueCardDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      siteCode: defaultSiteCode,
      maskedReference: demoMaskedReference(),
      provider: 'GOIL GHANA',
      vehicleId: '',
      driverId: '',
      issuedOn: todayIsoDate(),
      expiresOn: '',
      dailyLimit: '',
      monthlyLimit: '',
      perTransactionLimit: '',
      notes: '',
    },
    schema: {
      siteCode: required('Site'),
      maskedReference: compose(required('Masked reference'), maskedCard),
      provider: compose(required('Provider'), maxLength('Provider', 160)),
      dailyLimit: positiveNumber('Daily limit'),
      monthlyLimit: positiveNumber('Monthly limit'),
      perTransactionLimit: positiveNumber('Transaction limit'),
      notes: maxLength('Notes', 500),
    },
    crossFieldValidate: (values) =>
      values.expiresOn && values.issuedOn && values.expiresOn < values.issuedOn
        ? { expiresOn: 'Expiry cannot be before the issue date.' }
        : {},
    onSubmit: async (values) => {
      const card = await fuelCardsApi.issue({
        siteCode: values.siteCode.trim().toUpperCase(),
        maskedReference: values.maskedReference.trim(),
        provider: values.provider.trim(),
        vehicleId: values.vehicleId || null,
        driverId: values.driverId || null,
        issuedOn: values.issuedOn || null,
        expiresOn: values.expiresOn || null,
        dailyLimit: asNumber(values.dailyLimit),
        monthlyLimit: asNumber(values.monthlyLimit),
        perTransactionLimit: asNumber(values.perTransactionLimit),
        notes: values.notes.trim() || null,
      });
      onSaved(card);
      onClose();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Issue a fuel card"
      description="Creates a card register row from a safe masked reference. Never type a full card number here."
      submitLabel="Issue card"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="lg"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Alert variant="info" title="Masked references only">
        Live card references come from the fuel-card provider feed, already masked. For this demo,
        use the generated value below, for example ****1234. Full card numbers are refused before
        they leave the browser.
      </Alert>

      <div className="grid gap-4 sm:grid-cols-2">
        <SiteSelect
          required
          value={form.values.siteCode}
          onChange={(value) => form.setValues({ siteCode: value, vehicleId: '', driverId: '' })}
          {...form.fieldProps('siteCode')}
        />
        <div>
          <TextInput
            label="Masked reference"
            required
            placeholder="****1234"
            value={form.values.maskedReference}
            onChange={(value) => form.setValue('maskedReference', value)}
            {...form.fieldProps('maskedReference')}
          />
          <Button
            type="button"
            size="sm"
            variant="ghost"
            className="mt-1"
            startIcon="refresh"
            onClick={() => form.setValue('maskedReference', demoMaskedReference())}
          >
            Generate demo reference
          </Button>
        </div>
        <SelectInput
          label="Provider"
          required
          value={form.values.provider}
          onChange={(value) => form.setValue('provider', value)}
          options={GHANA_FUEL_CARD_PROVIDERS}
          {...form.fieldProps('provider')}
        />
        <DateField
          label="Issued on"
          value={form.values.issuedOn}
          onChange={(value) => form.setValue('issuedOn', value)}
          {...form.fieldProps('issuedOn')}
        />
        <DateField
          label="Expires on"
          value={form.values.expiresOn}
          onChange={(value) => form.setValue('expiresOn', value)}
          {...form.fieldProps('expiresOn', 'Optional. An expired card is not usable.')}
        />
        <VehicleSelect
          siteCode={form.values.siteCode}
          value={form.values.vehicleId}
          onChange={(value) => form.setValue('vehicleId', value)}
          allowEmpty
          emptyLabel="No vehicle"
          {...form.fieldProps('vehicleId', 'Optional at issue. Assign later if needed.')}
        />
        <DriverSelect
          siteCode={form.values.siteCode}
          value={form.values.driverId}
          onChange={(value) => form.setValue('driverId', value)}
          allowEmpty
          emptyLabel="No driver"
          {...form.fieldProps('driverId', 'Optional at issue.')}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <NumberInput
          label="Transaction limit"
          step={0.01}
          value={form.values.perTransactionLimit}
          onChange={(value) => form.setValue('perTransactionLimit', value)}
          {...form.fieldProps('perTransactionLimit', 'Blank means the site policy applies.')}
        />
        <NumberInput
          label="Daily limit"
          step={0.01}
          value={form.values.dailyLimit}
          onChange={(value) => form.setValue('dailyLimit', value)}
          {...form.fieldProps('dailyLimit', 'Blank means the site policy applies.')}
        />
        <NumberInput
          label="Monthly limit"
          step={0.01}
          value={form.values.monthlyLimit}
          onChange={(value) => form.setValue('monthlyLimit', value)}
          {...form.fieldProps('monthlyLimit', 'Blank means the site policy applies.')}
        />
      </div>

      <TextAreaInput
        label="Notes"
        rows={3}
        value={form.values.notes}
        onChange={(value) => form.setValue('notes', value)}
        {...form.fieldProps('notes')}
      />
    </FormDialog>
  );
};

interface CardActionDialogProps {
  open: boolean;
  card: FuelCard;
  action: CardAction;
  onClose: () => void;
  onSaved: (card: FuelCard) => void;
  onError: (error: unknown) => void;
}

const CardActionDialog = ({ open, card, action, onClose, onSaved, onError }: CardActionDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      vehicleId: card.vehicleId ?? '',
      driverId: card.driverId ?? '',
      reason: '',
    },
    schema: {
      reason:
        action === 'suspend' || action === 'cancel'
          ? compose(required('Reason'), maxLength('Reason', 500))
          : maxLength('Reason', 500),
    },
    onSubmit: async (values) => {
      try {
        const saved = await fuelCardsApi.transition(card.id, action, {
          vehicleId: action === 'assign' ? values.vehicleId || null : null,
          driverId: action === 'assign' ? values.driverId || null : null,
          reason: values.reason.trim() || null,
        });
        onSaved(saved);
        onClose();
      } catch (error) {
        onError(error);
        throw error;
      }
    },
  });

  const destructive = action === 'cancel' || action === 'suspend';

  return (
    <FormDialog
      open={open}
      title={`${humanise(action)} ${card.maskedReference}`}
      description={actionDescription(action)}
      submitLabel={actionLabel(action)}
      submitting={form.submitting}
      formError={form.formError}
      destructive={destructive}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      {action === 'assign' ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <VehicleSelect
            siteCode={siteOf(card.siteCode)}
            value={form.values.vehicleId}
            onChange={(value) => form.setValue('vehicleId', value)}
            allowEmpty
            emptyLabel="No vehicle"
            {...form.fieldProps('vehicleId')}
          />
          <DriverSelect
            siteCode={siteOf(card.siteCode)}
            value={form.values.driverId}
            onChange={(value) => form.setValue('driverId', value)}
            allowEmpty
            emptyLabel="No driver"
            {...form.fieldProps('driverId')}
          />
        </div>
      ) : action === 'reinstate' ? (
        <Alert variant="info" title="The card will become active again">
          Reinstatement clears the suspension reason. Historic transactions remain tied to this same
          masked card row.
        </Alert>
      ) : (
        <TextAreaInput
          label="Reason"
          required
          rows={4}
          autoFocus
          value={form.values.reason}
          onChange={(value) => form.setValue('reason', value)}
          {...form.fieldProps('reason')}
        />
      )}
    </FormDialog>
  );
};

const actionDescription = (action: CardAction): string =>
  ({
    assign: 'Updates the vehicle and driver assignment used by reconciliation.',
    suspend: 'Temporarily blocks the card. Reconciliation treats suspended cards as not usable.',
    reinstate: 'Returns a suspended card to active use.',
    cancel: 'Cancels the card permanently. The row stays for audit and historic transaction matching.',
  })[action];

const actionLabel = (action: CardAction): string =>
  ({
    assign: 'Save assignment',
    suspend: 'Suspend card',
    reinstate: 'Reinstate card',
    cancel: 'Cancel card',
  })[action];

const pastTense = (action: CardAction): string =>
  ({
    assign: 'assigned',
    suspend: 'suspended',
    reinstate: 'reinstated',
    cancel: 'cancelled',
  })[action];

export default FuelCardsPage;
