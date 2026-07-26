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

package de.jpx3.intave.benchmark;

import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.MoveMetric;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;

import java.util.Locale;

public final class MutableSimulationEnvironmentViewBenchmark {
	private static final int DEFAULT_ITERATIONS = 10_000_000;
	private static final int DEFAULT_WARMUP_ITERATIONS = 10_000;
	private static final int SAMPLE_MASK = 255;
	private static volatile double sink;

	private MutableSimulationEnvironmentViewBenchmark() {
	}

	public static void main(String[] args) {
		BenchmarkOptions options = BenchmarkOptions.from(args);

		BenchmarkState warmupState = BenchmarkState.create();
		runIterations(warmupState, options.warmupIterations);

		BenchmarkState measuredState = BenchmarkState.create();
		long start = System.nanoTime();
		double checksum = runIterations(measuredState, options.iterations);
		long elapsedNanos = System.nanoTime() - start;
		sink = checksum;

		double nanosPerIteration = elapsedNanos / (double) options.iterations;
		double iterationsPerSecond = 1_000_000_000.0 / nanosPerIteration;
		System.out.println("MutableSimulationEnvironmentView benchmark");
		System.out.println("  iterations: " + options.iterations);
		System.out.println("  warmup iterations: " + options.warmupIterations);
		System.out.println("  elapsed: " + formatMillis(elapsedNanos) + " ms");
		System.out.println("  ns/iteration: " + ((int)nanosPerIteration));
		System.out.println("  iterations/s: " + format(iterationsPerSecond));
		System.out.println("  checksum: " + format(sink));
	}

	private static double runIterations(BenchmarkState state, int iterations) {
		double checksum = 0.0;
		for (int iteration = 0; iteration < iterations; iteration++) {
			int sample = iteration & SAMPLE_MASK;
			SimulationEnvironment view = state.source.mutableView();

			double x = state.positions[sample].getX();
			double y = state.positions[sample].getY();
			double z = state.positions[sample].getZ();
			view.updateMovement(
				x, y, z,
				state.yaws[sample],
				state.pitches[sample],
				true,
				true
			);
			view.setVerifiedLastPosition(state.verifiedPositions[sample], "benchmark");
			view.setBoundingBox(state.boxes[sample]);
			view.setBaseMotion(
				state.motions[sample].motionX(),
				state.motions[sample].motionY(),
				state.motions[sample].motionZ()
			);
			view.setInWater((iteration & 1) == 0);
			view.resetInWeb();
			view.addFallDistance(0.03125D);
			if ((iteration & 3) == 0) {
				view.resetFallDistance();
			}
			view.setJumpMotion(0.42D + sample * 0.0001D);
			view.setLastOnGround((iteration & 7) == 0);
			view.setPushedByEntity((iteration & 15) == 0);
			view.setSimulationResult(state.results[sample]);
			view.checkSupportingBlock(true, state.motions[sample]);
			view.compileSpecialBlocks();
			view.activeTick(MoveMetric.FLYING_PACKET_CLIENT);
			view.inactiveTick(MoveMetric.VELOCITY);
			view.tickComplete(true, true, true);

			view.commitTo(state.target);
			checksum += view.positionX();
			checksum += view.baseMotionY();
			checksum += view.ticks(MoveMetric.ALIVE);
			checksum += state.target.verifiedLastPositionZ();
			state.swap();
		}
		return checksum;
	}

	private static String formatMillis(long nanos) {
		return format(nanos / 1_000_000.0);
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private static final class BenchmarkState {
		private MockSimulationEnvironment source;
		private MockSimulationEnvironment target;
		private final Position[] positions = new Position[SAMPLE_MASK + 1];
		private final Position[] verifiedPositions = new Position[SAMPLE_MASK + 1];
		private final BoundingBox[] boxes = new BoundingBox[SAMPLE_MASK + 1];
		private final Motion[] motions = new Motion[SAMPLE_MASK + 1];
		private final SimulationResult[] results = new SimulationResult[SAMPLE_MASK + 1];
		private final float[] yaws = new float[SAMPLE_MASK + 1];
		private final float[] pitches = new float[SAMPLE_MASK + 1];

		private static BenchmarkState create() {
			BenchmarkState state = new BenchmarkState();
			state.source = createEnvironment(1.0D, 64.0D, 1.0D);
			state.target = createEnvironment(1.0D, 64.0D, 1.0D);
			state.fillSamples();
			return state;
		}

		private static MockSimulationEnvironment createEnvironment(double x, double y, double z) {
			MockSimulationEnvironment environment = new MockSimulationEnvironment();
			environment.setPositionX(x);
			environment.setPositionY(y);
			environment.setPositionZ(z);
			environment.copyPositionToLastPosition();
			environment.copyPositionToVerifiedPosition();
			environment.setBoundingBox(BoundingBox.fromBounds(x - 0.3D, y, z - 0.3D, x + 0.3D, y + 1.8D, z + 0.3D));
			environment.setBaseMotion(0.0D, 0.0D, 0.0D);
			environment.setOnGround(true);
			environment.setLastOnGround(true);
			return environment;
		}

		private void fillSamples() {
			for (int sample = 0; sample <= SAMPLE_MASK; sample++) {
				double x = 1.0D + sample * 0.015625D;
				double y = 64.0D + (sample & 7) * 0.015625D;
				double z = 1.0D - sample * 0.0078125D;
				positions[sample] = new Position(x, y, z);
				verifiedPositions[sample] = new Position(x - 0.08D, y, z + 0.08D);
				boxes[sample] = BoundingBox.fromBounds(x - 0.3D, y, z - 0.3D, x + 0.3D, y + 1.8D, z + 0.3D);
				motions[sample] = new Motion(sample * 0.0001D, 0.42D - sample * 0.00001D, -sample * 0.0001D);
				results[sample] = SimulationResult.untouched(motions[sample]);
				yaws[sample] = sample * 1.40625F;
				pitches[sample] = (sample & 31) - 16.0F;
			}
		}

		private void swap() {
			MockSimulationEnvironment previousSource = source;
			source = target;
			target = previousSource;
		}
	}

	private static final class BenchmarkOptions {
		private final int iterations;
		private final int warmupIterations;

		private BenchmarkOptions(int iterations, int warmupIterations) {
			this.iterations = iterations;
			this.warmupIterations = warmupIterations;
		}

		private static BenchmarkOptions from(String[] args) {
			int iterations = args.length >= 1 ? parsePositiveInt(args[0], "iterations") : DEFAULT_ITERATIONS;
			int warmupIterations = args.length >= 2
				? parsePositiveInt(args[1], "warmup iterations")
				: DEFAULT_WARMUP_ITERATIONS;
			return new BenchmarkOptions(iterations, warmupIterations);
		}

		private static int parsePositiveInt(String text, String name) {
			try {
				int value = Integer.parseInt(text);
				if (value <= 0) {
					throw new IllegalArgumentException(name + " must be greater than zero");
				}
				return value;
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException(name + " must be an integer: " + text, exception);
			}
		}
	}
}
