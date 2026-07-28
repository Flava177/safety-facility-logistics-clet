import { Link as RouterLink } from 'react-router';
import { Box, Button, Stack, Typography } from '@mui/material';
import sflLogo from 'assets/sfl-logo.png';
import { fleetPaths } from 'shared/layout/navigation';

/** Not found. Kept plain and operational — the useful thing here is the way back. */
const Page404 = () => (
  <Stack
    sx={{
      minHeight: '100vh',
      alignItems: 'center',
      justifyContent: 'center',
      p: { xs: 3, sm: 6 },
      bgcolor: 'primary.main',
      textAlign: 'center',
    }}
    spacing={3}
  >
    <Box
      component="img"
      src={sflLogo}
      alt="Safety, Facilities & Logistics Directorate"
      sx={{
        width: 72,
        height: 72,
        objectFit: 'contain',
        bgcolor: 'common.white',
        borderRadius: 2,
        p: 1,
      }}
    />
    <Box>
      <Typography variant="h3" sx={{ color: 'common.white', fontSize: { xs: 28, sm: 36 } }}>
        Page not found
      </Typography>
      <Typography variant="body1" sx={{ color: 'rgba(255,255,255,0.75)', mt: 1 }}>
        That screen is not part of the SFL operations console.
      </Typography>
    </Box>
    <Button
      component={RouterLink}
      to={fleetPaths.dashboard}
      variant="contained"
      color="secondary"
      size="large"
    >
      Back to Fleet operations
    </Button>
  </Stack>
);

export default Page404;
