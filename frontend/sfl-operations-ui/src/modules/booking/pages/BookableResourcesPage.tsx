import { useState } from 'react';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import Select from 'shared/components/Select';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { humaniseCode } from 'modules/facilities/components/facilitiesFormat';
import { bookableResourcesApi } from '../api/bookingApi';
import type { BookableResource } from '../api/dto';
import { RESOURCE_CATEGORIES } from '../api/enums';
import type { ResourceCategory } from '../api/enums';
import { canManageResources } from '../api/workflow';
import ControlButton from '../components/ControlButton';
import RegisterResourceDialog from '../dialogs/RegisterResourceDialog';

/**
 * The bookable-resource register — SRS-SFL-S159-01.
 *
 * Projectors, furniture sets and everything else booked alongside a room. Separate from the S152
 * asset register and deliberately so: an asset is fixed plant whose condition feeds a space's
 * readiness, a resource is portable and its scarcity is the point.
 *
 * **Quantity is the column that matters.** One row holds forty chairs, and availability is arithmetic
 * against what is committed for a window. A quantity of exactly one is different in kind rather than
 * degree — it makes the resource exclusive, and exclusivity is enforced by the database's own
 * exclusion constraint rather than by that arithmetic. The register says which, because the number
 * alone does not.
 */
const BookableResourcesPage = () => {
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState(defaultSite);
  const [category, setCategory] = useState('');
  const [registering, setRegistering] = useState(false);

  const resources = useApiQuery(
    (signal) =>
      bookableResourcesApi.search(
        {
          siteCode: siteCode || undefined,
          category: (category as ResourceCategory) || undefined,
        },
        signal,
      ),
    [siteCode, category],
  );

  const rows = resources.data ?? [];

  const columns: Column<BookableResource>[] = [
    {
      key: 'name',
      header: 'Resource',
      width: 260,
      cell: (resource) => (
        <CellStack primary={resource.name} secondary={resource.resourceCode} />
      ),
    },
    {
      key: 'category',
      header: 'Category',
      width: 160,
      cell: (resource) => humaniseCode(resource.category),
    },
    {
      key: 'quantity',
      header: 'Quantity',
      align: 'right',
      width: 110,
      cell: (resource) => resource.quantity,
    },
    {
      key: 'exclusive',
      header: 'Contention',
      width: 170,
      cell: (resource) =>
        resource.exclusive ? (
          <span title="Enforced by the database's exclusion constraint, not by arithmetic.">
            <StatusChip value="EXCLUSIVE" tone="accent" />
          </span>
        ) : (
          <span className="text-theme-xs text-gray-500">Shared pool</span>
        ),
    },
    {
      key: 'requiresSetup',
      header: 'Turnaround',
      width: 150,
      hideBelowLg: true,
      cell: (resource) =>
        resource.requiresSetup ? (
          <StatusChip value="SETUP" label="Raises a task" tone="caution" />
        ) : (
          <span className="text-gray-400">—</span>
        ),
    },
    {
      key: 'site',
      header: 'Site',
      width: 110,
      hideBelowLg: true,
      cell: (resource) => resource.siteCode,
    },
    {
      key: 'lifecycle',
      header: 'Lifecycle',
      width: 130,
      align: 'right',
      cell: (resource) => <StatusChip value={resource.lifecycleStatus} />,
    },
  ];

  return (
    <>
      <PageHeader
        title="Bookable resources"
        subtitle="Projectors, furniture and everything else booked alongside a room"
        actions={
          <ControlButton
            state={canManageResources()}
            startIcon="plus"
            onClick={() => setRegistering(true)}
          >
            Register a resource
          </ControlButton>
        }
      />

      <FilterBar onReset={() => setCategory('')} resetDisabled={!category}>
        <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
        <Select
          value={category}
          onChange={setCategory}
          placeholder="Any category"
          options={[
            { value: '', label: 'Any category' },
            ...RESOURCE_CATEGORIES.map((value) => ({ value, label: humaniseCode(value) })),
          ]}
        />
      </FilterBar>

      <DataState
        loading={resources.loading}
        error={resources.error}
        empty={rows.length === 0}
        emptyTitle="No bookable resources"
        emptyHint="Nothing is registered for this site and category yet."
        onRetry={resources.refetch}
      >
        <DataTable
          rows={rows}
          columns={columns}
          getRowId={(resource) => resource.id}
          caption="Bookable resources"
        />
      </DataState>

      {registering && (
        <RegisterResourceDialog
          onClose={() => setRegistering(false)}
          onSubmit={async (body) => {
            const created = await bookableResourcesApi.register(body);
            setRegistering(false);
            notify.notifySuccess(`${created.resourceCode} registered.`);
            resources.refetch();
          }}
        />
      )}
    </>
  );
};

export default BookableResourcesPage;
