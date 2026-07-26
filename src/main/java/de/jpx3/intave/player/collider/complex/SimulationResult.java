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

package de.jpx3.intave.player.collider.complex;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.Motion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class SimulationResult {
	private static final SimulationResult INVALID_SIMULATION = new SimulationResult();
	public static final StreamCodec<JsonReader, JsonWriter, SimulationResult> JSON_CODEC = SimulationResultJsonCodec.INSTANCE;

	private final Motion offsetMotion;
	private final Motion intermittentResult;
	private final boolean onGround, collidedHorizontally, collidedVertically;
	private final boolean resetMotionX, resetMotionZ;
	private final boolean step, edgeSneak;

	private final double yStepHeight;
	private final Motion actualMotion;

	private final Map<String, Double> debugData = IntaveControl.ENABLE_MOVEMENT_DEBUGGER_COLLECTOR ? new HashMap<>() : Collections.emptyMap();

	private SimulationResult() {
		this.offsetMotion = null;
		this.intermittentResult = null;
		this.onGround = false;
		this.collidedHorizontally = false;
		this.collidedVertically = false;
		this.resetMotionX = false;
		this.resetMotionZ = false;
		this.step = false;
		this.edgeSneak = false;
		this.yStepHeight = 0;
		this.actualMotion = null;
	}

	public SimulationResult(
		Motion actualMotion,
		Motion offsetMotion,
		Motion intermittentResult,
		boolean onGround,
		boolean collidedHorizontally, boolean collidedVertically,
		boolean resetMotionX, boolean resetMotionZ,
		boolean step, boolean edgeSneak,
		double yStepHeight
	) {
		if (offsetMotion == null) {
			throw new IllegalArgumentException("Context cannot be null");
		}
		this.actualMotion = actualMotion;
		this.offsetMotion = offsetMotion;
		this.intermittentResult = intermittentResult;
		this.onGround = onGround;
		this.collidedHorizontally = collidedHorizontally;
		this.collidedVertically = collidedVertically;
		this.resetMotionX = resetMotionX;
		this.resetMotionZ = resetMotionZ;
		this.step = step;
		this.edgeSneak = edgeSneak;
		this.yStepHeight = yStepHeight;
	}

	public Motion actualMotion() {
		return actualMotion;
	}

	public Motion offsetMotion() {
		return offsetMotion;
	}

	public boolean offsetMotionDiffersFromActualMotion() {
		if (actualMotion == null) {
			return false;
		}
		if (offsetMotion == null) {
			return false;
		}
		return actualMotion().distance(offsetMotion()) > 0.001;
	}

	public boolean offsetMotionDiffersFromActualMotionInXZ() {
		if (actualMotion == null) {
			return false;
		}
		if (offsetMotion == null) {
			return false;
		}
		return actualMotion().horizontalDistance(offsetMotion()) > 0.001;
	}

	public boolean offsetMotionDiffersFromActualMotionInY() {
		if (actualMotion == null) {
			return false;
		}
		if (offsetMotion == null) {
			return false;
		}
		return Math.abs(actualMotion().motionY - offsetMotion().motionY) > 0.0000001;
	}

	public Motion intermittentResult() {
		return intermittentResult;
	}

	public boolean onGround() {
		return onGround;
	}

	public boolean collidedHorizontally() {
		return collidedHorizontally;
	}

	public boolean collidedVertically() {
		return collidedVertically;
	}

	public boolean step() {
		return step;
	}

	public boolean resetMotionX() {
		return resetMotionX;
	}

	public boolean resetMotionZ() {
		return resetMotionZ;
	}

	public boolean edgeSneak() {
		return edgeSneak;
	}

	public Motion edgeSneakMotion() {
		return actualMotion;
	}

	public double stepHeightThisMove() {
		return yStepHeight;
	}

	public void applyTo(
		SimulationEnvironment environment
	) {
		if (environment == null) {
			throw new IllegalArgumentException("Environment cannot be null");
		}
	}

	public void debugAttach(String key, double value) {
		if (IntaveControl.ENABLE_MOVEMENT_DEBUGGER_COLLECTOR) {
			debugData.put(key, value);
		}
	}

	public boolean isValid() {
		return this != INVALID_SIMULATION;
	}

	public Map<String, Double> debugData() {
		return debugData;
	}

	@Override
	public int hashCode() {
		int result = offsetMotion.hashCode();
		result = 31 * result + (intermittentResult != null ? intermittentResult.hashCode() : 0);
		result = 31 * result + (onGround ? 1 : 0);
		result = 31 * result + (collidedHorizontally ? 1 : 0);
		result = 31 * result + (collidedVertically ? 1 : 0);
		result = 31 * result + (resetMotionX ? 1 : 0);
		result = 31 * result + (resetMotionZ ? 1 : 0);
		result = 31 * result + (step ? 1 : 0);
		result = 31 * result + (edgeSneak ? 1 : 0);
		long temp = Double.doubleToLongBits(yStepHeight);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;

		SimulationResult that = (SimulationResult) obj;

		if (onGround != that.onGround) return false;
		if (collidedHorizontally != that.collidedHorizontally) return false;
		if (collidedVertically != that.collidedVertically) return false;
		if (resetMotionX != that.resetMotionX) return false;
		if (resetMotionZ != that.resetMotionZ) return false;
		if (step != that.step) return false;
		if (edgeSneak != that.edgeSneak) return false;
		if (Double.compare(that.yStepHeight, yStepHeight) != 0) return false;
		if (!offsetMotion.equals(that.offsetMotion)) return false;
		return Objects.equals(intermittentResult, that.intermittentResult);
	}

	public boolean almostIdenticalTo(
		SimulationResult other
	) {
		if (other == null) {
			return false;
		}
		if (this == other) {
			return true;
		}
		if (onGround != other.onGround) {
			return false;
		}
		if (collidedHorizontally != other.collidedHorizontally) {
			return false;
		}
		if (collidedVertically != other.collidedVertically) {
			return false;
		}
		if (resetMotionX != other.resetMotionX) {
			return false;
		}
		if (resetMotionZ != other.resetMotionZ) {
			return false;
		}
		if (step != other.step) {
			return false;
		}
		if (edgeSneak != other.edgeSneak) {
			return false;
		}
		if (!offsetMotion.almostIdentical(other.offsetMotion)) {
			return false;
		}
		if (intermittentResult != null ? !intermittentResult.almostIdentical(other.intermittentResult) : other.intermittentResult != null) {
			return false;
		}
		if (Math.abs(yStepHeight - other.yStepHeight) > 1e-6) {
			return false;
		}
		return true;
	}

	public long almostIdenticalHash() {
		long result = offsetMotion.almostIdenticalHash();
		result = 31 * result + (intermittentResult != null ? intermittentResult.almostIdenticalHash() : 0);
		result = 31 * result + (onGround ? 1 : 0);
		result = 31 * result + (collidedHorizontally ? 1 : 0);
		result = 31 * result + (collidedVertically ? 1 : 0);
		result = 31 * result + (resetMotionX ? 1 : 0);
		result = 31 * result + (resetMotionZ ? 1 : 0);
		result = 31 * result + (step ? 1 : 0);
		result = 31 * result + (edgeSneak ? 1 : 0);
		result = 31 * result + Double.hashCode(yStepHeight);
		return result;
	}

	@Override
	public String toString() {
		return "SimulationResult{" +
			"motion=" + offsetMotion +
			", intermittentResult=" + intermittentResult +
			", onGround=" + onGround +
			", collidedHorizontally=" + collidedHorizontally +
			", collidedVertically=" + collidedVertically +
			", resetMotionX=" + resetMotionX +
			", resetMotionZ=" + resetMotionZ +
			", step=" + step +
			", edgeSneak=" + edgeSneak +
			", yStepHeight=" + yStepHeight +
			'}';
	}

	public String tagString() {
		// only edgeSneak, resetMotionX/Z, & collided in a compact string
		return (
				(edgeSneak ? "E" : "-") +
				(resetMotionX ? "X" : "-") +
				(resetMotionZ ? "Z" : "-") +
				(collidedHorizontally ? "H" : "-") +
				(collidedVertically ? "V" : "-")
		);
	}

	public static SimulationResult invalid() {
		return INVALID_SIMULATION;
	}

	public static SimulationResult untouched(Motion motion) {
		return new SimulationResult(motion, motion, null, false, false, false, false, false, false, false, 0);
	}

	public SimulationResult copy() {
		if (this == INVALID_SIMULATION) {
			return INVALID_SIMULATION;
		}
		return new SimulationResult(actualMotion, offsetMotion, intermittentResult, onGround, collidedHorizontally, collidedVertically, resetMotionX, resetMotionZ, step, edgeSneak, yStepHeight);
	}
}
