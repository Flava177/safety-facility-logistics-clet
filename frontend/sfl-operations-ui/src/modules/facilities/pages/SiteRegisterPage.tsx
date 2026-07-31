import { useNavigate } from 'react-router';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import PageHeader from 'shared/components/PageHeader';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { Site } from '../api/dto';
import { listSites } from '../api/facilitiesApi';
import { formatDateTime, orDash } from '../components/facilitiesFormat';

/**
 * The sites this actor is scoped to.
 *
 * The service filters rather than refuses here — asking for "all sites" is a legitimate request that
 * should answer with the actor's own — so an operator scoped to one centre sees one row and no
 * error. Operating mode is the column that matters most: a centre in examination mode is running
 * under different rules, and that has to be visible without opening anything.
 */
const SiteRegisterPage = () => {
  const navigate = useNavigate();
  const { data, loading, error, refetch } = useApiQuery((signal) => listSites(signal), []);

  const columns: Column<Site>[] = [
    {
      key: 'siteCode',
      header: 'Code',
      width: 140,
      cell: (site) => <span className="font-medium text-gray-900">{site.siteCode}</span>,
    },
    { key: 'name', header: 'Site', cell: (site) => site.name },
    {
      key: 'description',
      header: 'Description',
      hideBelowLg: true,
      cell: (site) => <span className="text-gray-600">{orDash(site.description)}</span>,
    },
    {
      key: 'mode',
      header: 'Operating mode',
      width: 170,
      cell: (site) => (
        <StatusChip
          value={site.operatingMode}
          tone={site.operatingMode === 'EXAMINATION' ? 'accent' : 'neutral'}
        />
      ),
    },
    {
      key: 'lifecycle',
      header: 'Lifecycle',
      width: 120,
      cell: (site) => <StatusChip value={site.lifecycleStatus} />,
    },
    {
      key: 'changed',
      header: 'Last changed',
      width: 190,
      align: 'right',
      hideBelowLg: true,
      cell: (site) => (
        <span className="text-gray-600">{formatDateTime(site.metadata.lastModifiedAt)}</span>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Sites"
        subtitle="CLET centres, and the operating mode each is running under"
      />

      <DataState
        loading={loading}
        error={error}
        empty={!data || data.length === 0}
        emptyTitle="No sites in your scope"
        emptyHint="Your profile is scoped to sites that do not exist yet, or to none at all."
        onRetry={refetch}
      >
        {data && (
          <DataTable
            rows={data}
            columns={columns}
            getRowId={(site) => site.id}
            onRowClick={(site) => navigate(facilitiesPaths.siteDetail(site.id))}
            caption="Sites"
          />
        )}
      </DataState>
    </>
  );
};

export default SiteRegisterPage;
