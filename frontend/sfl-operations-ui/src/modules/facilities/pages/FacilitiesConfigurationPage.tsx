import { useState } from 'react';
import Alert from 'shared/components/Alert';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import type { ConfigurationValue } from '../api/dto';
import { describeKey } from '../api/configurationCatalogue';
import { listConfiguration, putConfiguration } from '../api/facilitiesApi';
import { canManageConfiguration } from '../api/workflow';
import RowActions, { EditRowAction } from '../components/RowActions';
import { formatDateTime } from '../components/facilitiesFormat';
import { EditConfigurationDialog } from '../dialogs/configurationDialogs';

/**
 * The runtime configuration the facilities rules are read from.
 *
 * <h2>What was wrong with this screen</h2>
 *
 * <p>It listed `facilities.readiness.staleness-threshold` in a monospace column and offered no way to
 * change it. That is a setting's *name*, not a description of one - an operator deciding whether
 * seven days is right for their centre could not get there from the string, and could not act on the
 * answer if they had. The label and the effect now lead; the key is kept as secondary text because
 * support reads it in a log line and has to find the row.
 *
 * <h2>Which value is in force</h2>
 *
 * <p>A site override shadows the default of the same key, and both are shown. "The staleness window
 * is seven days" and "it is seven days everywhere except Accra" are different facts and only one of
 * them is usually true, so the scope column distinguishes the override, the default it shadows, and
 * a default nothing has overridden.
 *
 * <p>Every threshold is read at evaluation time (NFR 23.8), so a value changed at 09:00 applies to
 * the 09:01 evaluation without a redeploy.
 */
const FacilitiesConfigurationPage = () => {
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [editing, setEditing] = useState<ConfigurationValue | null>(null);

  const { data, loading, error, refetch } = useApiQuery(
    (signal) => listConfiguration(siteCode || undefined, signal),
    [siteCode],
  );

  const mayManage = canManageConfiguration();

  /** A site override shadows the default of the same key; both are shown, the override first. */
  const overriddenKeys = new Set(
    (data ?? []).filter((value) => value.siteCode !== null).map((value) => value.key),
  );

  const columns: Column<ConfigurationValue>[] = [
    {
      key: 'setting',
      header: 'Setting',
      cell: (value) => {
        const entry = describeKey(value.key, value.description);
        return (
          <div className="min-w-0">
            <CellStack primary={entry.label} secondary={value.key} />
            <p className="mt-1 max-w-xl text-theme-xs text-gray-600">{entry.effect}</p>
          </div>
        );
      },
    },
    {
      key: 'value',
      header: 'In force',
      width: 150,
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
    {
      key: 'actions',
      header: '',
      width: 80,
      align: 'right',
      cell: (value) => (
        <RowActions>
          <EditRowAction
            state={mayManage ? { kind: 'allowed' } : { kind: 'hidden' }}
            onClick={() => setEditing(value)}
            label={`Change ${describeKey(value.key, value.description).label}`}
          />
        </RowActions>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Configuration"
        subtitle="The thresholds the facilities rules are evaluated against, and which value is in force"
      />

      <FilterBar>
        <SiteSelect
          value={siteCode}
          onChange={setSiteCode}
          allowEmpty
          emptyLabel="Platform defaults"
        />
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
            {!mayManage && (
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
            />
          </>
        )}
      </DataState>

      {editing && (
        <EditConfigurationDialog
          value={editing}
          siteCode={siteCode || defaultSite}
          onClose={() => setEditing(null)}
          onSubmit={async (key, request) => {
            const saved = await putConfiguration(key, request);
            setEditing(null);
            notify.notifySuccess(
              saved.siteCode
                ? `${key} is now ${saved.value} for ${saved.siteCode}, at v${saved.version}.`
                : `${key} is now ${saved.value} everywhere, at v${saved.version}.`,
            );
            refetch();
          }}
        />
      )}
    </>
  );
};

export default FacilitiesConfigurationPage;
