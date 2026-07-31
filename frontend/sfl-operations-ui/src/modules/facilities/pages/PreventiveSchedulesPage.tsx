import { useState } from 'react';
import { useNavigate } from 'react-router';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { PreventiveSchedule } from '../api/dto';
import { createSchedule, listSchedules, runGeneration } from '../api/facilitiesApi';
import { canManageSchedules } from '../api/workflow';
import CreateScheduleDialog from '../dialogs/CreateScheduleDialog';
import { formatDate, humaniseCode } from '../components/facilitiesFormat';

/**
 * Preventive maintenance schedules.
 *
 * ## The loop this screen closes
 *
 * S152 has carried `serviceIntervalDays` and `lastServicedOn` on every asset since it shipped, and
 * its dashboard has counted what is overdue from them. Nothing acted on either: the interval could
 * be set at registration and then only watched. A schedule raises the work, and closing that work
 * writes the service date back to the asset.
 *
 * ## Why "due for generation" is the service's answer and not a date comparison
 *
 * `dueForGeneration` accounts for something a client cannot see: whether this cycle has *already*
 * been generated for. A schedule inside its lead-time window that has already raised its work order
 * is not due, and a screen comparing `generateOn` to today would say it was — then a supervisor
 * would press Generate, nothing would happen, and they would reasonably conclude the button was
 * broken.
 */
const PreventiveSchedulesPage = () => {
  const navigate = useNavigate();
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [creating, setCreating] = useState(false);

  const schedules = useApiQuery(
    (signal) => listSchedules({ siteCode: siteCode || undefined }, signal),
    [siteCode],
  );

  const dueNow = (schedules.data ?? []).filter((schedule) => schedule.dueForGeneration).length;

  const generate = async () => {
    try {
      const run = await runGeneration();
      notify.notifySuccess(
        run.workOrdersRaised > 0
          ? `${run.workOrdersRaised} work order(s) raised for ${formatDate(run.generatedFor)}.`
          : 'Nothing was due. Every schedule inside its window has already generated.',
      );
      schedules.refetch();
    } catch (cause) {
      notify.notifyError(cause);
    }
  };

  const columns: Column<PreventiveSchedule>[] = [
    {
      key: 'scheduleCode',
      header: 'Schedule',
      width: 160,
      cell: (schedule) => (
        <span className="font-medium text-gray-900">{schedule.scheduleCode}</span>
      ),
    },
    { key: 'name', header: 'What it covers', cell: (schedule) => schedule.name },
    {
      key: 'intervalDays',
      header: 'Every',
      width: 110,
      hideBelowLg: true,
      cell: (schedule) => `${schedule.intervalDays} days`,
    },
    {
      key: 'nextDueOn',
      header: 'Next due',
      width: 140,
      cell: (schedule) => formatDate(schedule.nextDueOn),
    },
    {
      key: 'generateOn',
      header: 'Raises on',
      width: 140,
      cell: (schedule) => (
        <div className="flex flex-col gap-0.5">
          <span>{formatDate(schedule.generateOn)}</span>
          <span className="text-theme-xs text-gray-500">
            {schedule.leadTimeDays} days ahead
          </span>
        </div>
      ),
    },
    {
      key: 'dueForGeneration',
      header: 'State',
      width: 150,
      cell: (schedule) =>
        schedule.dueForGeneration ? (
          <StatusChip value="Due now" tone="caution" />
        ) : schedule.lifecycleStatus === 'ACTIVE' ? (
          <StatusChip value="Scheduled" tone="neutral" />
        ) : (
          <StatusChip value={humaniseCode(schedule.lifecycleStatus)} tone="neutral" />
        ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Preventive schedules"
        subtitle="Planned servicing, and what it has raised"
        crumbs={[
          { label: 'Facilities', to: facilitiesPaths.dashboard },
          { label: 'Preventive schedules' },
        ]}
        actions={
          canManageSchedules() && (
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={generate}>
                Generate due work
              </Button>
              <Button variant="primary" onClick={() => setCreating(true)}>
                New schedule
              </Button>
            </div>
          )
        }
      />

      <FilterBar>
        <SiteSelect value={siteCode} onChange={setSiteCode} />
      </FilterBar>

      {dueNow > 0 && (
        <Alert variant="info" title={`${dueNow} schedule(s) are due to raise work`}>
          The scheduler does this hourly on its own. Generating by hand raises the same work and is
          safe to repeat — a schedule that has already generated for its current cycle produces
          nothing.
        </Alert>
      )}

      <DataState
        loading={schedules.loading}
        error={schedules.error}
        empty={schedules.data?.length === 0}
        emptyTitle="No preventive schedules"
        emptyHint="A schedule raises a work order ahead of each service date, and closing that work records the service against the asset."
        onRetry={schedules.refetch}
      >
        <DataTable
          rows={schedules.data ?? []}
          columns={columns}
          getRowId={(schedule) => schedule.id}
          onRowClick={(schedule) => navigate(facilitiesPaths.scheduleDetail(schedule.id))}
        />
      </DataState>

      {creating && (
        <CreateScheduleDialog
          siteCode={siteCode}
          onClose={() => setCreating(false)}
          onSubmit={async (request) => {
            const created = await createSchedule(request);
            setCreating(false);
            notify.notifySuccess(`Schedule ${created.scheduleCode} created.`);
            schedules.refetch();
          }}
        />
      )}
    </>
  );
};

export default PreventiveSchedulesPage;
