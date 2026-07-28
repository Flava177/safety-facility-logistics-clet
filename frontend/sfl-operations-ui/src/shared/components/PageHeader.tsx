import { Fragment, ReactNode } from 'react';
import { Link } from 'react-router';
import Icon from './Icon';

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

/**
 * Page furniture: where am I, what is this, what can I do from here.
 *
 * A heavy sans title with a single quiet line under it — the same shape the rest of the CLET
 * platform uses, so an operator moving between consoles is not relearning the page.
 */
const PageHeader = ({ title, subtitle, crumbs, actions, meta }: PageHeaderProps) => (
  <div className="mb-6">
    {crumbs && crumbs.length > 0 && (
      <nav aria-label="Breadcrumb" className="mb-2">
        <ol className="flex flex-wrap items-center gap-1.5 text-theme-xs text-gray-500">
          {crumbs.map((crumb, index) => (
            <Fragment key={`${crumb.label}-${index}`}>
              {index > 0 && (
                <li aria-hidden="true" className="text-gray-400">
                  <Icon name="chevron-right" size={12} />
                </li>
              )}
              <li>
                {crumb.to ? (
                  <Link
                    to={crumb.to}
                    className="rounded-sm text-gray-500 underline-offset-2 transition-colors hover:text-teal-700 hover:underline"
                  >
                    {crumb.label}
                  </Link>
                ) : (
                  <span aria-current="page" className="font-medium text-gray-700">
                    {crumb.label}
                  </span>
                )}
              </li>
            </Fragment>
          ))}
        </ol>
      </nav>
    )}

    <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
      <div className="min-w-0">
        <h1 className="text-title-sm font-bold tracking-tight text-gray-900">{title}</h1>
        {subtitle && <p className="mt-1 max-w-3xl text-theme-sm text-gray-500">{subtitle}</p>}
        {meta && <div className="mt-3">{meta}</div>}
      </div>

      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  </div>
);

export default PageHeader;
