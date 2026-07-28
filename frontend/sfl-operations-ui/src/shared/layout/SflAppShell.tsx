import { PropsWithChildren, useState } from 'react';
import { NavLink, useLocation } from 'react-router';
import {
  AppBar,
  Box,
  Chip,
  Drawer,
  IconButton,
  Link,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import sflLogo from 'assets/sfl-logo.png';
import { sflActor } from 'shared/api/config';
import IconifyIcon from 'components/base/IconifyIcon';
import { NavItem, directorate, navSections } from './navigation';

const SIDENAV_WIDTH = 268;

const NavEntry = ({ item, onNavigate }: { item: NavItem; onNavigate: () => void }) => {
  const { pathname } = useLocation();
  const active =
    item.to === '/fleet' ? pathname === '/fleet' || pathname === '/' : pathname.startsWith(item.to);

  if (!item.available) {
    return (
      <Tooltip title="Module not yet implemented" placement="right">
        <Box>
          <ListItemButton disabled sx={{ borderRadius: 1.5, mb: 0.25, py: 0.85, opacity: 0.45 }}>
            <ListItemIcon sx={{ minWidth: 32, color: 'common.white' }}>
              <IconifyIcon icon={item.icon} sx={{ fontSize: 18 }} />
            </ListItemIcon>
            <ListItemText
              primary={item.label}
              slotProps={{ primary: { fontSize: 13.5, color: 'common.white' } }}
            />
          </ListItemButton>
        </Box>
      </Tooltip>
    );
  }

  return (
    <ListItemButton
      component={NavLink}
      to={item.to}
      onClick={onNavigate}
      sx={{
        borderRadius: 1.5,
        mb: 0.25,
        py: 0.85,
        color: 'common.white',
        // Gold marks the active module; navy stays the surface.
        bgcolor: active ? 'rgba(184, 149, 13, 0.18)' : 'transparent',
        borderLeft: 3,
        borderColor: active ? 'secondary.main' : 'transparent',
        '&:hover': { bgcolor: 'rgba(255, 255, 255, 0.07)' },
      }}
    >
      <ListItemIcon sx={{ minWidth: 32, color: active ? 'secondary.main' : 'common.white' }}>
        <IconifyIcon icon={item.icon} sx={{ fontSize: 18 }} />
      </ListItemIcon>
      <ListItemText
        primary={item.label}
        slotProps={{
          primary: { fontSize: 13.5, fontWeight: active ? 700 : 500, color: 'common.white' },
        }}
      />
    </ListItemButton>
  );
};

const SidenavContent = ({ onNavigate }: { onNavigate: () => void }) => (
  <Stack sx={{ height: 1, bgcolor: 'primary.main' }}>
    <Stack direction="row" spacing={1.5} alignItems="center" sx={{ px: 2.5, py: 2.25 }}>
      <Box
        component="img"
        src={sflLogo}
        alt="Safety, Facilities & Logistics Directorate"
        sx={{
          width: 40,
          height: 40,
          objectFit: 'contain',
          borderRadius: 1,
          bgcolor: 'common.white',
          p: 0.4,
        }}
      />
      <Box sx={{ minWidth: 0 }}>
        <Typography
          variant="subtitle2"
          sx={{ color: 'common.white', fontWeight: 800, lineHeight: 1.2 }}
        >
          {directorate.shortName}
        </Typography>
        <Typography variant="caption" sx={{ color: 'secondary.light', display: 'block' }}>
          {directorate.parentOrganisation} · Directorate
        </Typography>
      </Box>
    </Stack>

    <Box sx={{ px: 1.5, pb: 2, overflowY: 'auto', flex: 1 }}>
      {navSections.map((section) => (
        <Box key={section.heading} sx={{ mb: 1.5 }}>
          <Typography
            variant="caption"
            sx={{
              px: 1.5,
              py: 1,
              display: 'block',
              color: 'rgba(255,255,255,0.55)',
              textTransform: 'uppercase',
              letterSpacing: 0.6,
              fontSize: 10.5,
              fontWeight: 700,
            }}
          >
            {section.heading}
          </Typography>
          <List disablePadding>
            {section.items.map((item) => (
              <NavEntry key={item.to} item={item} onNavigate={onNavigate} />
            ))}
          </List>
        </Box>
      ))}
    </Box>

    <Box sx={{ px: 2.5, py: 2, borderTop: 1, borderColor: 'rgba(255,255,255,0.12)' }}>
      <Link
        href={directorate.url}
        target="_blank"
        rel="noopener"
        variant="caption"
        sx={{ color: 'secondary.light' }}
      >
        {directorate.name}
      </Link>
    </Box>
  </Stack>
);

/**
 * The SFL application shell.
 *
 * Navy is the chrome, gold marks what is active, white is the work surface. The sidenav is
 * permanent from `lg` up and a temporary drawer below it, which keeps the dense desktop and tablet
 * layouts intact while staying usable on a phone.
 */
const SflAppShell = ({ children }: PropsWithChildren) => {
  const theme = useTheme();
  const permanent = useMediaQuery(theme.breakpoints.up('lg'));
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.elevation1' }}>
      {permanent ? (
        <Box
          component="nav"
          sx={{ width: SIDENAV_WIDTH, flexShrink: 0, position: 'sticky', top: 0, height: '100vh' }}
        >
          <SidenavContent onNavigate={() => undefined} />
        </Box>
      ) : (
        <Drawer
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          slotProps={{ paper: { sx: { width: SIDENAV_WIDTH, border: 0 } } }}
        >
          <SidenavContent onNavigate={() => setMobileOpen(false)} />
        </Drawer>
      )}

      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <AppBar
          position="sticky"
          elevation={0}
          sx={{ bgcolor: 'primary.main', borderBottom: 3, borderColor: 'secondary.main' }}
        >
          <Toolbar sx={{ minHeight: { xs: 58, md: 62 }, gap: 1.5 }}>
            {!permanent && (
              <IconButton
                onClick={() => setMobileOpen(true)}
                sx={{ color: 'common.white' }}
                aria-label="Open navigation"
              >
                <IconifyIcon icon="material-symbols:menu" />
              </IconButton>
            )}
            <Box sx={{ minWidth: 0, flex: 1 }}>
              <Typography
                variant="subtitle2"
                sx={{ color: 'common.white', fontWeight: 700, lineHeight: 1.2 }}
                noWrap
              >
                Fleet & Vehicle Management
              </Typography>
              <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.7)' }} noWrap>
                SRS-SFL-S166 · Safety, Facilities & Logistics Directorate
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} alignItems="center" sx={{ flexShrink: 0 }}>
              <Chip
                size="small"
                variant="soft"
                color="secondary"
                label={`Sites: ${sflActor.sites}`}
                sx={{ display: { xs: 'none', md: 'inline-flex' } }}
              />
              <Tooltip title={`${sflActor.displayName} · ${sflActor.roles}`}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Box
                    sx={{
                      width: 32,
                      height: 32,
                      borderRadius: '50%',
                      bgcolor: 'secondary.main',
                      color: 'common.white',
                      display: 'grid',
                      placeItems: 'center',
                      fontSize: 13,
                      fontWeight: 700,
                    }}
                  >
                    {sflActor.displayName.slice(0, 1).toUpperCase()}
                  </Box>
                  <Typography
                    variant="caption"
                    sx={{ color: 'common.white', display: { xs: 'none', sm: 'block' } }}
                  >
                    {sflActor.displayName}
                  </Typography>
                </Stack>
              </Tooltip>
            </Stack>
          </Toolbar>
        </AppBar>

        <Box component="main" sx={{ flex: 1, p: { xs: 2, md: 3 }, minWidth: 0 }}>
          {children}
        </Box>
      </Box>
    </Box>
  );
};

export default SflAppShell;
