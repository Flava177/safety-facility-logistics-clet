import Button, { ButtonProps } from 'shared/components/Button';
import type { ControlState } from '../api/workflow';

interface ControlButtonProps extends Omit<ButtonProps, 'disabled' | 'title'> {
  state: ControlState;
}

/**
 * Renders a {@link ControlState} as the button it describes.
 *
 * The distinction this exists to keep: **a permission denial hides the control, a state shortfall
 * disables it and says why.** Spelling that out at each of the eleven call sites would eventually get
 * it wrong at one of them, and the one that got it wrong would be the one showing a technician a
 * button they can never press.
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
