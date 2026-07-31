import { useState } from 'react';
import { useNavigate } from 'react-router';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import Select from 'shared/components/Select';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { facilitiesPaths } from 'shared/layout/navigation';
import type { Space } from '../api/dto';
import { readinessStatuses, spaceTypes } from '../api/enums';
import type { LocationReadinessStatus, SpaceType } from '../api/enums';
import { searchSpaces } from '../api/facilitiesApi';
import {
  humaniseCode,
  orDash,
  readinessTone,
  relativeTime,
} from '../components/facilitiesFormat';

/**
 * The space register.
 *
 * The most-visited screen in the module, because a space is what every other IFIMP system points at:
 * S153 raises faults against one, S159 will book one, S162a zones contain one. It is therefore a
 * search rather than a list — an estate of any size is not browsable — and it leads with readiness,
 * which is the column an operator is actually scanning for.
 */
const SpaceRegisterPage = () => {
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [spaceType, setSpaceType] = useState<string>('');
  const [readiness, setReadiness] = useState<string>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);

  const { data, loading, error, refetch } = useApiQuery(
    (signal) =>
      searchSpaces(
        {
          siteCode: siteCode || undefined,
          spaceType: (spaceType as SpaceType) || undefined,
          readinessStatus: (readiness as LocationReadinessStatus) || undefined,
          page,
          size,
        },
        signal,
      ),
    [siteCode, spaceType, readiness, page, size],
  );

  /** Any filter change returns to the first page — page 4 of a new filter is meaningless. */
  const changeFilter = (apply: () => void) => {
    apply();
    setPage(0);
  };

  const columns: Column<Space>[] = [
    {
      key: 'roomCode',
      header: 'Code',
      width: 140,
      cell: (space) => <span className="font-medium text-gray-900">{space.roomCode}</span>,
    },
    { key: 'name', header: 'Name', cell: (space) => space.name },
    {
      key: 'spaceType',
      header: 'Type',
      hideBelowLg: true,
      cell: (space) => humaniseCode(space.spaceType),
    },
    {
      key: 'capacity',
      header: 'Capacity',
      align: 'right',
      width: 100,
      hideBelowLg: true,
      cell: (space) => orDash(space.capacity),
    },
    {
      key: 'readiness',
      header: 'Readiness',
      width: 130,
      cell: (space) => (
        <StatusChip value={space.readinessStatus} tone={readinessTone(space.readinessStatus)} />
      ),
    },
    {
      key: 'assessed',
      header: 'Assessed',
      width: 140,
      hideBelowLg: true,
      cell: (space) => (
        <span className="text-gray-600">{relativeTime(space.readinessUpdatedAt)}</span>
      ),
    },
    {
      key: 'availability',
      header: 'Available for',
      align: 'right',
      width: 190,
      cell: (space) => (
        <div className="flex flex-wrap justify-end gap-1">
          {/*
            Both flags are derived by the service. Showing "capable but not available" as two
            different things is the point: a hall can be examination-capable and still unusable,
            and an operator planning an examination needs to see which.
          */}
          {space.bookable && (
            <StatusChip
              value="BOOKING"
              label="Booking"
              tone={space.availableForBooking ? 'ready' : 'blocked'}
            />
          )}
          {space.examinationCapable && (
            <StatusChip
              value="EXAM"
              label="Exam"
              tone={space.availableForExamination ? 'ready' : 'blocked'}
            />
          )}
          {space.readinessLocked && <StatusChip value="LOCKED" tone="accent" label="Locked" />}
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Spaces"
        subtitle="Rooms, halls and courtrooms, with the readiness of each"
      />

      <FilterBar>
        <SiteSelect value={siteCode} onChange={(v) => changeFilter(() => setSiteCode(v))} allowEmpty emptyLabel="All sites" />
        <Select
          value={spaceType}
          onChange={(v) => changeFilter(() => setSpaceType(v))}
          placeholder="Any space type"
          options={[
            { value: '', label: 'Any space type' },
            ...spaceTypes.map((type) => ({ value: type, label: humaniseCode(type) })),
          ]}
        />
        <Select
          value={readiness}
          onChange={(v) => changeFilter(() => setReadiness(v))}
          placeholder="Any readiness"
          options={[
            { value: '', label: 'Any readiness' },
            ...readinessStatuses.map((status) => ({
              value: status,
              label: humaniseCode(status),
            })),
          ]}
        />
      </FilterBar>

      <DataState
        loading={loading}
        error={error}
        empty={!data || data.items.length === 0}
        emptyTitle="No spaces match these filters"
        emptyHint="Widen the site or clear the type and readiness filters."
        onRetry={refetch}
      >
        {data && (
          <DataTable
            rows={data.items}
            columns={columns}
            getRowId={(space) => space.id}
            onRowClick={(space) => navigate(facilitiesPaths.spaceDetail(space.id))}
            page={data.page}
            pageSize={data.size}
            totalElements={data.totalElements}
            onPageChange={setPage}
            onPageSizeChange={(next) => changeFilter(() => setSize(next))}
            caption="Spaces"
          />
        )}
      </DataState>
    </>
  );
};

export default SpaceRegisterPage;
