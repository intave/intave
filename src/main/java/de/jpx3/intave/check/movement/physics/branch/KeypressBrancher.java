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

package de.jpx3.intave.check.movement.physics.branch;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.share.Input;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.util.List;

final class KeypressBrancher extends MovementSearchBrancher {
  private static final int[][] KEYS_USAGE_ORDERED = {
    {1, 0},
    {0, 0},
    {1, -1},
    {1, 1},
    {0, -1},
    {0, 1},
    {-1, -1},
    {-1, 0},
    {-1, 1}
  };

  @Override
  public void branch(MovementSearchInput input, MovementSearchBranch inputBranch, List<MovementSearchBranch> outputBranches) {
    User user = input.user();
    ProtocolMetadata protocol = user.meta().protocol();
    MovementMetadata movement = user.meta().movement();

    // Elytra is key-independent
    if (!input.simulator().affectedByMovementKeys()) {
      outputBranches.add(inputBranch);
      return;
    }

    // For 1.9-1.20 vehicles send their keys
    if (movement.legacyVehicleKeyInput) {
      outputBranches.add(inputBranch.withKeypress(
        movement.legacyVehicleForwardKey,
        movement.legacyVehicleStrafeKey
      ));
      return;
    }

    if (protocol.sendsInputs()) {
      Input sentInput = movement.input;
      int forward = sentInput.forward();
      int strafe = sentInput.strafe();
      if (isValidPress(input, inputBranch, forward, strafe)) {
        outputBranches.add(inputBranch.withKeypress(forward, strafe));
      } else {
        outputBranches.add(inputBranch.withKeypress(0, 0));
      }
	    return;
    }

    int resultStart = outputBranches.size();

    // try predicting the keys
    int predictedDirection = predictedDirection(input, inputBranch);
    if (predictedDirection >= 0) {
      int predictedForward = forwardKeyFrom(predictedDirection);
      int predictedStrafe = strafeKeyFrom(predictedDirection);
      if (isValidPress(input, inputBranch, predictedForward, predictedStrafe)) {
        outputBranches.add(inputBranch.withPredictedKeypress(predictedForward, predictedStrafe));
      }
    }

    // try the last keys
    int lastKeyForward = movement.lastKeyForward;
    int lastKeyStrafe = movement.lastKeyStrafe;
    if (isValidPress(input, inputBranch, lastKeyForward, lastKeyStrafe)) {
      outputBranches.add(inputBranch.withPredictedKeypress(lastKeyForward, lastKeyStrafe));
    }

    // brute force keys
    for (int[] keyPair : KEYS_USAGE_ORDERED) {
      int keyForward = keyPair[0];
      int keyStrafe = keyPair[1];
      if (isValidPress(input, inputBranch, keyForward, keyStrafe)) {
        outputBranches.add(inputBranch.withKeypress(keyForward, keyStrafe));
      }
    }

    // we always have the option to "not press" keys
    if (outputBranches.size() == resultStart) {
      outputBranches.add(inputBranch.withKeypress(0, 0));
    }
  }

  private int predictedDirection(
    MovementSearchInput input, MovementSearchBranch config
  ) {
    SimulationEnvironment environment = input.environment();
    double lastMotionX = environment.baseMotionX();
    double lastMotionZ = environment.baseMotionZ();
    if (config.isJumping() && config.isSprinting()) {
      lastMotionX -= environment.yawSine() * 0.2f;
      lastMotionZ += environment.yawCosine() * 0.2f;
    }
    double differenceX = environment.offsetMotionX() - lastMotionX;
    double differenceZ = environment.offsetMotionZ() - lastMotionZ;
    return directionFrom(differenceX, differenceZ, environment.rotationYaw());
  }

  private boolean isValidPress(
    MovementSearchInput input,
    MovementSearchBranch parentConfig,
    int forward, int strafe
  ) {
    InventoryMetadata inventoryData = input.user().meta().inventory();
    if (inventoryData.inventoryOpen() && Math.abs(forward) + Math.abs(strafe) > 0) {
      return false;
    }
    if (parentConfig.moveConfig().isSprinting() && forward != 1) {
      return false;
    }
    return true;
  }

  private static int directionFrom(double differenceX, double differenceZ, float yaw) {
    if (Hypot.fast(differenceX, differenceZ) > 0.001) {
      double direction;
      direction = Math.toDegrees(Math.atan2(differenceZ, differenceX)) - 90d;
      direction -= yaw;
      direction %= 360d;
      if (direction < 0)
        direction += 360;
      direction = Math.abs(direction);
      direction /= 45d;
      int rounded = (int) Math.round(direction);
      if (Math.abs(rounded - direction) > 0.1) {
        // we shouldn't put faith in that prediction
        return -1;
      }
      return rounded;
    }
    return -1;
  }

  private static final int[] forwardKeys = {1, 1, 0, -1, -1, -1, 0, 1, 1};
  private static final int[] strafeKeys = {0, -1, -1, -1, 0, 1, 1, 1, 0};

  private static int forwardKeyFrom(int direction) {
    return direction == -1 ? 0 : forwardKeys[direction];
  }

  private static int strafeKeyFrom(int direction) {
    return direction == -1 ? 0 : strafeKeys[direction];
  }
}
