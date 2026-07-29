import { Link as RouterLink } from 'react-router';
import { DashboardDrilldownRow } from 'modules/fleet/api/dto';
import { humanise } from 'modules/fleet/api/enums';
import { dashboardApi } from 'modules/fleet/api/fleetApi';
import DataState from 'shared/components/DataState';
import Icon from 'shared/components/Icon';
import Modal, { ModalCloseButton } from 'shared/components/Modal';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';

interface DrilldownDrawerProps {
  indicator: string | null;
  siteCode?: string;
  onClose: () => void;
}

const recordLink = (resourceType: string, resourceId: string): string | null => {
  switch (resourceType) {
    case 'Vehicle':
      return fleetPaths.vehicleDetail(resourceId);
    case 'Trip':
      return fleetPaths.tripDetail(resourceId);
    default:
      return null;
  }
};

/**
 * The records behind a dashboard indicator.
 *
 * The service audits every drilldown and refuses rows the caller may not see
 * (`FLEET_DASHBOARD_RESTRICTED_DRILLDOWN`), so a refusal is surfaced as-is rather than shown as an
 * empty list.
 *
 * A centred panel rather than a side drawer: the kit has one dialog surface, and a record whose
 * resource type has no detail page shows its identifier instead of a dead link.
 */
const DrilldownDrawer = ({ indicator, siteCode, onClose }: DrilldownDrawerProps) => {
  const { data, loading, error, refetch } = useApiQuery(
    (signal) =>
      indicator
        ? dashboardApi.drilldown(indicator, { siteCode }, signal)
        : Promise.resolve<DashboardDrilldownRow[]>([]),
    [indicator, siteCode],
  );

  return (
    <Modal open={Boolean(indicator)} onClose={onClose} size="md" labelledBy="fleet-drilldown-title">
      <header className="flex items-start justify-between gap-4 border-b border-gray-200 px-6 py-4">
        <div className="min-w-0">
          <h2
            id="fleet-drilldown-title"
            className="text-theme-xl font-bold text-gray-900"
          >
            {humanise(indicator)}
          </h2>
          <p className="mt-1 text-theme-sm text-gray-600">Source records behind this indicator</p>
        </div>
        <ModalCloseButton onClose={onClose} />
      </header>

      <div className="custom-scrollbar max-h-[62vh] overflow-y-auto px-6 py-3">
        <DataState
          loading={loading}
          error={error}
          empty={(data ?? []).length === 0}
          emptyTitle="No records"
          emptyHint="Nothing currently contributes to this indicator in your site scope."
          onRetry={refetch}
          minHeight={200}
        >
          <ul className="divide-y divide-gray-200">
            {(data ?? []).map((row) => {
              const link = recordLink(row.resourceType, row.resourceId);
              return (
                <li key={`${row.resourceType}-${row.resourceId}`} className="py-3">
                  <p className="text-theme-xs text-gray-600">
                    {row.resourceType} · {row.siteCode}
                  </p>
                  <p className="mt-0.5 text-theme-sm font-semibold text-gray-900">{row.summary}</p>
                  {link ? (
                    // Teal is the dashboard's interactive colour, and the row is tall enough to clear
                    // the 24px target minimum of SC 2.5.8 without a larger type size.
                    <RouterLink
                      to={link}
                      onClick={onClose}
                      className="mt-1 inline-flex min-h-6 items-center gap-1 text-theme-xs font-medium text-teal-700 transition-colors hover:text-teal-800 hover:underline"
                    >
                      Open record
                      <Icon name="chevron-right" size={13} />
                    </RouterLink>
                  ) : (
                    <p className="mt-1 text-theme-xs text-gray-600">{row.resourceId}</p>
                  )}
                </li>
              );
            })}
          </ul>
        </DataState>
      </div>
    </Modal>
  );
};

export default DrilldownDrawer;
