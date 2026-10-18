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

import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Rotation;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class MovementSearchBranch {
	private static final long INITIAL_FREQUENCY_KEY = 0x9E3779B97F4A7C15L;
	private static final int ACTUAL_MOTION_OVERRIDE = 1;
	private static final int ALTERNATIVE_BLOCK_INSIDE = 2;
	private static final int AMBIGUOUS_UPDATE = 3;
	private static final int CROUCHING_INPUT = 4;
	private static final int EXPLICIT_TICK_FINISH = 5;
	private static final int HAND_ACTIVE = 6;
	private static final int JUMPED = 7;
	private static final int KEYPRESS = 8;
	private static final int PREDICTED_JUMP = 9;
	private static final int PREDICTED_KEYPRESS = 10;
	private static final int PREDICTED_SPRINTING = 11;
	private static final int PREVIOUS_POST_TICK = 12;
	private static final int REDUCE_BEFORE = 13;
	private static final int REDUCE_TICKS = 14;
	private static final int ROTATION = 15;
	private static final int SPRINTING = 16;
	private static final int USE_LAST_MOVEMENT_CONFIG = 17;
	private static final int FLYING = 18;
	private static final int ITEM_USE_SPEED_MULTIPLIER = 19;
	private static final int LEGACY_ITEM_USE_SLOT_SWITCH = 20;
	private static final int LEGACY_ITEM_USE_RELEASE_RESOLUTION = 21;

	private final static MovementSearchBranch BLANK = new MovementSearchBranch(
		MovementConfiguration.blank(), UnaryOperator.identity(), false, true, INITIAL_FREQUENCY_KEY
	);
	private final MovementConfiguration configuration;
	private final UnaryOperator<SimulationEnvironment> environmentModifier;
	private final boolean environmentModifierApplied;
	private final boolean canFinishExplicitTick;
	private final long frequencyKey;

	private MovementSearchBranch(MovementConfiguration configuration, UnaryOperator<SimulationEnvironment> environmentModifier, boolean environmentModifierApplied, boolean canFinishExplicitTick, long frequencyKey) {
		this.configuration = configuration;
		this.environmentModifier = environmentModifier;
		this.environmentModifierApplied = environmentModifierApplied;
		this.canFinishExplicitTick = canFinishExplicitTick;
		this.frequencyKey = frequencyKey;
	}

	public static MovementSearchBranch blank(MovementSearchInput input) {
		return BLANK;
	}

	public MovementConfiguration moveConfig() {
		return configuration;
	}

	private MovementSearchBranch withMoveConfig(
		MovementConfiguration configuration, int key, long value
	) {
		return new MovementSearchBranch(
			configuration, environmentModifier, environmentModifierApplied,
			canFinishExplicitTick, updateBranchKey(frequencyKey, key, value)
		);
	}

	private MovementSearchBranch modifyBefore(UnaryOperator<SimulationEnvironment> modifier, int key, long value) {
		return new MovementSearchBranch(
			configuration,
			andThen(modifier, environmentModifier),
			true, canFinishExplicitTick,
			updateBranchKey(frequencyKey, key, value)
		);
	}

	private MovementSearchBranch modifyAfter(UnaryOperator<SimulationEnvironment> modifier, int key, long value) {
		return new MovementSearchBranch(
			configuration,
			andThen(environmentModifier, modifier),
			true, canFinishExplicitTick,
			updateBranchKey(frequencyKey, key, value)
		);
	}

	public long frequencyKey() {
		return frequencyKey;
	}

	private static <T> UnaryOperator<T> andThen(UnaryOperator<T> first, UnaryOperator<T> second) {
		return t -> second.apply(first.apply(t));
	}

	public MovementSearchBranch withExplicitTickFinishAllow(boolean canFinishUserTick) {
		boolean newFinishTick = canFinishUserTick && canFinishExplicitTick;
		return new MovementSearchBranch(
			configuration, environmentModifier, environmentModifierApplied,
			newFinishTick, updateBranchKey(frequencyKey, EXPLICIT_TICK_FINISH, booleanValue(newFinishTick))
		);
	}

	public MovementSearchBranch setToOnlyUsableForImplicitFlyingPackets() {
		return withExplicitTickFinishAllow(false);
	}

	public MovementSearchBranch withRotation(Rotation rotation) {
		return modifyAfter(env -> {
			env.setRotation(rotation);
			return env;
		}, ROTATION, 0);
	}

	public MovementSearchBranch withHandActive(boolean handActive) {
		return withMoveConfig(
			configuration.withHandActive(handActive),
			HAND_ACTIVE, booleanValue(handActive)
		);
	}

	public MovementSearchBranch withHandActive(boolean handActive, float speedMultiplier) {
		MovementSearchBranch branch = withHandActive(handActive);
		if (!handActive) {
			return branch;
		}
		return branch.modifyAfter(environment -> {
			environment.overrideItemUseSpeedMultiplier(speedMultiplier);
			return environment;
		}, ITEM_USE_SPEED_MULTIPLIER, Float.floatToIntBits(speedMultiplier));
	}

	MovementSearchBranch withLegacyItemUseSlotSwitch() {
		MovementSearchBranch branch = withHandActive(false);
		return branch.modifyAfter(environment -> {
			environment.setLegacyItemUseSlotSwitchPending(true);
			return environment;
		}, LEGACY_ITEM_USE_SLOT_SWITCH, 1);
	}

	MovementSearchBranch withLegacyItemUseSlotSwitchResolved() {
		return modifyAfter(environment -> {
			environment.setLegacyItemUseSlotSwitchPending(false);
			return environment;
		}, LEGACY_ITEM_USE_SLOT_SWITCH, 0);
	}

	MovementSearchBranch withLegacyItemUseReleaseResolved() {
		return modifyAfter(environment -> {
			environment.setLegacyItemUseReleaseUncertain(false);
			environment.setLegacyItemUseForcedReleaseApplied(true);
			environment.setLegacyItemUseSlotSwitchPending(false);
			return environment;
		}, LEGACY_ITEM_USE_RELEASE_RESOLUTION, 0);
	}

	public MovementSearchBranch withAlternativeBlockInsideCheck(boolean alternate) {
		MovementConfiguration updated = alternate
			? configuration.withAlternativeBlockInsideCheck()
			: configuration.withoutAlternativeBlockInsideCheck();
		return withMoveConfig(updated, ALTERNATIVE_BLOCK_INSIDE, booleanValue(alternate));
	}

	public MovementSearchBranch withKeypress(int forward, int strafe) {
		return withMoveConfig(
			configuration.withKeypress(forward, strafe),
			KEYPRESS, keypressValue(forward, strafe)
		);
	}

	public MovementSearchBranch withPredictedKeypress(int forward, int strafe) {
		return withMoveConfig(
			configuration.withKeypress(forward, strafe),
			PREDICTED_KEYPRESS, keypressValue(forward, strafe)
		);
	}

	public MovementSearchBranch withReduceTicks(int ticks) {
		return withMoveConfig(
			configuration.withReduceTicks(ticks),
			REDUCE_TICKS, ticks
		);
	}

	public MovementSearchBranch withReduceBefore(boolean reduceBefore) {
		return withMoveConfig(
			configuration.withReduceBefore(reduceBefore),
			REDUCE_BEFORE, booleanValue(reduceBefore)
		);
	}

	public MovementSearchBranch withJumped(boolean jumped) {
		return withMoveConfig(configuration.withJumped(jumped), JUMPED, booleanValue(jumped));
	}

	MovementSearchBranch withFlying(boolean flying) {
		return modifyAfter(environment -> {
			environment.setFlying(flying);
			return environment;
		}, FLYING, booleanValue(flying));
	}

	public MovementSearchBranch withPredictedJumped(boolean jumped) {
		return withMoveConfig(configuration.withJumped(jumped), PREDICTED_JUMP, booleanValue(jumped));
	}

	public boolean canFinishExplicitTick() {
		return canFinishExplicitTick;
	}

	public boolean isJumping() {
		return configuration.isJumping();
	}

	public MovementSearchBranch withSprintingSetTo(boolean sprinting) {
		return withMoveConfig(configuration.withSprintingSetTo(sprinting), SPRINTING, booleanValue(sprinting));
	}

	public MovementSearchBranch withPredictedSprintingSetTo(boolean sprinting) {
		return withMoveConfig(configuration.withSprintingSetTo(sprinting), PREDICTED_SPRINTING, booleanValue(sprinting));
	}

	MovementSearchBranch withActualMotionOverride(boolean override) {
		MovementConfiguration updated = override
			? configuration.allowOverrideToActualMotion()
			: configuration.denyOverrideToActualMotion();
		return withMoveConfig(updated, ACTUAL_MOTION_OVERRIDE, booleanValue(override));
	}

	MovementSearchBranch withLastMovementConfiguration(MovementConfiguration previous) {
		return withMoveConfig(previous, USE_LAST_MOVEMENT_CONFIG, 0);
	}

	MovementSearchBranch withoutCrouchingInputSlowdown() {
		return modifyAfter(environment -> {
			environment.overrideCrouchingInputSlowdown(false);
			return environment;
		}, CROUCHING_INPUT, 0);
	}

	MovementSearchBranch withPreviousPostTickCandidate(Motion motion, boolean priorSprinting) {
		return modifyBefore(environment -> {
			environment.setBaseMotion(motion);
			environment.setLastSprinting(priorSprinting);
			return environment;
		}, PREVIOUS_POST_TICK, booleanValue(priorSprinting));
	}

	MovementSearchBranch withAmbiguousUpdates(
		UnaryOperator<SimulationEnvironment> modifier,
		int optionIndex,
		boolean canFinishTick
	) {
		long value = (long) optionIndex << 1 | booleanValue(canFinishTick);
		return modifyAfter(modifier, AMBIGUOUS_UPDATE, value);
	}

	public boolean isSprinting() {
		return configuration.isSprinting();
	}

	public SimulationEnvironment modifiedMutableView(SimulationEnvironment env) {
		if (!environmentModifierApplied) {
			return env.mutableView();
		}
		return environmentModifier.apply(env.mutableView());
	}

	SimulationEnvironment modifiedImmutableView(MovementSearchInput input) {
		if (!environmentModifierApplied) {
			return input.environment();
		}
		return input.modifiedImmutableEnvironment(environmentModifier);
	}

	public SimulationEnvironment modifiedImmutableView(SimulationEnvironment env) {
		if (!environmentModifierApplied) {
			return env.immutableView();
		}
		return environmentModifier.apply(env.mutableView()).immutableView();
	}

	private static int booleanValue(boolean value) {
		return value ? 1 : 0;
	}

	private static long keypressValue(int forward, int strafe) {
		return ((long) forward << 32) ^ (strafe & 0xFFFFFFFFL);
	}

	private static long updateBranchKey(long oldKey, int addedKey, long addedValue) {
		long val = addedValue + addedKey * 0x9E3779B97F4A7C15L;
		val ^= val >>> 30;
		val *= 0xBF58476D1CE4E5B9L;
		val ^= val >>> 27;
		val *= 0x94D049BB133111EBL;
		val ^= val >>> 31;
		return Long.rotateLeft(oldKey, 21) * 0x9E3779B97F4A7C15L ^ val;
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MovementSearchBranch)) {
			return false;
		}
		MovementSearchBranch that = (MovementSearchBranch) other;
		return this.configuration.equals(that.configuration) &&
			Objects.equals(this.environmentModifier, that.environmentModifier);
	}

	@Override
	public int hashCode() {
		int result = configuration.hashCode();
		result = 31 * result + environmentModifier.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "MovementSearchBranch{" +
			"configuration=" + configuration +
			", reduceBefore=" + configuration.reduceBefore() +
			", overrideEndMotionToActualMotion=" + configuration.overrideEndMotionToActualMotion() +
			", alternateBlockInsideCheck=" + configuration.usesAlternateBlockInsideCheck() +
			", environmentModifierApplied=" + environmentModifierApplied +
			", canFinishExplicitTick=" + canFinishExplicitTick +
			", frequencyKey=0x" + Long.toHexString(frequencyKey) +
			'}';
	}
}
