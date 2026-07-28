import { Link as RouterLink } from 'react-router';
import { Box, Divider, Drawer, IconButton, Link, Stack, Typography } from '@mui/material';
import { humanise } from 'modules/fleet/api/enums';
import { dashboardApi } from 'modules/fleet/api/fleetApi';
import DataState from 'shared/components/DataState';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import IconifyIcon from 'components/base/IconifyIcon';

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
 */
const DrilldownDrawer = ({ indicator, siteCode, onClose }: DrilldownDrawerProps) => {
  const { data, loading, error, refetch } = useApiQuery(
    (signal) =>
      indicator ? dashboardApi.drilldown(indicator, { siteCode }, signal) : Promise.resolve([]),
    [indicator, siteCode],
  );

  return (
    <Drawer
      anchor="right"
      open={Boolean(indicator)}
      onClose={onClose}
      slotProps={{ paper: { sx: { width: { xs: 1, sm: 460 } } } }}
    >
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ px: 2.5, py: 2, bgcolor: 'primary.main' }}
      >
        <Box>
          <Typography variant="subtitle1" fontWeight={700} sx={{ color: 'common.white' }}>
            {humanise(indicator)}
          </Typography>
          <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.7)' }}>
            Source records behind this indicator
          </Typography>
        </Box>
        <IconButton onClick={onClose} sx={{ color: 'common.white' }} aria-label="Close">
          <IconifyIcon icon="material-symbols:close-rounded" />
        </IconButton>
      </Stack>
      <Divider />

      <Box sx={{ p: 2.5, overflowY: 'auto' }}>
        <DataState
          loading={loading}
          error={error}
          empty={(data ?? []).length === 0}
          emptyTitle="No records"
          emptyHint="Nothing currently contributes to this indicator in your site scope."
          onRetry={refetch}
        >
          <Stack divider={<Divider />} spacing={0}>
            {(data ?? []).map((row) => {
              const link = recordLink(row.resourceType, row.resourceId);
              return (
                <Box key={`${row.resourceType}-${row.resourceId}`} sx={{ py: 1.5 }}>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                    <Typography variant="caption" color="text.secondary">
                      {row.resourceType} · {row.siteCode}
                    </Typography>
                  </Stack>
                  <Typography variant="body2" fontWeight={600} sx={{ mt: 0.25 }}>
                    {row.summary}
                  </Typography>
                  {link ? (
                    <Link component={RouterLink} to={link} onClick={onClose} variant="caption">
                      Open record
                    </Link>
                  ) : (
                    <Typography variant="caption" color="text.disabled">
                      {row.resourceId}
                    </Typography>
                  )}
                </Box>
              );
            })}
          </Stack>
        </DataState>
      </Box>
    </Drawer>
  );
};

export default DrilldownDrawer;
