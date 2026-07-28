import { ReactNode } from 'react';
import { Box, Button, Stack } from '@mui/material';

interface FilterBarProps {
  children: ReactNode;
  onReset?: () => void;
  /** Disables reset when nothing is filtered, so the control tells the truth about state. */
  resetDisabled?: boolean;
  trailing?: ReactNode;
}

/** Filter row for register screens: wraps on narrow viewports, stays on one line on desktop. */
const FilterBar = ({ children, onReset, resetDisabled, trailing }: FilterBarProps) => (
  <Stack
    direction={{ xs: 'column', lg: 'row' }}
    spacing={1.5}
    alignItems={{ xs: 'stretch', lg: 'center' }}
    sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}
  >
    <Box
      sx={{
        display: 'grid',
        gap: 1.5,
        flex: 1,
        gridTemplateColumns: {
          xs: 'repeat(1, minmax(0, 1fr))',
          sm: 'repeat(2, minmax(0, 1fr))',
          lg: 'repeat(4, minmax(0, 1fr))',
        },
      }}
    >
      {children}
    </Box>
    <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
      {trailing}
      {onReset && (
        <Button variant="soft" color="neutral" onClick={onReset} disabled={resetDisabled}>
          Reset
        </Button>
      )}
    </Stack>
  </Stack>
);

export default FilterBar;
