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

import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class TickSearch {
	private final Simulation simulation;
	private final int simulationCount;
	private final int searchDepth;
	private final Map<MovementConfiguration, Double> configurationDistances;

	public TickSearch(Simulation simulation, int simulationCount, int searchDepth) {
		this(simulation, simulationCount, searchDepth, Collections.emptyMap());
	}

	public TickSearch(
		Simulation simulation,
		int simulationCount,
		int searchDepth,
		Map<MovementConfiguration, Double> configurationDistances
	) {
		this.simulation = Objects.requireNonNull(simulation, "simulation");
		if (simulationCount < 0) {
			throw new IllegalArgumentException("simulationCount must not be negative");
		}
		if (searchDepth < 0) {
			throw new IllegalArgumentException("searchDepth must not be negative");
		}
		this.simulationCount = simulationCount;
		this.searchDepth = searchDepth;
		this.configurationDistances = Collections.unmodifiableMap(
			new HashMap<>(Objects.requireNonNull(configurationDistances, "configurationDistances"))
		);
	}

	public Simulation simulation() {
		return simulation;
	}

	public int simulationCount() {
		return simulationCount;
	}

	public int simulationsDone() {
		return simulationCount;
	}

	public int searchDepth() {
		return searchDepth;
	}

	public Map<MovementConfiguration, Double> configurationDistances() {
		return configurationDistances;
	}

	public boolean onlyPlausibleBranch(
		Predicate<MovementConfiguration> branch,
		boolean branchValue,
		double maximumDistance
	) {
		Objects.requireNonNull(branch, "branch");
		if (maximumDistance < 0 || Double.isNaN(maximumDistance)) {
			throw new IllegalArgumentException("maximumDistance must not be negative or NaN");
		}

		boolean falseEvaluated = false;
		boolean trueEvaluated = false;
		boolean falsePlausible = false;
		boolean truePlausible = false;
		for (Map.Entry<MovementConfiguration, Double> entry : configurationDistances.entrySet()) {
			boolean value = branch.test(entry.getKey());
			boolean plausible = entry.getValue() <= maximumDistance;
			if (value) {
				trueEvaluated = true;
				truePlausible |= plausible;
			} else {
				falseEvaluated = true;
				falsePlausible |= plausible;
			}
		}
		if (!falseEvaluated || !trueEvaluated) {
			return false;
		}
		return branchValue
			? truePlausible && !falsePlausible
			: falsePlausible && !truePlausible;
	}

	public boolean itemUseImpossible(double maximumDistance) {
		return onlyPlausibleBranch(
			MovementConfiguration::isHandActive, false, maximumDistance
		);
	}

	public boolean requiredItemUseImpossible(double maximumDistance) {
		if (maximumDistance < 0 || Double.isNaN(maximumDistance)) {
			throw new IllegalArgumentException("maximumDistance must not be negative or NaN");
		}

		boolean activeEvaluated = false;
		for (Map.Entry<MovementConfiguration, Double> entry : configurationDistances.entrySet()) {
			if (!entry.getKey().isHandActive()) {
				continue;
			}
			activeEvaluated = true;
			if (entry.getValue() <= maximumDistance) {
				return false;
			}
		}
		return activeEvaluated;
	}

	public boolean itemUseRequired(double maximumDistance) {
		return onlyPlausibleBranch(
			MovementConfiguration::isHandActive, true, maximumDistance
		);
	}

	public boolean isFromExhaustiveSearch() {
		return simulation.isFromExhaustiveSearch();
	}

	public TickSearch withAdditionalSimulations(int additionalSimulationCount) {
		if (additionalSimulationCount < 0) {
			throw new IllegalArgumentException("additionalSimulationCount must not be negative");
		}
		return new TickSearch(
			simulation,
			Math.addExact(simulationCount, additionalSimulationCount),
			searchDepth,
			configurationDistances
		);
	}

	TickSearch withAdditionalSearch(TickSearch additionalSearch) {
		Map<MovementConfiguration, Double> combinedDistances = new HashMap<>(configurationDistances);
		additionalSearch.configurationDistances.forEach((configuration, distance) ->
			combinedDistances.merge(configuration, distance, Math::min)
		);
		return new TickSearch(
			simulation,
			Math.addExact(simulationCount, additionalSearch.simulationCount),
			searchDepth,
			combinedDistances
		);
	}

	TickSearch withSimulation(Simulation selectedSimulation) {
		return new TickSearch(
			selectedSimulation, simulationCount, searchDepth, configurationDistances
		);
	}
}
