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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class TickSearchTest {
	@Test
	void exposesTheSimulationAndSearchMetadata() {
		Simulation simulation = Simulation.invalid();

		TickSearch search = new TickSearch(simulation, 42, 2);

		assertSame(simulation, search.simulation());
		assertEquals(42, search.simulationCount());
		assertEquals(42, search.simulationsDone());
		assertEquals(2, search.searchDepth());
	}

	@Test
	void rejectsNegativeSearchMetadata() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new TickSearch(Simulation.invalid(), -1, 0)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new TickSearch(Simulation.invalid(), 0, -1)
		);
	}

	@Test
	void defensivelyCopiesConfigurationDistances() {
		MovementConfiguration configuration = MovementConfiguration.blank();
		Map<MovementConfiguration, Double> distances = new HashMap<>();
		distances.put(configuration, 0.25);

		TickSearch search = new TickSearch(Simulation.invalid(), 42, 2, distances);
		distances.put(configuration, 0.5);

		assertEquals(0.25, search.configurationDistances().get(configuration));
		assertThrows(
			UnsupportedOperationException.class,
			() -> search.configurationDistances().put(configuration, 0.75)
		);
	}

	@Test
	void derivesItemUsePlausibilityFromConfigurationDistances() {
		MovementConfiguration inactive = MovementConfiguration.blank();
		MovementConfiguration active = inactive.withHandActive(true);
		Map<MovementConfiguration, Double> distances = new HashMap<>();
		distances.put(inactive, 0.5);
		distances.put(active, 0.01);

		TickSearch search = new TickSearch(Simulation.invalid(), 2, 0, distances);

		assertTrue(search.itemUseRequired(0.1));
		assertFalse(search.itemUseImpossible(0.1));
	}

	@Test
	void itemUsePlausibilityRequiresBothBranchesToBeEvaluated() {
		MovementConfiguration active = MovementConfiguration.blank().withHandActive(true);
		Map<MovementConfiguration, Double> distances = new HashMap<>();
		distances.put(active, 0.01);

		TickSearch search = new TickSearch(Simulation.invalid(), 1, 0, distances);

		assertFalse(search.itemUseRequired(0.1));
		assertFalse(search.itemUseImpossible(0.1));
	}

	@Test
	void detectsImpossiblePacketRequiredItemUseWithoutAnInactiveBranch() {
		MovementConfiguration inactive = MovementConfiguration.blank();
		MovementConfiguration active = inactive.withHandActive(true);
		Map<MovementConfiguration, Double> distances = new HashMap<>();
		distances.put(active, 0.5);

		TickSearch search = new TickSearch(Simulation.invalid(), 1, 0, distances);

		assertTrue(search.requiredItemUseImpossible(0.1));
		assertFalse(search.requiredItemUseImpossible(0.5));

		distances.clear();
		distances.put(inactive, 0.01);
		TickSearch inactiveOnly = new TickSearch(Simulation.invalid(), 1, 0, distances);
		assertFalse(inactiveOnly.requiredItemUseImpossible(0.1));
	}

	@Test
	void ambiguousItemUseIsNeitherRequiredNorImpossible() {
		MovementConfiguration inactive = MovementConfiguration.blank();
		MovementConfiguration active = inactive.withHandActive(true);
		Map<MovementConfiguration, Double> distances = new HashMap<>();
		distances.put(inactive, 0.01);
		distances.put(active, 0.01);

		TickSearch search = new TickSearch(Simulation.invalid(), 2, 0, distances);

		assertFalse(search.itemUseRequired(0.1));
		assertFalse(search.itemUseImpossible(0.1));
	}
}
