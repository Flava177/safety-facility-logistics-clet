import { ReactNode } from 'react';
import { Box, Divider, Paper, Stack, Typography } from '@mui/material';

interface SectionCardProps {
  title?: string;
  subtitle?: string;
  actions?: ReactNode;
  /** Removes the body padding for edge-to-edge tables. */
  flush?: boolean;
  children: ReactNode;
}

/** A titled work surface — white, low-chrome, dense. The default container for content. */
const SectionCard = ({ title, subtitle, actions, flush, children }: SectionCardProps) => (
  <Paper
    sx={{
      bgcolor: 'common.white',
      border: 1,
      borderColor: 'divider',
      borderRadius: 2,
      overflow: 'hidden',
    }}
  >
    {(title || actions) && (
      <>
        <Stack
          direction="row"
          alignItems="center"
          justifyContent="space-between"
          spacing={1.5}
          sx={{ px: 2, py: 1.5 }}
        >
          <Box sx={{ minWidth: 0 }}>
            {title && (
              <Typography variant="subtitle1" fontWeight={700} sx={{ lineHeight: 1.3 }}>
                {title}
              </Typography>
            )}
            {subtitle && (
              <Typography variant="caption" color="text.secondary">
                {subtitle}
              </Typography>
            )}
          </Box>
          {actions && (
            <Stack direction="row" spacing={1} alignItems="center" sx={{ flexShrink: 0 }}>
              {actions}
            </Stack>
          )}
        </Stack>
        <Divider />
      </>
    )}
    <Box sx={{ p: flush ? 0 : 2 }}>{children}</Box>
  </Paper>
);

export default SectionCard;
