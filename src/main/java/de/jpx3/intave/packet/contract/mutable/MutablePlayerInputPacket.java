/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.contract.mutable;

import de.jpx3.intave.packet.contract.PlayerInputPacket;
import de.jpx3.intave.share.Input;

public interface MutablePlayerInputPacket extends PlayerInputPacket {

	void setInput(Input input);

	default void setAxes(float forward, float strafe) {
		Input input = input();
		setInput(input.withKeysSetTo(forward, strafe, input.jumpKey(), input.sneakKey(), input.sprintKey()));
	}

	default void setJumping(boolean jumping) {
		Input input = input();
    setInput(input.withKeysSetTo(input.forward(), input.strafe(), jumping, input.sneakKey(), input.sprintKey()));
	}
}
