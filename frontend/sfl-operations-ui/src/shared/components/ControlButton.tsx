import Button, { ButtonProps } from './Button';

/**
 * What a control may do, decided once by a module's `workflow.ts` and rendered here.
 *
 * <h2>The rule, and why it is not the obvious one</h2>
 *
 * <p>**A permission denial hides the control; a state or data shortfall disables it with the
 * reason.** S153 paid for that distinction: a technician was shown a Close button disabled with "You
 * do not have permission" - permanently, on every job, forever. A control somebody will never be
 * allowed to press is noise; a control they cannot press *yet* is information.
 *
 * <p>Promoted out of `modules/booking` when facilities became the second module to need it, on the
 * rule in the playbook that the kit gains a component at the second caller rather than in
 * anticipation. `modules/booking/components/ControlButton.tsx` is now a one-line re-export, so its
 * eleven call sites were untouched - the same shape `useClientWindow` was promoted in.
 */
export type ControlState =
  | { kind: 'allowed' }
  | { kind: 'hidden' }
  | { kind: 'disabled'; reason: string };

export const allowed: ControlState = { kind: 'allowed' };
export const hidden: ControlState = { kind: 'hidden' };
export const disabled = (reason: string): ControlState => ({ kind: 'disabled', reason });

interface ControlButtonProps extends Omit<ButtonProps, 'disabled' | 'title'> {
  state: ControlState;
}

/**
 * Renders a {@link ControlState} as the button it describes.
 *
 * Spelling the rule out at each call site would eventually get it wrong at one of them, and the one
 * that got it wrong would be the one showing a technician a button they can never press.
 */
const ControlButton = ({ state, children, ...rest }: ControlButtonProps) => {
  if (state.kind === 'hidden') {
    return null;
  }
  return (
    <Button
      {...rest}
      disabled={state.kind === 'disabled'}
      // The reason travels on the control itself, so it is readable where the operator is looking
      // rather than in a notice further up the page.
      title={state.kind === 'disabled' ? state.reason : undefined}
    >
      {children}
    </Button>
  );
};

export default ControlButton;
