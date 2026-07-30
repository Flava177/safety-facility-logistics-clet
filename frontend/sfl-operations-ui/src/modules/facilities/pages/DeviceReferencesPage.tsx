import { useState } from 'react';
import Alert from 'shared/components/Alert';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import Select from 'shared/components/Select';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import type { DeviceReference } from '../api/dto';
import { deviceReferenceTypes } from '../api/enums';
import type { DeviceReferenceType } from '../api/enums';
import { listDeviceReferences } from '../api/facilitiesApi';
import { humaniseCode, orDash, relativeTime } from '../components/facilitiesFormat';

/**
 * Device references — the identity and location of devices vendor systems operate.
 *
 * S152 does not run cameras, readers or panels; it owns where each one is, so that a CCTV event, an
 * access denial or a fire alarm can be placed in a space and a zone without every consuming system
 * inventing its own device registry.
 *
 * The reported time is the *vendor's* observation, not our receipt, which is why "last reported" can
 * be old on a device the feed is talking to constantly.
 */
const DeviceReferencesPage = () => {
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [type, setType] = useState<string>('');

  const { data, loading, error, refetch } = useApiQuery(
    (signal) =>
      listDeviceReferences(
        {
          siteCode: siteCode || undefined,
          type: (type as DeviceReferenceType) || undefined,
        },
        signal,
      ),
    [siteCode, type],
  );

  const columns: Column<DeviceReference>[] = [
    {
      key: 'deviceCode',
      header: 'Code',
      width: 150,
      cell: (device) => <span className="font-medium text-gray-900">{device.deviceCode}</span>,
    },
    { key: 'name', header: 'Device', cell: (device) => device.name },
    {
      key: 'type',
      header: 'Type',
      hideBelowLg: true,
      cell: (device) => humaniseCode(device.type),
    },
    {
      key: 'vendor',
      header: 'Vendor',
      width: 150,
      hideBelowLg: true,
      cell: (device) => <span className="text-gray-600">{orDash(device.vendor)}</span>,
    },
    {
      key: 'status',
      header: 'Reported status',
      width: 150,
      cell: (device) => (
        <StatusChip
          value={device.status}
          tone={
            device.status === 'ONLINE'
              ? 'ready'
              : device.status === 'OFFLINE'
                ? 'blocked'
                : device.status === 'DEGRADED'
                  ? 'caution'
                  : 'neutral'
          }
        />
      ),
    },
    {
      key: 'reported',
      header: 'Last reported',
      width: 160,
      align: 'right',
      cell: (device) => (
        <span className="text-gray-600">{relativeTime(device.statusReportedAt)}</span>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Device references"
        subtitle="Where each vendor-operated device sits on this estate"
      />

      <FilterBar>
        <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
        <Select
          value={type}
          onChange={setType}
          placeholder="Any device type"
          options={[
            { value: '', label: 'Any device type' },
            ...deviceReferenceTypes.map((value) => ({ value, label: humaniseCode(value) })),
          ]}
        />
      </FilterBar>

      <DataState
        loading={loading}
        error={error}
        empty={!data || data.length === 0}
        emptyTitle="No device references"
        emptyHint="Register one so CCTV, access and life-safety events can be placed in a space."
        onRetry={refetch}
      >
        {data && (
          <>
            {data.some((device) => device.status === 'UNKNOWN') && (
              <Alert variant="info" className="mb-4">
                Devices showing UNKNOWN have never been reported on by their vendor system. S152 holds
                the reference; the vendor feed supplies the status.
              </Alert>
            )}
            <DataTable
              rows={data}
              columns={columns}
              getRowId={(device) => device.id}
              caption="Device references"
            />
          </>
        )}
      </DataState>
    </>
  );
};

export default DeviceReferencesPage;
