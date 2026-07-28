import { cn } from './cn';

export interface TabItem {
  value: string;
  label: string;
  count?: number;
}

interface TabsProps {
  items: TabItem[];
  value: string;
  onChange: (value: string) => void;
  className?: string;
}

/** Underlined tab strip for detail pages. Scrolls rather than wrapping on narrow viewports. */
const Tabs = ({ items, value, onChange, className }: TabsProps) => (
  <div className={cn('no-scrollbar overflow-x-auto border-b border-gray-200', className)}>
    <div className="flex min-w-max gap-1" role="tablist">
      {items.map((item) => {
        const active = item.value === value;
        return (
          <button
            key={item.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(item.value)}
            className={cn(
              '-mb-px flex items-center gap-2 border-b-2 px-4 py-2.5 text-theme-sm font-medium whitespace-nowrap transition',
              active
                ? 'border-gold-700 text-brand-900'
                : 'border-transparent text-gray-600 hover:border-gray-400 hover:text-gray-900',
            )}
          >
            {item.label}
            {item.count !== undefined && (
              <span
                className={cn(
                  'rounded-full px-1.5 py-0.5 text-theme-xs font-semibold',
                  active ? 'bg-gold-50 text-gold-900' : 'bg-gray-100 text-gray-700',
                )}
              >
                {item.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  </div>
);

export default Tabs;
