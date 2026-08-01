import { useState } from 'react';
import { useNavigate } from 'react-router';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import FacetFilter from 'shared/components/FacetFilter';
import { SelectInput } from 'shared/components/fields';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { FacilityFault } from '../api/dto';
import type { FacilityFaultStatus } from '../api/enums';
import { faultPriorities, faultStatuses } from '../api/enums';
import { reportFault, searchFaults } from '../api/facilitiesApi';
import { canReportFaults } from '../api/workflow';
import ReportFaultDialog from '../dialogs/ReportFaultDialog';
import {
  faultStatusTone,
  formatDateTime,
  humaniseCode,
  orDash,
  priorityTone,
} from '../components/facilitiesFormat';

/**
 * The fault register — SRS-SFL-S153-01.
 *
 * <p>This is the screen the retired static page had and the dashboard did not, and it is the last
 * thing ADR 0006 gave up when that page went.
 *
 * <p>**Overdue is a column, not a computation.** The service returns `overdue` and `slaDueAt` on
 * every fault, decided by the same clock the escalation sweep uses. A browser working it out for
 * itself would disagree with the sweep whenever a workstation clock drifted, and the sweep is the
 * one that notifies people.
 *
 * <p>A requester sees only the faults they reported. That is enforced per record by the service, so
 * this screen needs no special case: it asks for the register and receives a shorter one.
 */
const FaultRegisterPage = () => {
  const navigate = useNavigate();
  const notify = useNotifier();

  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [status, setStatus] = useState<string[]>([]);
  const [priority, setPriority] = useState<string[]>([]);
  const [openOnly, setOpenOnly] = useState(true);
  const [reporting, setReporting] = useState(false);

  const faults = useApiQuery(
    (signal) =>
      searchFaults(
        {
          siteCode: siteCode || undefined,
          // One value narrows the fetch; the rest are applied below. `FaultQuery` takes a single
          // status, so a multi-select cannot be pushed down whole.
          status: (status[0] || undefined) as FacilityFaultStatus | undefined,
          openOnly: openOnly || undefined,
          limit: 100,
        },
        signal,
      ),
    [siteCode, status, openOnly],
  );

  /*
    Counts describe what the service returned, not the whole register. A count claiming to be the
    site total would be a promise this screen cannot keep: a requester's view is narrowed per record
    by `FacilityFaultService.requesterFilter`, so their totals are legitimately smaller.
  */
  const fetched = faults.data ?? [];
  const visible = fetched.filter(
    (fault) =>
      (status.length === 0 || status.includes(fault.status)) &&
      (priority.length === 0 || priority.includes(fault.priority)),
  );
  const tally = (pick: (fault: (typeof fetched)[number]) => string) =>
    fetched.reduce<Record<string, number>>((counts, fault) => {
      const key = pick(fault);
      counts[key] = (counts[key] ?? 0) + 1;
      return counts;
    }, {});
  const statusCounts = tally((fault) => fault.status);
  const priorityCounts = tally((fault) => fault.priority);

  const columns: Column<FacilityFault>[] = [
    {
      key: 'faultNumber',
      header: 'Fault',
      width: 170,
      cell: (fault) => <span className="font-medium text-gray-900">{fault.faultNumber}</span>,
    },
    { key: 'title', header: 'What is wrong', cell: (fault) => fault.title },
    {
      key: 'locationCode',
      header: 'Where',
      hideBelowLg: true,
      cell: (fault) => orDash(fault.locationCode),
    },
    {
      key: 'priority',
      header: 'Priority',
      width: 110,
      cell: (fault) => (
        <StatusChip value={humaniseCode(fault.priority)} tone={priorityTone(fault.priority)} />
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 160,
      cell: (fault) => (
        <StatusChip value={humaniseCode(fault.status)} tone={faultStatusTone(fault.status)} />
      ),
    },
    {
      key: 'slaDueAt',
      header: 'SLA',
      width: 190,
      cell: (fault) => {
        if (!fault.slaDueAt) {
          // Untriaged, or migrated from the pre-S153 system. Either way it has no deadline yet, and
          // saying so is more use than an empty cell — a fault with no SLA never escalates.
          return <span className="text-theme-xs text-gray-500">Not triaged</span>;
        }
        return fault.overdue ? (
          <span className="text-theme-xs font-medium text-error-600">
            Overdue{fault.escalationLevel > 0 ? ` · level ${fault.escalationLevel}` : ''}
          </span>
        ) : (
          <span className="text-theme-xs text-gray-600">{formatDateTime(fault.slaDueAt)}</span>
        );
      },
    },
  ];

  return (
    <>
      <PageHeader
        title="Faults"
        subtitle="Reported problems, what they are blocking, and what is late"
        crumbs={[{ label: 'Facilities', to: facilitiesPaths.dashboard }, { label: 'Faults' }]}
        actions={
          canReportFaults() && (
            <Button variant="primary" onClick={() => setReporting(true)}>
              Report a fault
            </Button>
          )
        }
      />

      <FilterBar
        onReset={() => {
          setStatus([]);
          setPriority([]);
        }}
        resetDisabled={status.length === 0 && priority.length === 0}
      >
        <SiteSelect value={siteCode} onChange={setSiteCode} />
        <FacetFilter
          label="Status"
          selected={status}
          onChange={setStatus}
          options={faultStatuses.map((value) => ({
            value,
            label: humaniseCode(value),
            count: statusCounts[value] ?? 0,
          }))}
        />
        <FacetFilter
          label="Priority"
          selected={priority}
          onChange={setPriority}
          options={faultPriorities.map((value) => ({
            value,
            label: humaniseCode(value),
            count: priorityCounts[value] ?? 0,
          }))}
        />
        <SelectInput
          label="Show"
          value={openOnly ? 'open' : 'all'}
          onChange={(value) => setOpenOnly(value === 'open')}
          options={[
            { value: 'open', label: 'Open only' },
            { value: 'all', label: 'Everything' },
          ]}
        />
      </FilterBar>

      <DataState
        loading={faults.loading}
        error={faults.error}
        empty={visible.length === 0}
        emptyTitle={openOnly ? 'Nothing is outstanding' : 'No faults reported'}
        emptyHint={
          // Same caution as the work-order queue: a requester sees only the faults they reported,
          // so a definite statement about the site would be wrong for them.
          openOnly
            ? 'Nothing outstanding is visible to you. Switch to Everything to include resolved and dismissed faults.'
            : 'No faults are visible to you. A requester sees only the ones they reported themselves.'
        }
        onRetry={faults.refetch}
      >
        <DataTable
          rows={visible}
          columns={columns}
          getRowId={(fault) => fault.id}
          onRowClick={(fault) => navigate(facilitiesPaths.faultDetail(fault.id))}
        />
      </DataState>

      {reporting && (
        <ReportFaultDialog
          siteCode={siteCode}
          onClose={() => setReporting(false)}
          onSubmit={async (request) => {
            const created = await reportFault(request);
            setReporting(false);
            notify.notifySuccess(`Fault ${created.faultNumber} reported.`);
            navigate(facilitiesPaths.faultDetail(created.id));
          }}
        />
      )}
    </>
  );
};

export default FaultRegisterPage;
