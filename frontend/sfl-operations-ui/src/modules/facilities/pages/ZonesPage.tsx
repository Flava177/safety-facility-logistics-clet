import { useState } from 'react';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import type { Zone, ZoneMember } from '../api/dto';
import { listZoneMembers, listZones } from '../api/facilitiesApi';
import { formatDateTime, orDash } from '../components/facilitiesFormat';

/**
 * Zones and what they cover.
 *
 * A zone is how the safety and emergency systems address the estate — S162a life-safety events
 * arrive per zone, S174 broadcasts target recipient zones — so "what is actually in this zone" is
 * the question the screen exists to answer. Selecting a zone loads its membership rather than
 * navigating away, because the comparison between zones is the common task.
 */
const ZonesPage = () => {
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [selected, setSelected] = useState<Zone | null>(null);

  const zones = useApiQuery((signal) => listZones(siteCode || undefined, signal), [siteCode]);
  const members = useApiQuery(
    (signal) => (selected ? listZoneMembers(selected.id, signal) : Promise.resolve([])),
    [selected?.id],
  );

  const zoneColumns: Column<Zone>[] = [
    {
      key: 'zoneCode',
      header: 'Code',
      width: 150,
      cell: (zone) => <span className="font-medium text-gray-900">{zone.zoneCode}</span>,
    },
    { key: 'name', header: 'Zone', cell: (zone) => zone.name },
    {
      key: 'purpose',
      header: 'Purpose',
      hideBelowLg: true,
      cell: (zone) => <span className="text-gray-600">{orDash(zone.purpose)}</span>,
    },
    {
      key: 'parent',
      header: 'Nested',
      width: 110,
      align: 'right',
      cell: (zone) =>
        zone.parentZoneId ? <StatusChip value="NESTED" label="Nested" tone="neutral" /> : null,
    },
  ];

  const memberColumns: Column<ZoneMember>[] = [
    {
      key: 'memberType',
      header: 'Type',
      width: 130,
      cell: (member) => <StatusChip value={member.memberType} tone="neutral" />,
    },
    {
      key: 'memberId',
      header: 'Record',
      cell: (member) => <span className="font-mono text-theme-xs">{member.memberId}</span>,
    },
    {
      key: 'addedBy',
      header: 'Added by',
      width: 160,
      hideBelowLg: true,
      cell: (member) => member.addedBy,
    },
    {
      key: 'addedAt',
      header: 'Added',
      width: 190,
      align: 'right',
      cell: (member) => (
        <span className="text-gray-600">{formatDateTime(member.addedAt)}</span>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Zones"
        subtitle="How safety, life-safety and emergency systems address this estate"
      />

      <FilterBar>
        <SiteSelect
          value={siteCode}
          onChange={(value) => {
            setSiteCode(value);
            setSelected(null);
          }}
          allowEmpty
          emptyLabel="All sites"
        />
      </FilterBar>

      <div className="space-y-5">
        <SectionCard title="Zones" subtitle="Select one to see what it covers">
          <DataState
            loading={zones.loading}
            error={zones.error}
            empty={!zones.data || zones.data.length === 0}
            emptyTitle="No zones configured"
            emptyHint="A zone is what an evacuation broadcast or a life-safety alarm resolves against."
            onRetry={zones.refetch}
          >
            {zones.data && (
              <DataTable
                rows={zones.data}
                columns={zoneColumns}
                getRowId={(zone) => zone.id}
                onRowClick={setSelected}
                caption="Zones"
                dense
              />
            )}
          </DataState>
        </SectionCard>

        {selected && (
          <SectionCard
            title={`What ${selected.zoneCode} covers`}
            subtitle={`${selected.name}${selected.purpose ? ` · ${selected.purpose}` : ''}`}
          >
            <DataState
              loading={members.loading}
              error={members.error}
              empty={!members.data || members.data.length === 0}
              emptyTitle="This zone is empty"
              emptyHint="A zone with no members resolves to nothing — an alarm against it would reach nobody."
              onRetry={members.refetch}
              minHeight={120}
            >
              {members.data && (
                <DataTable
                  rows={members.data}
                  columns={memberColumns}
                  getRowId={(member) => member.id}
                  dense
                />
              )}
            </DataState>
          </SectionCard>
        )}
      </div>
    </>
  );
};

export default ZonesPage;
