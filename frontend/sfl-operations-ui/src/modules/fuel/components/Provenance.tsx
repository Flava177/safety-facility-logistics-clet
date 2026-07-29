import { ReactNode } from 'react';
import Icon from 'shared/components/Icon';

/**
 * A caption for a figure the console worked out for itself.
 *
 * Far less of the fuel module needs this than once did: the dashboard publishes its anomaly,
 * logbook and import indicators, the registers page properly, and the detail screens read a real
 * audit history. What is left is genuinely derived — a chart bucketed by day from fetched records,
 * a freshness threshold this console chose — and it still says so, because a derived figure sitting
 * silently beside a published one is how a dashboard starts lying.
 */
export const DerivedNote = ({ children }: { children: ReactNode }) => (
  <p className="mt-3 flex items-start gap-1.5 text-theme-xs text-gray-600">
    <Icon name="info" size={13} className="mt-0.5 shrink-0 text-teal-700" />
    <span>{children}</span>
  </p>
);
