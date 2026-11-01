package game.racer;

import game.app.*;
public class PlayerController implements RacerController {
    @Override
    public RacerAction createAction(InputState inputState, Racer racer, double elapsedSeconds) {
        int horizontalDirection = inputState.isMovingRight() ? 1 : 0;
        int verticalDirection = (inputState.isMovingDown() ? 1 : 0)
            - (inputState.isMovingUp() ? 1 : 0);

        return new RacerAction(
            horizontalDirection,
            verticalDirection,
            inputState.isSprinting());
    }
}
