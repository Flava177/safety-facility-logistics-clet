import { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router';
import { Box, Breadcrumbs, Link, Stack, Typography } from '@mui/material';

export interface Crumb {
  label: string;
  to?: string;
}

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  crumbs?: Crumb[];
  actions?: ReactNode;
  meta?: ReactNode;
}

/** Consistent page furniture: where am I, what is this, what can I do from here. */
const PageHeader = ({ title, subtitle, crumbs, actions, meta }: PageHeaderProps) => (
  <Box sx={{ mb: 2.5 }}>
    {crumbs && crumbs.length > 0 && (
      <Breadcrumbs sx={{ mb: 0.75 }}>
        {crumbs.map((crumb) =>
          crumb.to ? (
            <Link key={crumb.label} component={RouterLink} to={crumb.to} variant="caption">
              {crumb.label}
            </Link>
          ) : (
            <Typography key={crumb.label} variant="caption" color="text.secondary">
              {crumb.label}
            </Typography>
          ),
        )}
      </Breadcrumbs>
    )}

    <Stack
      direction={{ xs: 'column', md: 'row' }}
      justifyContent="space-between"
      alignItems={{ xs: 'stretch', md: 'flex-start' }}
      spacing={1.5}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h5" sx={{ fontSize: 22, lineHeight: 1.3 }}>
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
            {subtitle}
          </Typography>
        )}
        {meta && <Box sx={{ mt: 1 }}>{meta}</Box>}
      </Box>

      {actions && (
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ flexShrink: 0 }}>
          {actions}
        </Stack>
      )}
    </Stack>
  </Box>
);

export default PageHeader;
