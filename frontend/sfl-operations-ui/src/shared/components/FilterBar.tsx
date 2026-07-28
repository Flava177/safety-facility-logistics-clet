import { ReactNode } from 'react';
import Button from './Button';

interface FilterBarProps {
  children: ReactNode;
  onReset?: () => void;
  /** Disables reset when nothing is filtered, so the control tells the truth about state. */
  resetDisabled?: boolean;
  trailing?: ReactNode;
}

/** Filter row for register screens: wraps on narrow viewports, stays on one line on desktop. */
const FilterBar = ({ children, onReset, resetDisabled, trailing }: FilterBarProps) => (
  <div className="flex flex-col gap-4 border-b border-gray-200 px-5 pt-5 pb-6 lg:flex-row lg:items-end">
    <div className="grid flex-1 grid-cols-1 gap-x-5 gap-y-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {children}
    </div>
    <div className="flex shrink-0 items-center gap-2">
      {trailing}
      {onReset && (
        <Button variant="outline" onClick={onReset} disabled={resetDisabled} startIcon="filter">
          Reset
        </Button>
      )}
    </div>
  </div>
);

export default FilterBar;
