import { Box, ButtonBase, Stack, Typography } from '@mui/material';
import IconifyIcon from 'components/base/IconifyIcon';

type Tone = 'neutral' | 'good' | 'caution' | 'critical';

const toneStyles: Record<Tone, { color: string; bg: string }> = {
  neutral: { color: 'primary.main', bg: 'rgba(5, 27, 43, 0.06)' },
  good: { color: 'success.dark', bg: 'rgba(46, 125, 50, 0.08)' },
  caution: { color: 'secondary.dark', bg: 'rgba(184, 149, 13, 0.12)' },
  critical: { color: 'error.dark', bg: 'rgba(211, 47, 47, 0.09)' },
};

interface IndicatorTileProps {
  label: string;
  value: number | string;
  icon: string;
  tone?: Tone;
  caption?: string;
  /** Present only when the service exposes a drilldown for this indicator. */
  onDrilldown?: () => void;
}

/**
 * One dashboard indicator.
 *
 * A tile is only clickable when the service actually has a drilldown for it — the fleet service
 * implements four (`EXPIRED_COMPLIANCE`, `SERVICE_DUE`, `READINESS_BLOCKERS`,
 * `ASSIGNMENT_CONFLICTS`), so the rest deliberately do not look interactive.
 */
const IndicatorTile = ({
  label,
  value,
  icon,
  tone = 'neutral',
  caption,
  onDrilldown,
}: IndicatorTileProps) => {
  const styles = toneStyles[tone];

  const content = (
    <Stack
      direction="row"
      spacing={1.5}
      alignItems="center"
      sx={{
        p: 2,
        width: 1,
        height: 1,
        bgcolor: 'common.white',
        border: 1,
        borderColor: 'divider',
        borderRadius: 2,
        textAlign: 'left',
        transition: 'border-color 120ms ease',
        ...(onDrilldown && { '&:hover': { borderColor: 'secondary.main' } }),
      }}
    >
      <Box
        sx={{
          width: 40,
          height: 40,
          borderRadius: 1.5,
          flexShrink: 0,
          display: 'grid',
          placeItems: 'center',
          bgcolor: styles.bg,
          color: styles.color,
        }}
      >
        <IconifyIcon icon={icon} sx={{ fontSize: 20 }} />
      </Box>

      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Typography variant="h5" sx={{ fontSize: 24, lineHeight: 1.2, color: styles.color }}>
          {value}
        </Typography>
        <Typography variant="body2" fontWeight={600} noWrap>
          {label}
        </Typography>
        {caption && (
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
            {caption}
          </Typography>
        )}
      </Box>

      {onDrilldown && (
        <IconifyIcon
          icon="material-symbols:chevron-right-rounded"
          sx={{ fontSize: 20, color: 'text.disabled', flexShrink: 0 }}
        />
      )}
    </Stack>
  );

  if (!onDrilldown) {
    return content;
  }

  return (
    <ButtonBase
      onClick={onDrilldown}
      sx={{ width: 1, borderRadius: 2, textAlign: 'left', display: 'block' }}
      aria-label={`Show records behind ${label}`}
    >
      {content}
    </ButtonBase>
  );
};

export default IndicatorTile;
