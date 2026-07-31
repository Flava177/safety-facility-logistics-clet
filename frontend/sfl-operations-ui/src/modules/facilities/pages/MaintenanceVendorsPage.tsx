import { useState } from 'react';
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
import type { MaintenanceVendor } from '../api/dto';
import { listVendors, registerVendor } from '../api/facilitiesApi';
import { canManageVendors } from '../api/workflow';
import RegisterVendorDialog from '../dialogs/RegisterVendorDialog';
import { formatDate, orDash } from '../components/facilitiesFormat';

/**
 * The vendor register.
 *
 * ## What this is not
 *
 * Not the procurement master. It holds enough to assign work, know the contracted response time and
 * see whether the contract has run out, and it carries `externalVendorId` so procurement's record of
 * the same company can be reconciled with it later. Anything more would be a second source of truth
 * for supplier data that nobody has agreed to maintain.
 *
 * ## Why expired vendors stay on the list
 *
 * `assignable` and `unassignableReason` come from the service, which refuses to assign work to a
 * vendor whose contract has lapsed. Hiding them would leave a supervisor wondering where a
 * contractor they use every week has gone; showing them with the reason answers it here, and a
 * contract renewed this morning is assignable again without anybody clearing a cache.
 */
const MaintenanceVendorsPage = () => {
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [registering, setRegistering] = useState(false);

  const vendors = useApiQuery(
    (signal) => listVendors(siteCode || undefined, signal),
    [siteCode],
  );

  const columns: Column<MaintenanceVendor>[] = [
    {
      key: 'vendorCode',
      header: 'Code',
      width: 140,
      cell: (vendor) => <span className="font-medium text-gray-900">{vendor.vendorCode}</span>,
    },
    { key: 'name', header: 'Vendor', cell: (vendor) => vendor.name },
    {
      key: 'specialisation',
      header: 'Specialisation',
      hideBelowLg: true,
      cell: (vendor) => orDash(vendor.specialisation),
    },
    {
      key: 'responseHours',
      header: 'Response',
      width: 130,
      cell: (vendor) =>
        vendor.responseHours ? (
          <span>{vendor.responseHours}h contracted</span>
        ) : (
          <span className="text-theme-xs text-gray-500">Not contracted</span>
        ),
    },
    {
      key: 'contractExpiresOn',
      header: 'Contract',
      width: 150,
      cell: (vendor) =>
        vendor.contractExpiresOn ? (
          formatDate(vendor.contractExpiresOn)
        ) : (
          <span className="text-theme-xs text-gray-500">No end date</span>
        ),
    },
    {
      key: 'assignable',
      header: 'Availability',
      width: 190,
      cell: (vendor) =>
        vendor.assignable ? (
          <StatusChip value="Can take work" tone="ready" />
        ) : (
          <div className="flex flex-col gap-0.5">
            <StatusChip value="Unavailable" tone="blocked" />
            <span className="text-theme-xs text-gray-500">
              {orDash(vendor.unassignableReason)}
            </span>
          </div>
        ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Vendors"
        subtitle="Contractors, their contracts and their contracted response times"
        crumbs={[{ label: 'Facilities', to: facilitiesPaths.dashboard }, { label: 'Vendors' }]}
        actions={
          canManageVendors() && (
            <Button variant="primary" onClick={() => setRegistering(true)}>
              Register a vendor
            </Button>
          )
        }
      />

      <FilterBar>
        <SiteSelect value={siteCode} onChange={setSiteCode} />
      </FilterBar>

      <DataState
        loading={vendors.loading}
        error={vendors.error}
        empty={vendors.data?.length === 0}
        emptyTitle="No vendors registered"
        emptyHint="Register a contractor to assign work to them. A contracted response time shortens the SLA on anything they take."
        onRetry={vendors.refetch}
      >
        <DataTable
          rows={vendors.data ?? []}
          columns={columns}
          getRowId={(vendor) => vendor.id}
        />
      </DataState>

      {registering && (
        <RegisterVendorDialog
          siteCode={siteCode}
          onClose={() => setRegistering(false)}
          onSubmit={async (request) => {
            const created = await registerVendor(request);
            setRegistering(false);
            notify.notifySuccess(`${created.name} registered.`);
            vendors.refetch();
          }}
        />
      )}
    </>
  );
};

export default MaintenanceVendorsPage;
