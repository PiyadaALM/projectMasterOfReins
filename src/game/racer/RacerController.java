package game.racer;

import game.app.*;
public interface RacerController {
    RacerAction createAction(InputState inputState, Racer racer, double elapsedSeconds);

    default RacerAction createAction(InputState inputState) {
        return createAction(inputState, null, 0);
    }
}
