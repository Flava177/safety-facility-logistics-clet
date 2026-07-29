import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import type { NotificationActivation } from 'modules/emergency/api/dto';
import {
  ACTIVATION_MODES,
  ACTIVATION_STATUSES,
  OPERATOR_REACHABLE_STATUSES,
  PRIORITIES,
} from 'modules/emergency/api/enums';
import type { ActivationMode, ActivationStatus, Priority } from 'modules/emergency/api/enums';
import { ACTIVATION_WINDOW, activationsApi, emergencyReportsApi } from 'modules/emergency/api/emergencyApi';
import {
  activationLive,
  activationOpen,
  afterActionOutstanding,
  awaitingApproval,
} from 'modules/emergency/api/workflow';
import { ActivationStatusChip } from 'modules/emergency/components/EmergencyFields';
import { formatElapsed } from 'modules/emergency/components/emergencyFormat';
import { useSiteRecords } from 'modules/emergency/components/useSiteRecords';
import { ComposeActivationDialog } from 'modules/emergency/dialogs/activationDialogs';
import { humanise } from 'modules/fleet/api/enums';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import Icon from 'shared/components/Icon';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import WindowNotice from 'shared/components/WindowNotice';
import { EnumSelect, SelectInput, TextInput } from 'shared/components/fields';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { useClientWindow } from 'shared/hooks/useClientWindow';
import { emergencyPaths } from 'shared/layout/navigation';

const QUEUE_VIEWS = [
  { value: 'OPEN', label: 'Open' },
  { value: 'LIVE', label: 'Live now' },
  { value: 'AWAITING_APPROVAL', label: 'Awaiting approval' },
  { value: 'AFTER_ACTION_DUE', label: 'After-action due' },
];

/**
 * The activation register.
 *
 * `GET /activations` takes a site and a status and nothing else. Mode, priority, incident reference
 * and the four queue views are applied here over the returned window, and every one of those
 * controls says so — a filter that silently searches only what happened to be loaded is how an
 * operator concludes a broadcast was never sent.
 *
 * The status filter offers the whole enum, not only the statuses this dashboard can produce.
 * `ACTIVATING`, `PARTIALLY_DELIVERED`, `ESCALATED`, `FAILED`, `CANCELLED` and `REOPENED` are set by
 * provider callbacks, by the scheduled sweep, or by nothing at all — but a stored record can hold
 * them, and a filter that cannot find such a record is worse than one that returns nothing.
 */
const ActivationsPage = () => {
  const navigate = useNavigate();
  const { notifySuccess, notifyError } = useNotifier();
  const [searchParams] = useSearchParams();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [status, setStatus] = useState<ActivationStatus | ''>(
    (searchParams.get('status') as ActivationStatus | null) ?? '',
  );
  const [view, setView] = useState('OPEN');
  const [mode, setMode] = useState<ActivationMode | ''>('');
  const [priority, setPriority] = useState<Priority | ''>('');
  const [reference, setReference] = useState('');
  const [composing, setComposing] = useState(false);
  const [exporting, setExporting] = useState(false);

  const records = useSiteRecords(siteCode);

  const query = useApiQuery(
    (signal) => activationsApi.search({ siteCode, status: status || undefined }, signal),
    [siteCode, status],
  );

  const all = useMemo(() => query.data ?? [], [query.data]);

  const filtered = useMemo(() => {
    let rows = all;
    if (view === 'OPEN') {
      rows = rows.filter(activationOpen);
    } else if (view === 'LIVE') {
      rows = rows.filter(activationLive);
    } else if (view === 'AWAITING_APPROVAL') {
      rows = rows.filter(awaitingApproval);
    } else if (view === 'AFTER_ACTION_DUE') {
      rows = rows.filter(afterActionOutstanding);
    }
    if (mode) {
      rows = rows.filter((row) => row.mode === mode);
    }
    if (priority) {
      rows = rows.filter((row) => row.priority === priority);
    }
    if (reference.trim()) {
      const needle = reference.trim().toLowerCase();
      rows = rows.filter(
        (row) =>
          (row.incidentReference ?? '').toLowerCase().includes(needle) ||
          row.activationNumber.toLowerCase().includes(needle),
      );
    }
    // Most recently touched first: an emergency register is read from the top.
    return [...rows].sort((left, right) =>
      right.metadata.lastModifiedAt.localeCompare(left.metadata.lastModifiedAt),
    );
  }, [all, view, mode, priority, reference]);

  const windowed = useClientWindow(
    filtered,
    `${siteCode}|${status}|${view}|${mode}|${priority}|${reference}`,
    all.length,
    ACTIVATION_WINDOW,
  );

  const live = useMemo(() => all.filter(activationLive), [all]);
  const pending = useMemo(() => all.filter(awaitingApproval), [all]);
  const afterActionDue = useMemo(() => all.filter(afterActionOutstanding), [all]);

  const exportReport = async () => {
    setExporting(true);
    try {
      const fileName = await emergencyReportsApi.activations(siteCode);
      notifySuccess(
        `Downloaded ${fileName}.`,
        'The service exports the site’s activation register, not the filtered view.',
      );
    } catch (error) {
      notifyError(error);
    } finally {
      setExporting(false);
    }
  };

  const columns = useMemo<Column<NotificationActivation>[]>(
    () => [
      {
        key: 'activation',
        header: 'Activation',
        width: 280,
        cell: (row) => (
          <CellStack
            primary={`${row.activationNumber} · ${records.templateName(row.templateId)}`}
            secondary={
              row.incidentReference
                ? `Incident ${row.incidentReference}`
                : records.scenarioName(row.scenarioId)
            }
          />
        ),
      },
      {
        key: 'mode',
        header: 'Mode',
        width: 130,
        cell: (row) => (
          <div className="flex items-center gap-1.5">
            <StatusChip value={row.mode} />
            {afterActionOutstanding(row) && (
              <Icon
                name="alert-circle"
                size={14}
                className="shrink-0 text-error-800"
                aria-label="After-action approval outstanding"
              />
            )}
          </div>
        ),
      },
      {
        key: 'priority',
        header: 'Priority',
        width: 110,
        hideBelowLg: true,
        cell: (row) => <StatusChip value={row.priority} />,
      },
      {
        key: 'reach',
        header: 'Reach',
        width: 110,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatNumber(records.audienceReach(row.audienceGroupIds)),
      },
      {
        key: 'channels',
        header: 'Channels',
        width: 90,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatNumber(row.channels.length),
      },
      {
        key: 'sent',
        header: 'Time to send',
        width: 120,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatElapsed(row.fastLaneMillis),
      },
      {
        key: 'updated',
        header: 'Last change',
        width: 160,
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.metadata.lastModifiedAt),
      },
      {
        key: 'status',
        header: 'Status',
        width: 170,
        align: 'right',
        cell: (row) => <ActivationStatusChip status={row.status} />,
      },
    ],
    [records],
  );

  const filtersApplied = Boolean(status || mode || priority || reference || view !== 'OPEN');
  const derivedHint = 'Filters the loaded records.';

  return (
    <div>
      <PageHeader
        title="Activations"
        subtitle="Every broadcast this site has composed, sent, stood down or closed."
        crumbs={[{ label: 'Emergency', to: emergencyPaths.dashboard }, { label: 'Activations' }]}
        actions={
          <>
            <Button variant="primary" startIcon="plus" onClick={() => setComposing(true)}>
              Compose activation
            </Button>
            <Button
              variant="outline"
              startIcon="download"
              loading={exporting}
              onClick={exportReport}
            >
              Export CSV
            </Button>
            <Button variant="outline" startIcon="refresh" onClick={query.refetch}>
              Refresh
            </Button>
          </>
        }
      />

      <div className="mb-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Live now"
          value={formatNumber(live.length)}
          icon="siren"
          tone={live.length > 0 ? 'critical' : 'neutral'}
          caption="Broadcast out, not stood down"
          onClick={() => setView('LIVE')}
        />
        <StatCard
          label="Awaiting approval"
          value={formatNumber(pending.length)}
          icon="user-plus"
          tone={pending.length > 0 ? 'caution' : 'neutral'}
          caption="Submitted, nobody has decided"
          onClick={() => setView('AWAITING_APPROVAL')}
        />
        <StatCard
          label="After-action due"
          value={formatNumber(afterActionDue.length)}
          icon="zap"
          tone={afterActionDue.length > 0 ? 'critical' : 'neutral'}
          caption="Break-glass sends blocking closure"
          onClick={() => setView('AFTER_ACTION_DUE')}
        />
        <StatCard
          label="Open at this site"
          value={formatNumber(all.filter(activationOpen).length)}
          icon="megaphone"
          caption="Not closed, cancelled or rejected"
          onClick={() => setView('OPEN')}
        />
      </div>

      <SectionCard flush>
        <FilterBar
          onReset={() => {
            setStatus('');
            setView('OPEN');
            setMode('');
            setPriority('');
            setReference('');
          }}
          resetDisabled={!filtersApplied}
        >
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <EnumSelect
            label="Status"
            value={status}
            options={ACTIVATION_STATUSES}
            onChange={(value) => setStatus(value)}
            allowEmpty
            renderOptionLabel={(option) =>
              OPERATOR_REACHABLE_STATUSES.includes(option)
                ? humanise(option)
                : `${humanise(option)} (set elsewhere)`
            }
            helperText="Reaches the service."
          />
          <SelectInput
            label="View"
            value={view}
            onChange={setView}
            options={QUEUE_VIEWS}
            allowEmpty
            emptyLabel="Everything returned"
            helperText={derivedHint}
          />
          <EnumSelect
            label="Mode"
            value={mode}
            options={ACTIVATION_MODES}
            onChange={(value) => setMode(value)}
            allowEmpty
            helperText={derivedHint}
          />
          <EnumSelect
            label="Priority"
            value={priority}
            options={PRIORITIES}
            onChange={(value) => setPriority(value)}
            allowEmpty
            helperText={derivedHint}
          />
          <TextInput
            label="Reference"
            value={reference}
            onChange={setReference}
            placeholder="Activation or incident"
            helperText={derivedHint}
          />
        </FilterBar>
      </SectionCard>

      <div className="mt-5">
        <SectionCard flush>
          <DataState
            loading={query.initialising}
            error={query.error}
            onRetry={query.refetch}
            minHeight={300}
          >
            <DataTable
              rows={windowed.rows}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={(row) => navigate(emergencyPaths.activationDetail(row.id))}
              caption="Activations matching the current filters, with mode, priority, audience reach, channel count, time to send, last change and status."
              emptyMessage="No activation matches these filters."
              page={windowed.page}
              pageSize={windowed.pageSize}
              totalElements={windowed.total}
              onPageChange={windowed.setPage}
              onPageSizeChange={windowed.setPageSize}
            />
          </DataState>
        </SectionCard>

        <WindowNotice
          truncated={windowed.truncated}
          total={all.length}
          requestedSize={ACTIVATION_WINDOW}
          noun="activations"
          service="emergency notification service"
        />
      </div>

      {composing && (
        <ComposeActivationDialog
          open
          defaultSiteCode={siteCode}
          records={records}
          onClose={() => setComposing(false)}
          onSaved={(activation) => {
            notifySuccess(
              `${activation.activationNumber} created as a draft.`,
              'Nothing has been sent. Submit it for approval from its detail screen.',
            );
            query.refetch();
            navigate(emergencyPaths.activationDetail(activation.id));
          }}
        />
      )}
    </div>
  );
};

export default ActivationsPage;
