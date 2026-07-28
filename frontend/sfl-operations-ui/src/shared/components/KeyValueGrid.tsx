import { ReactNode } from 'react';
import { Box, Stack, Typography } from '@mui/material';

export interface KeyValueItem {
  label: string;
  value: ReactNode;
  /** Marks a value the service masked, so the UI never presents a mask as the real thing. */
  masked?: boolean;
  span?: 1 | 2;
}

interface KeyValueGridProps {
  items: KeyValueItem[];
  columns?: number;
}

const isBlank = (value: ReactNode) =>
  value === null || value === undefined || value === '' || value === '—';

/** Dense label/value grid used across every detail surface. */
const KeyValueGrid = ({ items, columns = 3 }: KeyValueGridProps) => (
  <Box
    sx={{
      display: 'grid',
      gap: 2,
      gridTemplateColumns: {
        xs: 'repeat(1, minmax(0, 1fr))',
        sm: 'repeat(2, minmax(0, 1fr))',
        md: `repeat(${columns}, minmax(0, 1fr))`,
      },
    }}
  >
    {items.map((item) => (
      <Box
        key={item.label}
        sx={{ gridColumn: item.span === 2 ? { md: 'span 2' } : undefined, minWidth: 0 }}
      >
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ display: 'block', textTransform: 'uppercase', letterSpacing: 0.4, fontSize: 10.5 }}
        >
          {item.label}
        </Typography>
        <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mt: 0.25 }}>
          <Typography
            variant="body2"
            fontWeight={500}
            color={isBlank(item.value) ? 'text.disabled' : 'text.primary'}
            sx={{ wordBreak: 'break-word' }}
          >
            {isBlank(item.value) ? '—' : item.value}
          </Typography>
          {item.masked && (
            <Typography variant="caption" color="warning.main" fontWeight={600}>
              (masked)
            </Typography>
          )}
        </Stack>
      </Box>
    ))}
  </Box>
);

export default KeyValueGrid;
