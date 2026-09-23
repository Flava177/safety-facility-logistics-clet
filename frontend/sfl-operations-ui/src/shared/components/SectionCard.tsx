import { ReactNode } from 'react';
import cardWatermark from 'assets/adinkra-hene-navy.png';
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
      'relative flex h-full flex-col overflow-hidden rounded-lg border border-gray-200 bg-white shadow-theme-md',
      className,
    )}
  >
    {/* Adinkra Hene watermark, engraved faintly into the card - the same treatment the design
        system's own Cards component uses (`card.css`), one mark per card rather than the
        repeating pattern the dark side-navigation uses. */}
    <div
      aria-hidden="true"
      className="pointer-events-none absolute -right-5 -bottom-5 z-0 h-28 w-28 bg-contain bg-no-repeat opacity-[0.06]"
      style={{ backgroundImage: `url(${cardWatermark})` }}
    />
    {(title || actions) && (
      <header
        className={cn(
          'relative z-1 flex shrink-0 flex-wrap items-start justify-between gap-3 px-5 pt-5',
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
    <div
      className={cn('relative z-1 min-w-0 flex-1', flush ? 'p-0' : 'p-5', bodyClassName)}
    >
      {children}
    </div>
  </section>
);

export default SectionCard;
