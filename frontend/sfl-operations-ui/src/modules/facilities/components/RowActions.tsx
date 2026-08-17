import { ReactNode } from 'react';
import ControlButton from 'shared/components/ControlButton';
import type { ControlState } from 'shared/components/ControlButton';
import { cn } from 'shared/components/cn';

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
  <div className="flex items-center justify-end gap-1.5">{children}</div>
);

interface RowActionProps {
  state: ControlState;
  onClick: () => void;
  /** Names the control for assistive technology, and is the tooltip on the icon-only ones. */
  label: string;
  /**
   * `sm` in a table row, `md` in a page header.
   *
   * The same action has to read as the same action in both places, so the detail screens use these
   * components too rather than re-styling a plain button and drifting away from the registers.
   */
  size?: 'sm' | 'md';
}

/**
 * Edit, as a pencil and nothing else.
 *
 * <p>The word "Edit" beside a pencil is the word twice. In a row that already carries a code, a
 * name, a status and a date, a repeated label is the thing the eye has to skip past to reach what
 * the row is actually saying - so the glyph carries it, and the accessible name and the tooltip
 * carry the rest.
 *
 * <p>Amber rather than the button kit's grey: it has to be findable at the right-hand edge of a wide
 * table without competing with the status chips further left. `gold-800` on white measures 4.6:1,
 * which clears SC 1.4.3 - `gold-700` is 2.9:1 and is why CLET Gold is never used as a text colour
 * elsewhere in this application. The hover fill is the same gold at 50, so the target grows visibly
 * without the label changing colour underneath the cursor.
 */
export const EditRowAction = ({ state, onClick, label, size = 'sm' }: RowActionProps) => (
  <ControlButton
    state={state}
    variant="ghost"
    size={size}
    startIcon="edit"
    onClick={onClick}
    aria-label={label}
    className={cn(
      'px-2 text-gold-800',
      state.kind === 'allowed' && 'hover:bg-gold-50 hover:text-gold-900',
    )}
  />
);

/**
 * Move an asset to another space.
 *
 * <p>A pin, for the same reason edit is a pencil: the row is already dense and the glyph says it.
 * Teal is the kit's interactive colour - the one used for links and "view records" - because moving
 * something is an ordinary operational act, neither a correction nor a retirement.
 */
export const MoveRowAction = ({ state, onClick, label, size = 'sm' }: RowActionProps) => (
  <ControlButton
    state={state}
    variant="ghost"
    size={size}
    startIcon="map-pin"
    onClick={onClick}
    aria-label={label}
    className={cn(
      'px-2 text-teal-700',
      state.kind === 'allowed' && 'hover:bg-teal-50 hover:text-teal-900',
    )}
  />
);

/**
 * Take a record out of a collection - a member out of a zone.
 *
 * <p>Red, because it removes something, and icon-only because the row it sits on already names what
 * would be removed. Distinct from retiring: nothing is archived here, a membership simply ends, and
 * the button directly above puts it back.
 */
export const RemoveRowAction = ({ state, onClick, label, size = 'sm' }: RowActionProps) => (
  <ControlButton
    state={state}
    variant="ghost"
    size={size}
    startIcon="close"
    onClick={onClick}
    aria-label={label}
    className={cn(
      'px-2 text-error-800',
      state.kind === 'allowed' && 'hover:bg-error-50 hover:text-error-900',
    )}
  />
);

/**
 * Retire, in red, because it takes a record out of use.
 *
 * <p>Not `danger` - a filled red button on every row of a register would make the table read as a
 * page of warnings, and the destructive treatment belongs on the confirmation, which is where the
 * irreversible half of this actually happens. This is the error text colour on a quiet button:
 * `error-800` is 12:1 on white, so it reads as serious without shouting on twenty rows at once.
 */
export const RetireRowAction = ({ state, onClick, label, size = 'sm' }: RowActionProps) => (
  <ControlButton
    state={state}
    variant="ghost"
    size={size}
    onClick={onClick}
    aria-label={label}
    className={cn(
      'text-error-800',
      state.kind === 'allowed' && 'hover:bg-error-50 hover:text-error-900',
    )}
  >
    Retire
  </ControlButton>
);

export default RowActions;
