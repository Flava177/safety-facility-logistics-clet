import { useState } from 'react';
import Alert from 'shared/components/Alert';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import type { ConfigurationValue } from '../api/dto';
import { listConfiguration } from '../api/facilitiesApi';
import { canManageConfiguration } from '../api/workflow';
import { formatDateTime } from '../components/facilitiesFormat';

/**
 * The runtime configuration the S152 rules are read from.
 *
 * Every threshold here is read by the service at evaluation time — a value changed at 09:00 applies
 * to the 09:01 evaluation without a redeploy (NFR 23.8). The screen's job is to make clear *which*
 * value is in force: a site override and the platform default both appear, distinguished, because
 * "the staleness window is 7 days" and "the staleness window is 7 days everywhere except Accra" are
 * different facts and only one of them is usually true.
 */
const FacilitiesConfigurationPage = () => {
  const [siteCode, setSiteCode] = useState<string>(defaultSite);

  const { data, loading, error, refetch } = useApiQuery(
    (signal) => listConfiguration(siteCode || undefined, signal),
    [siteCode],
  );

  /** A site override shadows the default of the same key; both are shown, the override first. */
  const overriddenKeys = new Set(
    (data ?? []).filter((value) => value.siteCode !== null).map((value) => value.key),
  );

  const columns: Column<ConfigurationValue>[] = [
    {
      key: 'key',
      header: 'Key',
      cell: (value) => <span className="font-mono text-theme-xs text-gray-900">{value.key}</span>,
    },
    {
      key: 'value',
      header: 'Value',
      width: 140,
      cell: (value) => <span className="font-medium text-gray-900">{value.value}</span>,
    },
    {
      key: 'scope',
      header: 'Scope',
      width: 170,
      cell: (value) =>
        value.siteCode ? (
          <StatusChip value="OVERRIDE" label={`${value.siteCode} override`} tone="accent" />
        ) : overriddenKeys.has(value.key) ? (
          <StatusChip value="SHADOWED" label="Default (overridden)" tone="neutral" />
        ) : (
          <StatusChip value="DEFAULT" label="Platform default" tone="neutral" />
        ),
    },
    {
      key: 'version',
      header: 'Version',
      align: 'right',
      width: 90,
      cell: (value) => `v${value.version}`,
    },
    {
      key: 'updated',
      header: 'Last set',
      width: 200,
      align: 'right',
      hideBelowLg: true,
      cell: (value) => (
        <span className="text-gray-600">
          {formatDateTime(value.updatedAt)} by {value.updatedBy}
        </span>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Configuration"
        subtitle="The thresholds S152 evaluates against, and which value is in force"
      />

      <FilterBar>
        <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="Platform defaults" />
      </FilterBar>

      <DataState
        loading={loading}
        error={error}
        empty={!data || data.length === 0}
        emptyTitle="No configuration values"
        emptyHint="The service seeds its defaults on first migration; an empty list means something is wrong."
        onRetry={refetch}
      >
        {data && (
          <>
            {!canManageConfiguration() && (
              <Alert variant="info" className="mb-4">
                These values are read-only for you. Changing one needs the facilities configuration
                management permission.
              </Alert>
            )}
            <DataTable
              rows={data}
              columns={columns}
              getRowId={(value) => `${value.key}:${value.siteCode ?? 'default'}`}
              caption="Runtime configuration"
              dense
            />
          </>
        )}
      </DataState>
    </>
  );
};

export default FacilitiesConfigurationPage;
