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

package de.jpx3.intave.check.movement.physics.search;

import de.jpx3.intave.annotate.Immutable;
import de.jpx3.intave.annotate.Mutable;
import de.jpx3.intave.block.tick.BlockTickEntities;
import de.jpx3.intave.check.movement.physics.branch.MovementSearchBranch;
import de.jpx3.intave.check.movement.physics.branch.MovementSearchBranchers;
import de.jpx3.intave.check.movement.physics.branch.MovementSearchInput;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.config.TraceImmutableMovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.PostTickSimulation;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.search.collector.BestSimulationSet;
import de.jpx3.intave.check.movement.physics.search.collector.ExhaustiveSimulationCollector;
import de.jpx3.intave.check.movement.physics.search.collector.MergingSimulationCollector;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.RateLimiter;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.search.Searcher;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import it.unimi.dsi.fastutil.longs.Long2LongMap;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

import static de.jpx3.intave.IntaveControl.FIRST_TICK_MUST_BE_FULLY_SIMULATED;
import static de.jpx3.intave.math.MathHelper.formatDouble;

public final class ThreeTickSimulationSearch implements SimulationSearch {
	private final static double STRICT_ACCURACY = 0.0001;

	private final static Searcher<MovementSearchInput, MovementSearchBranch> TICK_SEARCHER = new Searcher<>(
		MovementSearchBranchers.tick(),
		MovementSearchBranch::blank
	);

	private final static Searcher<MovementSearchInput, MovementSearchBranch> AFTER_TICK_SEARCHER = new Searcher<>(
		MovementSearchBranchers.afterTick(),
		MovementSearchBranch::blank
	);

	private final boolean detectNoSlowdown;

	public ThreeTickSimulationSearch(boolean detectNoSlowdown) {
		this.detectNoSlowdown = detectNoSlowdown;
	}

	@Override
	public Set<Simulation> exhaustiveTickSearch(
		User user, SimulationEnvironment environment, Simulator simulator
	) {
		Position receivedPosition = environment.position();
		Position lastPositionB4Flying = environment.lastPosition();

		RateLimiter ratelimiter = user.meta().movement().simulationRateLimiter;
		int maxFlyingSimulations = ratelimiter.isOverLimit() ? 4 : 36;
		int firstFlyingTickLimit = ratelimiter.isOverLimit() ? 256 : 512;
		int secondFlyingTickLimit = ratelimiter.isOverLimit() ? 0 : 256;

		BestSimulationSet bestSimulations = new BestSimulationSet();
		ExhaustiveSimulationCollector firstTickContainer = collectSimulations(
			user, simulator, environment,
			ExhaustiveSimulationCollector.forEnvironment(
				user, environment, lastPositionB4Flying, maxFlyingSimulations,
				bestSimulations, Simulation::offsetDifference
			),
			sim -> false
		);
		int totalSimulationsDone = firstTickContainer.simulationsDone();

		for (Simulation firstTickSimulation : firstTickContainer.flyingSimulations()) {
			if (totalSimulationsDone > firstFlyingTickLimit) {
				continue;
			}
			SimulationEnvironment firstTickEnvironment = firstTickSimulation.environment().mutableView();
			Simulator secondTickSimulator = simulator.simulateAround(
				user, firstTickEnvironment, firstTickSimulation,
				receivedPosition, environment.rotation()
			);

			ExhaustiveSimulationCollector secondTickContainer = collectSimulations(
				user, secondTickSimulator, firstTickEnvironment,
				ExhaustiveSimulationCollector.forEnvironment(
					user, firstTickEnvironment, lastPositionB4Flying, maxFlyingSimulations,
					bestSimulations, sim -> sim.positionDifference(receivedPosition)
				),
				sim -> false
			);
			totalSimulationsDone += secondTickContainer.simulationsDone();

			for (Simulation secondTickFlyingSimulation : secondTickContainer.flyingSimulations()) {
				if (totalSimulationsDone > secondFlyingTickLimit) {
					continue;
				}
				SimulationEnvironment secondTickEnvironment = secondTickFlyingSimulation.environment().mutableView();
				Simulator thirdTickSimulator = secondTickSimulator.simulateAround(
					user, secondTickEnvironment, secondTickFlyingSimulation,
					receivedPosition, environment.rotation()
				);

				ExhaustiveSimulationCollector thirdTickContainer = collectSimulations(
					user, thirdTickSimulator, secondTickEnvironment,
					ExhaustiveSimulationCollector.forEnvironment(
						user, secondTickEnvironment, lastPositionB4Flying, maxFlyingSimulations,
						bestSimulations, sim -> sim.positionDifference(receivedPosition)
					),
					sim -> false
				);
				totalSimulationsDone += thirdTickContainer.simulationsDone();
			}
		}
		return bestSimulations.simulations();
	}

	public Simulation exactFlyingPacketSearch(
		User user,
		SimulationEnvironment environment,
		Simulator simulator,
		int precedingFlyingPackets
	) {
		// Recordings know the exact count; production fuzzy search intentionally does not.
		if (precedingFlyingPackets < 0 || precedingFlyingPackets > 2) {
			throw new IllegalArgumentException(
				"precedingFlyingPackets must be between 0 and 2"
			);
		}

		Position receivedPosition = environment.position();
		Position lastReportedPosition = environment.lastPosition();
		int maxFlyingSimulations = 36;
		MergingSimulationCollector firstTickContainer = collectSimulations(
			user, simulator, environment,
			MergingSimulationCollector.forEnvironment(user, environment, maxFlyingSimulations),
			simulation -> false
		);
		int totalSimulationsDone = firstTickContainer.simulationsDone();
		if (precedingFlyingPackets == 0) {
			return finishExactSearch(
				user, firstTickContainer.bestSimulation(), 0, totalSimulationsDone
			);
		}

		Simulation bestSimulation = Simulation.invalid();
		for (Simulation firstTickSimulation : firstTickContainer.flyingSimulations()) {
			SimulationEnvironment firstTickEnvironment = firstTickSimulation.environment().mutableView();
			Simulator secondTickSimulator = simulator.simulateAround(
				user, firstTickEnvironment, firstTickSimulation,
				receivedPosition, environment.rotation()
			);
			MergingSimulationCollector secondTickContainer = collectSimulations(
				user, secondTickSimulator, firstTickEnvironment,
				MergingSimulationCollector.forEnvironmentWithCustomTargets(
					user, firstTickEnvironment, firstTickEnvironment.sentOffsetMotion(),
					lastReportedPosition, maxFlyingSimulations
				),
				simulation -> false
			);
			totalSimulationsDone += secondTickContainer.simulationsDone();
			if (precedingFlyingPackets == 1) {
				bestSimulation = bestSimulation.select(
					secondTickContainer.bestSimulation(), receivedPosition
				);
				continue;
			}

			for (Simulation secondTickFlyingSimulation : secondTickContainer.flyingSimulations()) {
				SimulationEnvironment secondTickEnvironment = secondTickFlyingSimulation.environment().mutableView();
				Simulator thirdTickSimulator = secondTickSimulator.simulateAround(
					user, secondTickEnvironment, secondTickFlyingSimulation,
					receivedPosition, environment.rotation()
				);
				MergingSimulationCollector thirdTickContainer = collectSimulations(
					user, thirdTickSimulator, secondTickEnvironment,
					MergingSimulationCollector.forEnvironmentWithCustomTargets(
						user, secondTickEnvironment, secondTickEnvironment.sentOffsetMotion(),
						lastReportedPosition, maxFlyingSimulations
					),
					simulation -> false
				);
				totalSimulationsDone += thirdTickContainer.simulationsDone();
				bestSimulation = bestSimulation.select(
					thirdTickContainer.bestSimulation(), receivedPosition
				);
			}
		}
		return finishExactSearch(
			user, bestSimulation, precedingFlyingPackets, totalSimulationsDone
		);
	}

	public Simulation positionlessFlyingPacketSearch(
		User user,
		SimulationEnvironment environment,
		Simulator simulator
	) {
		// A positionless packet may only select a branch below the client's send threshold.
		MergingSimulationCollector simulations = collectSimulations(
			user, simulator, environment,
			MergingSimulationCollector.forEnvironment(user, environment, 36),
			simulation -> false
		);
		Position lastReportedPosition = environment.lastPosition();
		Simulation bestSimulation = simulations.flyingSimulations().stream()
			.min(Comparator
				.comparingDouble((Simulation simulation) ->
					simulation.positionDifference(lastReportedPosition)
				)
				.thenComparing(simulation -> simulation.configuration().toString())
				.thenComparingDouble(simulation -> simulation.offsetMotion().motionX)
				.thenComparingDouble(simulation -> simulation.offsetMotion().motionY)
				.thenComparingDouble(simulation -> simulation.offsetMotion().motionZ)
			)
			.orElse(Simulation.invalid());
		if (bestSimulation == Simulation.invalid()) {
			return bestSimulation;
		}
		bestSimulation.appendBlue("pf/" + simulations.simulationsDone() + "es");
		bestSimulation.setSimulationCount(simulations.simulationsDone());
		bestSimulation.setSearchDepth(0);
//		applySimulation(user, bestSimulation);
		user.meta().movement().simulationRateLimiter.noteAcquired(simulations.simulationsDone());
		return bestSimulation;
	}

	private Simulation finishExactSearch(
		User user,
		Simulation simulation,
		int precedingFlyingPackets,
		int totalSimulationsDone
	) {
		if (simulation == Simulation.invalid()) {
			return simulation;
		}
		simulation.appendBlue(
			precedingFlyingPackets + "f/" + totalSimulationsDone + "es"
		);
		simulation.setSimulationCount(totalSimulationsDone);
		simulation.setSearchDepth(precedingFlyingPackets);
//		applySimulation(user, simulation);
		user.meta().movement().simulationRateLimiter.noteAcquired(totalSimulationsDone);
		return simulation;
	}

	@Override
	public TickSearch tickSearch(
		User user, SimulationEnvironment movementData,
		Simulator simulator, SimulationSearchOptions options
	) {
		Position receivedPosition = movementData.position();
		Position lastPositionB4Flying = movementData.lastPosition();

		boolean likelyInaccurate = likelyInaccurate(user, movementData);
		boolean allowFuzziness = options.allowFuzziness();
		double requiredAccuracyFirstTick = STRICT_ACCURACY;
		double requiredAccuracySecondTick = likelyInaccurate && allowFuzziness ? 0.03 : STRICT_ACCURACY;
		double requiredAccuracyThirdTick = likelyInaccurate && allowFuzziness ? 0.04 : STRICT_ACCURACY;

		RateLimiter ratelimiter = user.meta().movement().simulationRateLimiter;

		int maxFlyingSimulations = ratelimiter.isOverLimit() ? 4 : 36;
		int firstFlyingTickLimit = ratelimiter.isOverLimit() ? 256 : 512;
		int secondFlyingTickLimit = ratelimiter.isOverLimit() ? 0 : 256;
		Map<MovementConfiguration, Double> configurationDistances = new HashMap<>();
		BiConsumer<MovementSearchBranch, Simulation> optionalBnSConsumer = (configuration, simulation) -> {
			if (simulation.canFinishExplicitTick()) {
				configurationDistances.merge(
					configuration.moveConfig(),
					simulation.positionDifference(receivedPosition),
					ThreeTickSimulationSearch::min
				);
			}
		};

		// Go through all this-tick possibilities
		MergingSimulationCollector firstTickContainer = collectSimulations(
			user, simulator, movementData,
			MergingSimulationCollector.forEnvironment(user, movementData, maxFlyingSimulations),
			sim -> sim.offsetDifference() < requiredAccuracyFirstTick && !FIRST_TICK_MUST_BE_FULLY_SIMULATED,
			optionalBnSConsumer
		);

		int totalSimulationsDone = firstTickContainer.simulationsDone();
		long start = System.nanoTime();

		List<Simulation> firstTickFlyingSimulations = firstTickContainer.flyingSimulations();
		Simulation bestSimulation = firstTickContainer.bestSimulation();
		bestSimulation.setSearchDepth(0);

		if (firstTickFlyingSimulations.isEmpty() || bestSimulation.offsetDifference() < requiredAccuracyFirstTick) {
			if (bestSimulation.canFinishExplicitTick()) {
				if (totalSimulationsDone > 1) {
					bestSimulation.appendBlue(totalSimulationsDone + "as");
				}
				double durationMs = ((double) System.nanoTime() - start) / 1_000_000d;
				if (durationMs > 0.1) {
					bestSimulation.appendBlue(formatDouble(durationMs, 2) + "ms");
				}
				bestSimulation.setSimulationCount(totalSimulationsDone);
//				applySimulation(user, bestSimulation);
				if (firstTickFlyingSimulations.isEmpty()) {
					bestSimulation.setWasFromExhaustiveSearch();
				}
				ratelimiter.noteAcquired(totalSimulationsDone);
				return new TickSearch(
					bestSimulation, totalSimulationsDone, 0,
					configurationDistances
				);
			}
		}

		double bestDistance = bestSimulation.offsetDifference();
		for (Simulation firstTickSimulation : firstTickFlyingSimulations) {
			// If simulating take too long, we can not search that deep
			if (totalSimulationsDone > firstFlyingTickLimit) {
				continue;
			}
			SimulationEnvironment firstTickEnvironment = firstTickSimulation.environment().mutableView();
			Simulator secondTickSimulator = simulator.simulateAround(
				user, firstTickEnvironment, firstTickSimulation,
				receivedPosition, movementData.rotation()
			);

			Motion secondTickRemainingMotion = firstTickEnvironment.sentOffsetMotion();
			MergingSimulationCollector secondTickContainer = collectSimulations(
				user, secondTickSimulator, firstTickEnvironment,
				MergingSimulationCollector.forEnvironmentWithCustomTargets(
					user, firstTickEnvironment, secondTickRemainingMotion, lastPositionB4Flying, maxFlyingSimulations
				),
				sim -> sim.positionDifference(receivedPosition) < requiredAccuracySecondTick,
				optionalBnSConsumer
			);
			totalSimulationsDone += secondTickContainer.simulationsDone();

			Simulation secondTickSimulation = secondTickContainer.bestSimulation();
			secondTickSimulation.appendBlue("1f/" + firstTickFlyingSimulations.size() + "x");
			secondTickSimulation.setSearchDepth(1);

			double secondTickDistance = secondTickSimulation.positionDifference(receivedPosition);
			if (secondTickDistance < bestDistance && secondTickSimulation.canFinishExplicitTick()) {
				bestSimulation = secondTickSimulation.reusableCopy();
				bestDistance = secondTickDistance;
			}

			if (bestDistance < requiredAccuracyThirdTick) {
				if (totalSimulationsDone > 1) {
					bestSimulation.appendBlue(totalSimulationsDone + "bs");
				}
				double durationMs = ((double) System.nanoTime() - start) / 1_000_000d;
				if (durationMs > 0.1) {
					bestSimulation.appendBlue(formatDouble(durationMs, 2) + "ms");
				}
				bestSimulation.setSimulationCount(totalSimulationsDone);
//				applySimulation(user, bestSimulation);
				ratelimiter.noteAcquired(totalSimulationsDone);
				return new TickSearch(
					bestSimulation, totalSimulationsDone, bestSimulation.searchDepth(),
					configurationDistances
				);
			}

			List<Simulation> secondTickFlyingCandidates = secondTickContainer.flyingSimulations();

			for (Simulation secondTickFlyingSimulation : secondTickFlyingCandidates) {
				// If simulating take too long, we can not search that deep
				if (totalSimulationsDone > secondFlyingTickLimit) {
					continue;
				}

				SimulationEnvironment secondTickEnvironment = secondTickFlyingSimulation.environment().mutableView();
				Simulator thirdTickSimulator = secondTickSimulator.simulateAround(
					user, secondTickEnvironment, secondTickFlyingSimulation,
					receivedPosition, movementData.rotation()
				);

				Motion thirdTickRemainingMotion = secondTickEnvironment.sentOffsetMotion();
				MergingSimulationCollector thirdTickContainer = collectSimulations(
					user, thirdTickSimulator, secondTickEnvironment,
					MergingSimulationCollector.forEnvironmentWithCustomTargets(
						user, secondTickEnvironment, thirdTickRemainingMotion, lastPositionB4Flying, maxFlyingSimulations
					),
					sim -> sim.positionDifference(receivedPosition) < requiredAccuracyThirdTick,
					optionalBnSConsumer
				);
				totalSimulationsDone += thirdTickContainer.simulationsDone();

				Simulation thirdTickSimulation = thirdTickContainer.bestSimulation();
				thirdTickSimulation.appendBlue("2f/" + secondTickFlyingCandidates.size() + "x");
				thirdTickSimulation.setSearchDepth(2);
				double thirdTickDistance = thirdTickSimulation.positionDifference(receivedPosition);
				if (thirdTickDistance < bestDistance && thirdTickSimulation.canFinishExplicitTick()) {
					bestSimulation = thirdTickSimulation.reusableCopy();
					bestDistance = thirdTickDistance;
				}

				if (bestDistance < requiredAccuracyThirdTick) {
					if (totalSimulationsDone > 1) {
						bestSimulation.appendBlue(totalSimulationsDone + "cs");
					}
					double durationMs = ((double) System.nanoTime() - start) / 1_000_000d;
					if (durationMs > 0.1) {
						bestSimulation.appendBlue(formatDouble(durationMs, 2) + "ms");
					}
					bestSimulation.setSimulationCount(totalSimulationsDone);
//					applySimulation(user, bestSimulation);
					ratelimiter.noteAcquired(totalSimulationsDone);
					return new TickSearch(
						bestSimulation, totalSimulationsDone, bestSimulation.searchDepth(),
						configurationDistances
					);
				}
			}
		}
		if (totalSimulationsDone > 1) {
			bestSimulation.appendBlue(totalSimulationsDone + "ds");
		}
		double durationMs = ((double) System.nanoTime() - start) / 1_000_000d;
		if (durationMs > 0.1) {
			bestSimulation.appendBlue(formatDouble(durationMs, 2) + "ms");
		}
		bestSimulation.setWasFromExhaustiveSearch();
		bestSimulation.setSimulationCount(totalSimulationsDone);
//		applySimulation(user, bestSimulation);
		ratelimiter.noteAcquired(totalSimulationsDone);
		return new TickSearch(
			bestSimulation, totalSimulationsDone, bestSimulation.searchDepth(),
			configurationDistances
		);
	}

	@Override
	public List<PostTickSimulation> afterTickMotionCandidates(
		@Immutable User user, @Mutable SimulationEnvironment environment,
		@Immutable Simulator simulator, @Immutable Position position,
		@Immutable PostTickMotionType motionType
	) {
		SimulationEnvironment branchEnv = environment.mutableView();
		SimulationResult result = environment.simulationResult();

		Motion afterTickInputMotion = motionType == PostTickMotionType.SIMULATED_MOTION
			? result.actualMotion()
			: environment.sentOffsetMotion();

		MovementConfiguration last = environment.lastMovementConfiguration();
		TraceImmutableMovementConfiguration trace = last.withRecording();

		Motion outputMotion = simulator.simulateAfterTick(
			user, branchEnv, trace,
			position, afterTickInputMotion.copy()
		);
		outputMotion = BlockTickEntities.tick(
			user, branchEnv, position, outputMotion
		);

		// Entity.updateSwimming consumes this tick's sprint state during the next
		// base tick, even when after-tick motion itself did not read sprinting.
		if (user.meta().protocol().swimmingMechanics()) {
			trace.isSprinting();
		}

		// write to the active environment
		// this is a safety measure in case we drop/forget to run LastPostTickCandidateBrancher next tick
		branchEnv.setBaseMotion(outputMotion);

		// If the afterTick does not depend on the movementConfiguration, we can just return the outputMotion and not bother with the search
		if (!trace.requiredAnyState()) {
			branchEnv.commitTo(environment);
			return Collections.singletonList(
				new PostTickSimulation(outputMotion, last.isSprinting())
			);
		}

		// Now we can search for all possible movement configurations that we possibly didn't check in the tick search.
		Set<MovementSearchBranch> branches = AFTER_TICK_SEARCHER.searchConfigurationsFor(
			MovementSearchInput.forAfterTick(user, simulator, environment, detectNoSlowdown, trace) // trace to restrict
		);

		if (branches.isEmpty()) {
			branchEnv.commitTo(environment);
			return Collections.singletonList(
				new PostTickSimulation(outputMotion, last.isSprinting())
			);
		}

		List<PostTickSimulation> candidates = new ArrayList<>();
		candidates.add(new PostTickSimulation(outputMotion, last.isSprinting()));

		for (MovementSearchBranch branch : branches) {
			SimulationEnvironment disposable = environment.mutableView();
			// see below
//			disposable = branch.applyTo(disposable);
			Motion motion = simulator.simulateAfterTick(
				user, disposable,
				branch.moveConfig(),
				position,
				afterTickInputMotion.copy()
			);
			motion = BlockTickEntities.tick(
				user, disposable, position, motion
			);
			// see below
//			disposable.commitTo(environment);

			addCandidateIfUnique(
				candidates,
				new PostTickSimulation(motion, branch.moveConfig().isSprinting())
			);
		}


		// Okay this might be a bit confusing.
		// We opted not to branch each possible environment of the simulateAfterTick calls above,
		// because there is just too much still happening in between ticks
		// we do not properly account for, and we currently don't have an applyAll(env -> {}) method.
		//
		// Also, no environment variables currently depend on the movement config.
		// Should this change, we have to properly support this (or we rely on rollbacking here?).
		branchEnv.commitTo(environment);

		return candidates;
	}

	private static void addCandidateIfUnique(
		List<PostTickSimulation> candidates,
		PostTickSimulation candidate
	) {
		for (PostTickSimulation existing : candidates) {
			if (existing.sameAs(candidate)) {
				return;
			}
		}
		candidates.add(candidate);
	}

	private boolean likelyInaccurate(User user, SimulationEnvironment movementData) {
		if (user.meta().protocol().flyingPacketUncertaintyRadius() < 0.00001) {
			return false;
		}
		if (Math.abs(movementData.offsetMotionY()) < 0.05
			&& Math.abs(movementData.offsetMotionX()) < 0.05 && Math.abs(movementData.offsetMotionZ()) < 0.05) {
			return true;
		}
		return movementData.isSneaking() || movementData.inWater();
	}

	private <C, R> R collectSimulations(
		User user, Simulator simulator,
		SimulationEnvironment environment,
		Collector<Simulation, C, R> collector,
		Predicate<Simulation> earlyStop
	) {
		return collectSimulations(
			user, simulator, environment, collector, earlyStop, null
		);
	}

	private <C, R> R collectSimulations(
		User user, Simulator simulator,
		SimulationEnvironment environment,
		Collector<Simulation, C, R> collector,
		Predicate<Simulation> earlyStop,
		BiConsumer<MovementSearchBranch, Simulation> optionalBnSConsumer
	) {
		Timings.CHECK_PHYSICS_PROC_ITR.start();
		Timings.CHECK_PHYSICS_PROC_ITR_BUILD_CONFIGS.start();
		Set<MovementSearchBranch> possibleConfigs = TICK_SEARCHER.searchConfigurationsFor(
			MovementSearchInput.forTick(user, simulator, environment.immutableView(), detectNoSlowdown)
		);
		List<MovementSearchBranch> sortedConfigs = sortByFrequency(user, possibleConfigs);
		Timings.CHECK_PHYSICS_PROC_ITR_BUILD_CONFIGS.stop();

		C container = collector.supplier().get();
		Function<C, R> finisher = collector.finisher();
		BiConsumer<C, Simulation> accumulator = collector.accumulator();

		for (MovementSearchBranch config : sortedConfigs) {
			boolean canFinishExplicitTick = config.canFinishExplicitTick();
			SimulationEnvironment localEnvironment = config.modifiedMutableView(environment);
			Simulation simulation = simulator.simulateTick(
				user, localEnvironment.mutableBaseMotionCopy(),
				localEnvironment.immutableView(), config.moveConfig()
			);
			simulation.setEnvironment(localEnvironment);
			simulation.setCanFinishExplicitTick(canFinishExplicitTick);
			simulation.setBranchFrequencyKey(config.frequencyKey());
			accumulator.accept(container, simulation);
			if (optionalBnSConsumer != null) {
				optionalBnSConsumer.accept(config, simulation);
			}
			if (canFinishExplicitTick && earlyStop.test(simulation)) {
				break;
			}
			simulation.expire();
		}
		Timings.CHECK_PHYSICS_PROC_ITR.stop();
		return finisher.apply(container);
	}

	private static double min(double first, double second) {
		if (!Double.isFinite(first)) {
			return second;
		}
		if (!Double.isFinite(second)) {
			return first;
		}
		return Math.min(first, second);
	}

	private static List<MovementSearchBranch> sortByFrequency(
		User user, Set<MovementSearchBranch> branches
	) {
		MovementMetadata movement = user.meta().movement();
		MovementSearchBranch[] sorted = branches.toArray(new MovementSearchBranch[0]);

		if (sorted.length < 2) {
			return Arrays.asList(sorted);
		}
		Long2LongMap frequencies = movement.branchFrequency;
		Arrays.sort(sorted, (left, right) -> Long.compare(
			frequencies.get(right.frequencyKey()),
			frequencies.get(left.frequencyKey())
		));
		return Arrays.asList(sorted);
	}
}
