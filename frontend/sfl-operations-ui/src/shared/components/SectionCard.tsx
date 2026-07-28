import { ReactNode } from 'react';
import { cn } from './cn';

interface SectionCardProps {
  title?: string;
  subtitle?: string;
  actions?: ReactNode;
  /** Removes the body padding for edge-to-edge tables and lists. */
  flush?: boolean;
  className?: string;
  bodyClassName?: string;
  children: ReactNode;
}

/**
 * A titled work surface.
 *
 * The heading block sits inside the card's own padding rather than in a bordered strip, so a card
 * holding a table reads as one object: title, one line of explanation, then the table's own grey
 * header row. Stacking two horizontal rules there is the thing that made the earlier screens look
 * boxed-in.
 */
const SectionCard = ({
  title,
  subtitle,
  actions,
  flush,
  className,
  bodyClassName,
  children,
}: SectionCardProps) => (
  <section
    className={cn(
      'flex h-full flex-col overflow-hidden rounded-lg border border-gray-200 bg-white',
      className,
    )}
  >
    {(title || actions) && (
      <header
        className={cn(
          'flex shrink-0 flex-wrap items-start justify-between gap-3 px-5 pt-5',
          flush ? 'pb-1' : 'pb-0',
        )}
      >
        <div className="min-w-0">
          {title && <h2 className="text-theme-md font-bold text-gray-900">{title}</h2>}
          {subtitle && <p className="mt-0.5 text-theme-xs text-gray-500">{subtitle}</p>}
        </div>
        {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
      </header>
    )}
    <div className={cn('min-w-0 flex-1', flush ? 'p-0' : 'p-5', bodyClassName)}>{children}</div>
  </section>
);

export default SectionCard;
