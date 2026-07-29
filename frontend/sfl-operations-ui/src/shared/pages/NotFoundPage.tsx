import { Link } from 'react-router';
import Icon from 'shared/components/Icon';
import { landingPath } from 'shared/layout/navigation';

const NotFoundPage = () => {
  // Back to *this* actor's dashboard, which is not the fleet one for everybody.
  const home = landingPath();

  return (
  <div className="flex min-h-[60vh] flex-col items-center justify-center text-center">
    <span className="mb-4 flex h-14 w-14 items-center justify-center rounded-lg bg-gray-100 text-gray-600">
      <Icon name="search" size={26} />
    </span>
    <p className="text-title-sm font-bold text-gray-900">Page not found</p>
    <p className="mt-2 max-w-md text-theme-sm text-gray-600">
      That address is not part of the SFL Operations dashboards. It may have been a link to a record
      that has since been removed.
    </p>
    {home && (
      <Link
        to={home}
        className="mt-5 inline-flex h-11 items-center gap-2 rounded-lg bg-brand-800 px-4 text-theme-sm font-medium text-white transition-colors hover:bg-brand-700"
      >
        <Icon name="dashboard" size={17} />
        Back to the dashboard
      </Link>
    )}
  </div>
  );
};

export default NotFoundPage;
