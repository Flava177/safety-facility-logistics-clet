import { ReactNode } from 'react';

/**
 * The controls that belong to one register row.
 *
 * <p>Right-aligned in the last column, and a plain wrapper rather than a menu: with two or three
 * actions a kebab menu costs a click to discover what is behind it, and the actions here - edit,
 * retire - are the ones an operator came to the register to use.
 *
 * <p>Safe beside a clickable row because `DataTable` renders row activation as a real button inside
 * the *first* cell rather than a handler on the `<tr>`. Nothing here is nested inside it, so a click
 * on an action cannot also navigate.
 */
const RowActions = ({ children }: { children: ReactNode }) => (
  <div className="flex items-center justify-end gap-1">{children}</div>
);

export default RowActions;
