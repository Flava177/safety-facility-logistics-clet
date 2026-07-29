import { useMemo, useState } from 'react';
import type { AudienceGroup, RecipientZone } from 'modules/emergency/api/dto';
import { useSiteRecords } from 'modules/emergency/components/useSiteRecords';
import { CreateAudienceDialog, CreateZoneDialog } from 'modules/emergency/dialogs/recordDialogs';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import Icon from 'shared/components/Icon';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatCard from 'shared/components/StatCard';
import StatusChip from 'shared/components/StatusChip';
import Tabs from 'shared/components/Tabs';
import { TextInput } from 'shared/components/fields';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { emergencyPaths } from 'shared/layout/navigation';

/**
 * Who receives a broadcast: audience groups and the zones a broadcast can be narrowed to.
 *
 * Paired for the same reason templates and scenarios are — an activation chooses from both at once,
 * and the two answer one question between them. Neither holds a contact detail: an audience group
 * is a directory pointer and a count, a zone is a facilities-location pointer and a name.
 *
 * The recipient count is the load-bearing field on this screen. It is what the service fans out to
 * and the denominator every delivery and acknowledgement percentage is read against, and no
 * endpoint can correct one once it is created — so a group sized wrongly quietly distorts every
 * activation that ever uses it.
 */
const EmergencyAudiencesPage = () => {
  const { notifySuccess } = useNotifier();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [tab, setTab] = useState('audiences');
  const [search, setSearch] = useState('');
  const [creatingAudience, setCreatingAudience] = useState(false);
  const [creatingZone, setCreatingZone] = useState(false);

  const records = useSiteRecords(siteCode);
  const needle = search.trim().toLowerCase();

  const audiences = useMemo(
    () =>
      records.audiences.filter(
        (audience) =>
          !needle ||
          audience.name.toLowerCase().includes(needle) ||
          audience.groupCode.toLowerCase().includes(needle),
      ),
    [records.audiences, needle],
  );

  const zones = useMemo(
    () =>
      records.zones.filter(
        (zone) =>
          !needle ||
          zone.name.toLowerCase().includes(needle) ||
          zone.zoneCode.toLowerCase().includes(needle),
      ),
    [records.zones, needle],
  );

  const totalReach = records.audiences.reduce(
    (total, audience) => total + audience.recipientCount,
    0,
  );
  const emptyGroups = records.audiences.filter((audience) => audience.recipientCount === 0);

  const audienceColumns = useMemo<Column<AudienceGroup>[]>(
    () => [
      {
        key: 'group',
        header: 'Group',
        width: 280,
        cell: (row) => (
          <CellStack
            primary={`${row.groupCode} · ${row.name}`}
            secondary={row.directoryReference ?? 'No directory reference recorded'}
          />
        ),
      },
      {
        key: 'recipients',
        header: 'Recipients',
        width: 130,
        align: 'right',
        cell: (row) =>
          row.recipientCount === 0 ? (
            <span className="font-medium text-error-800">0</span>
          ) : (
            formatNumber(row.recipientCount)
          ),
      },
      {
        key: 'share',
        header: 'Share of site',
        width: 130,
        align: 'right',
        hideBelowLg: true,
        cell: (row) =>
          totalReach > 0 ? `${Math.round((100 * row.recipientCount) / totalReach)}%` : '—',
      },
      {
        key: 'lifecycle',
        header: 'Lifecycle',
        width: 120,
        hideBelowLg: true,
        cell: (row) => <StatusChip value={row.lifecycle} />,
      },
      {
        key: 'created',
        header: 'Created',
        width: 160,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.metadata.createdAt),
      },
    ],
    [totalReach],
  );

  const zoneColumns = useMemo<Column<RecipientZone>[]>(
    () => [
      {
        key: 'zone',
        header: 'Zone',
        width: 300,
        cell: (row) => (
          <CellStack
            primary={`${row.zoneCode} · ${row.name}`}
            secondary={row.locationReference ?? 'Not mapped to a facilities location'}
          />
        ),
      },
      {
        key: 'lifecycle',
        header: 'Lifecycle',
        width: 130,
        cell: (row) => <StatusChip value={row.lifecycle} />,
      },
      {
        key: 'createdBy',
        header: 'Created by',
        width: 180,
        hideBelowLg: true,
        cell: (row) => row.metadata.createdBy,
      },
      {
        key: 'created',
        header: 'Created',
        width: 170,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.metadata.createdAt),
      },
    ],
    [],
  );

  return (
    <div>
      <PageHeader
        title="Audiences and zones"
        subtitle="Who a broadcast reaches, and where it can be narrowed to."
        crumbs={[
          { label: 'Emergency', to: emergencyPaths.dashboard },
          { label: 'Audiences and zones' },
        ]}
        actions={
          <>
            <Button variant="primary" startIcon="plus" onClick={() => setCreatingAudience(true)}>
              Create audience group
            </Button>
            <Button variant="outline" startIcon="plus" onClick={() => setCreatingZone(true)}>
              Create zone
            </Button>
            <Button variant="outline" startIcon="refresh" onClick={records.refetch}>
              Refresh
            </Button>
          </>
        }
      />

      <div className="mb-5 grid gap-4 sm:grid-cols-3">
        <StatCard
          label="Recipients across all groups"
          value={formatNumber(totalReach)}
          icon="users"
          caption="What a broadcast to every group would reach"
        />
        <StatCard
          label="Audience groups"
          value={formatNumber(records.audiences.length)}
          icon="clipboard"
          tone={emptyGroups.length > 0 ? 'caution' : 'neutral'}
          caption={
            emptyGroups.length > 0
              ? `${emptyGroups.length} sized at zero recipients`
              : 'All sized above zero'
          }
        />
        <StatCard
          label="Recipient zones"
          value={formatNumber(records.zones.length)}
          icon="map-pin"
          caption="Places a broadcast can be narrowed to"
        />
      </div>

      {emptyGroups.length > 0 && (
        <Alert
          variant="warning"
          title={`${emptyGroups.length} audience group${emptyGroups.length === 1 ? ' is' : 's are'} sized at zero`}
          className="mb-5"
        >
          A group with no recipients sends to nobody and still reports a successful broadcast — the
          channel record shows a target of zero, which is not the same as a failure and reads
          exactly like a clean send.{' '}
          {emptyGroups.map((group) => group.name).join(', ')}. The count cannot be corrected through
          any endpoint; create a replacement group with the right size.
        </Alert>
      )}

      <div className="mb-5">
        <SectionCard>
          <div className="grid gap-4 sm:grid-cols-2 lg:max-w-2xl">
            <SiteSelect value={siteCode} onChange={setSiteCode} required />
            <TextInput
              label="Search"
              value={search}
              onChange={setSearch}
              placeholder="Code or name"
              helperText="Filters the loaded records."
            />
          </div>
        </SectionCard>
      </div>

      <SectionCard flush>
        <div className="px-5 pt-4">
          <Tabs
            value={tab}
            onChange={setTab}
            items={[
              { value: 'audiences', label: 'Audience groups', count: records.audiences.length },
              { value: 'zones', label: 'Recipient zones', count: records.zones.length },
            ]}
          />
        </div>

        <DataState loading={records.initialising} minHeight={300}>
          {tab === 'audiences' ? (
            <DataTable
              rows={audiences}
              columns={audienceColumns}
              getRowId={(row) => row.id}
              loading={records.loading}
              caption="Audience groups at this site, with their recipient count, share of the site total, lifecycle and creation time."
              emptyMessage="No audience group matches this search."
            />
          ) : (
            <DataTable
              rows={zones}
              columns={zoneColumns}
              getRowId={(row) => row.id}
              loading={records.loading}
              caption="Recipient zones at this site, with their facilities location reference, lifecycle, creator and creation time."
              emptyMessage="No zone matches this search."
            />
          )}
        </DataState>

        <div className="flex items-start gap-1.5 px-5 pt-2 pb-4 text-theme-xs text-gray-600">
          <Icon name="info" size={13} className="mt-0.5 shrink-0 text-teal-700" />
          <span>
            {tab === 'audiences'
              ? 'Contact detail stays in the directory and never reaches this dashboard. The service exposes creation and reads only, so a count cannot be amended after the fact.'
              : `Naming a zone on an activation records ${humanise('ACCESS_CONTROL').toLowerCase()} lockdown and CCTV preservation context against it. SFL never actuates certified life-safety hardware.`}
          </span>
        </div>
      </SectionCard>

      {creatingAudience && (
        <CreateAudienceDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setCreatingAudience(false)}
          onSaved={(audience) => {
            notifySuccess(
              `${audience.groupCode} created.`,
              `${formatNumber(audience.recipientCount)} recipients. The count cannot be changed later.`,
            );
            records.refetch();
          }}
        />
      )}

      {creatingZone && (
        <CreateZoneDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setCreatingZone(false)}
          onSaved={(zone) => {
            notifySuccess(`${zone.zoneCode} created.`);
            records.refetch();
          }}
        />
      )}
    </div>
  );
};

export default EmergencyAudiencesPage;
