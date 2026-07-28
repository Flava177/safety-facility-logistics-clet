import { fleetPaths } from 'shared/layout/navigation';

export const rootPaths = {
  root: '/',
  fleetRoot: '/fleet',
};

const paths = {
  root: rootPaths.root,
  ...fleetPaths,
  404: '/404',
};

export default paths;
