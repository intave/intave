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

package de.jpx3.intave.check.movement.physics.recording;

import de.jpx3.intave.check.movement.physics.branch.MovementSearchBranch;
import de.jpx3.intave.check.movement.physics.branch.MovementSearchBranchers;
import de.jpx3.intave.check.movement.physics.branch.MovementSearchInput;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.search.SearchBrancher;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Turns every PTR replay into an interactive branch trace under
 * {@code build/reports/ptr-branching}.
 */
@Execution(ExecutionMode.SAME_THREAD)
final class PtrBranchingVisualizationTest {
	private static final String RECORDING_ROOT = "physics_test_runs/";
	private static final Path OUTPUT_ROOT = Path.of("build", "reports", "ptr-branching");
	private static final int SAMPLE_CONFIGURATIONS = 4;
	private static final int BLOCK_RADIUS = 4;
	private static final int THREE_TICK_SEARCH_LAST_DEPTH = 2;
	private static final double EXACT_BRANCH_LOSS = 0.0001;
	private static final double ACCEPTED_BRANCH_LOSS = 0.01;

	@TestFactory
	Stream<DynamicTest> writesInteractiveBranchTracesForAllRecordings() throws IOException {
		return MovementRecordingPhysicsTests.findMovementRecordings().stream()
			.filter(PtrBranchingVisualizationTest::shouldVisualize)
			.map(path -> {
				String resource = MovementRecordingPhysicsTests.resourcePathOf(path);
				return dynamicTest(resource, () -> writeInteractiveBranchTrace(resource));
			});
	}

	private static boolean shouldVisualize(Path recordingPath) {
		return !recordingPath.getFileName().toString().startsWith("_");
	}

	private static void writeInteractiveBranchTrace(String resource) throws IOException {
		MovementRecording recording = MovementRecording.loadFrom(Resources.resourceFromJarOrTestBuild(resource));
		List<TickTrace> ticks = new ArrayList<>();
		try {
			MovementRecordingPhysicsTests.processRecordingResource(resource, (tick, selected, actualMotion) -> ticks.add(traceTick(tick, selected, actualMotion)));
		} catch (AssertionError failure) {
			if (!ticks.isEmpty()) {
				int failingTick = ticks.getLast().tick();
				Path output = writeReport(resource, recording, ticks);
				printReportLink(output, failingTick);
			}
			throw failure;
		}

		Path output = writeReport(resource, recording, ticks);
		System.out.println("PTR branching visualization: " + output.toAbsolutePath());
	}

	static Path writeFailureReport(String resource, MovementRecording recording, int tick, Simulation selected, Motion actualMotion) throws IOException {
		return writeReport(resource, recording, List.of(traceTick(tick, selected, actualMotion)));
	}

	static String printReportLink(Path output, int tick) {
		String reportUri = reportUri(output, tick);
		System.out.println("[REPORT] Tick " + tick + ": " + reportUri);
		return reportUri;
	}

	private static String reportUri(Path output, int tick) {
		return output.toAbsolutePath().normalize().toUri() + "?tick=" + tick;
	}

	private static Path writeReport(String resource, MovementRecording recording, List<TickTrace> ticks) throws IOException {
		Path output = outputPath(resource);
		Files.createDirectories(output.getParent());
		Files.writeString(output, HTML.replace("__TRACE_DATA__", toJson(ticks)).replace("__RECORDING__", resource).replace("__RECORDING_NAME__", recordingName(resource)).replace("__CLIENT_PROTOCOL__", Integer.toString(recording.clientProtocolVersion())).replace("__SERVER_VERSION__", recording.serverVersion().getVersion()).replace("__ASSET_VERSION__", recording.serverVersion().getVersion()), StandardCharsets.UTF_8);
		return output;
	}

	@Test
	void reportLinkIncludesExactTick() {
		assertTrue(reportUri(Path.of("build", "reports", "ptr-branching", "sample.html"), 37).endsWith("sample.html?tick=37"));
	}

	@Test
	void underscorePrefixedRecordingsAreNotVisualized() {
		assertFalse(shouldVisualize(Path.of("nested", "_disabled.ptr")));
		assertTrue(shouldVisualize(Path.of("enabled.ptr")));
	}

	private static Path outputPath(String resource) {
		if (!resource.startsWith(RECORDING_ROOT) || !resource.endsWith(".ptr")) {
			throw new IllegalArgumentException("Unexpected PTR resource path: " + resource);
		}
		String relative = resource.substring(RECORDING_ROOT.length(), resource.length() - ".ptr".length()) + ".html";
		return OUTPUT_ROOT.resolve(Path.of(relative));
	}

	private static String recordingName(String resource) {
		int start = resource.lastIndexOf('/') + 1;
		return resource.substring(start, resource.length() - ".ptr".length()).replace('_', ' ');
	}

	private static TickTrace traceTick(int tick, Simulation selected, Motion actualMotion) {
		SimulationEnvironment selectedEnvironment = selected.environment();
		long selectedFrequencyKey = selected.branchFrequencyKey();
		Motion selectedPredictedMotion = selected.offsetMotion().copy();
		int selectedSimulationCount = selected.simulationCount();
		int selectedSearchDepth = selected.searchDepth();
		String selectedConfiguration = formatConfiguration(selected.configuration());
		User user = selectedEnvironment.user();
		SimulationEnvironment rootEnvironment = user.meta().movement().immutableView();
		Simulator simulator = rootEnvironment.simulator();
		MovementSearchInput input = MovementSearchInput.forTick(user, simulator, rootEnvironment, false);

		Set<MovementSearchBranch> current = new HashSet<>();
		MovementSearchBranch blank = MovementSearchBranch.blank(input);
		current.add(blank);
		List<MutableStage> mutableStages = new ArrayList<>();
		mutableStages.add(MutableStage.start(blank));

		for (SearchBrancher<MovementSearchInput, MovementSearchBranch> brancher : MovementSearchBranchers.tick()) {
			Set<MovementSearchBranch> next = new HashSet<>();
			MutableStage stage = new MutableStage(friendlyName(brancher), current.size());
			for (MovementSearchBranch parent : current) {
				int attemptsBefore = stage.attemptedChildren;
				Collection<MovementSearchBranch> recordingOutput = new RecordingOutput(next, parent, stage);
				brancher.branch(input, parent, recordingOutput);
				int fanOut = stage.attemptedChildren - attemptsBefore;
				stage.fanOut.merge(fanOut, 1, Integer::sum);
			}
			if (next.isEmpty()) {
				throw new IllegalStateException(brancher.getClass().getSimpleName() + " produced no branches at tick " + tick);
			}
			stage.captureOutputs(next);
			mutableStages.add(stage);
			current = next;
		}

		boolean winnerIsInFirstLayer = selectedSearchDepth == 0 && current.stream().anyMatch(branch -> branch.frequencyKey() == selectedFrequencyKey);
		if (winnerIsInFirstLayer) {
			long winnerAtStage = selectedFrequencyKey;
			for (int stageIndex = mutableStages.size() - 1; stageIndex > 0; stageIndex--) {
				MutableStage stage = mutableStages.get(stageIndex);
				stage.winnerFrequencyKey = winnerAtStage;
				winnerAtStage = stage.predecessors.getOrDefault(winnerAtStage, 0L);
			}
			mutableStages.get(0).winnerFrequencyKey = blank.frequencyKey();
		}

		List<StageTrace> stages = mutableStages.stream().map(MutableStage::freeze).toList();
		EnvironmentTrace environment = captureEnvironment(user, rootEnvironment);
		List<BranchTrace> branches = simulateBranches(user, rootEnvironment, simulator, current, rootEnvironment.position(), selectedFrequencyKey, winnerIsInFirstLayer);
		MultiTickTrace multiTick = traceMultiTickSearch(user, rootEnvironment, simulator, selected, current);
		double loss = selected.positionDifference(rootEnvironment.position());
		return new TickTrace(tick, loss, selectedSimulationCount, selectedSearchDepth, current.size(), winnerIsInFirstLayer, selectedConfiguration, formatMotion(selectedPredictedMotion), formatMotion(actualMotion), actualMotion.motionX(), actualMotion.motionY(), actualMotion.motionZ(), stages, branches, environment, multiTick);
	}

	private static List<BranchTrace> simulateBranches(User user, SimulationEnvironment rootEnvironment, Simulator simulator, Set<MovementSearchBranch> branches, Position targetPosition, long selectedFrequencyKey, boolean winnerIsInFirstLayer) {
		List<BranchTrace> results = new ArrayList<>(branches.size());
		for (MovementSearchBranch branch : branches) {
			SimulationEnvironment branchEnvironment = branch.modifiedMutableView(rootEnvironment);
			Simulation simulation = simulator.simulateTick(user, branchEnvironment.mutableBaseMotionCopy(), branchEnvironment.immutableView(), branch.moveConfig());
			Motion predicted = simulation.offsetMotion().copy();
			double loss = simulation.positionDifference(targetPosition);
			results.add(new BranchTrace(shortKey(branch.frequencyKey()), formatSampleConfiguration(branch.moveConfig()), loss, branch.canFinishExplicitTick(), winnerIsInFirstLayer && branch.frequencyKey() == selectedFrequencyKey, predicted.motionX(), predicted.motionY(), predicted.motionZ()));
			simulation.expire();
		}
		results.sort(Comparator.comparingDouble(BranchTrace::loss).thenComparing(BranchTrace::key));
		return results;
	}

	private static MultiTickTrace traceMultiTickSearch(User user, SimulationEnvironment rootEnvironment, Simulator rootSimulator, Simulation selected, Set<MovementSearchBranch> firstTickBranches) {
		int selectedDepth = Math.max(0, Math.min(2, selected.searchDepth()));
		Position startPosition = rootEnvironment.verifiedLastPosition();
		Position targetPosition = rootEnvironment.position();
		Position lastReportedPosition = rootEnvironment.lastPosition();
		double flyingLimit = user.meta().protocol().flyingPacketUncertaintyRadius();
		List<MultiTickLayerTrace> layers = new ArrayList<>();
		List<MultiTickStepTrace> selectedPath = List.of();
		List<CandidatePath> allCandidates = new ArrayList<>();
		Set<Integer> uniqueEligibleIds = new HashSet<>();
		Set<Integer> retainedIds = new HashSet<>();
		Set<Integer> expandedIds = new HashSet<>();
		int[] nextCandidateId = {0};
		int selectedCandidateId = -1;
		List<SearchParent> parents = List.of(new SearchParent(rootEnvironment, rootSimulator, List.of(), firstTickBranches, -1));

		for (int depth = 0; depth <= THREE_TICK_SEARCH_LAST_DEPTH && !parents.isEmpty(); depth++) {
			MutableMultiTickLayer layer = new MutableMultiTickLayer(depth);
			List<SearchParent> nextParents = new ArrayList<>();
			for (SearchParent parent : parents) {
				Set<MovementSearchBranch> branches = parent.branches() == null ? searchTickBranches(user, parent.environment(), parent.simulator()) : parent.branches();
				List<CandidatePath> candidates = simulateLayerCandidates(user, parent, branches, depth, startPosition, targetPosition, lastReportedPosition, flyingLimit, parent.candidateId(), nextCandidateId);
				allCandidates.addAll(candidates);
				layer.capture(candidates, selectedDepth == depth ? selected : null);
				if (depth == selectedDepth) {
					CandidatePath winner = candidates.stream().filter(candidate -> sameSimulation(candidate.simulation(), selected)).findFirst().orElse(null);
					if (winner != null) {
						selectedPath = winner.steps();
						selectedCandidateId = winner.id();
					}
				}
				if (depth == THREE_TICK_SEARCH_LAST_DEPTH) {
					continue;
				}

				Retention retention = retainFlyingCandidates(candidates);
				layer.captureRetention(retention);
				uniqueEligibleIds.addAll(retention.uniqueCandidateIds());
				for (CandidatePath candidate : retention.retained()) {
					retainedIds.add(candidate.id());
					expandedIds.add(candidate.id());
					SimulationEnvironment nextEnvironment = candidate.simulation().environment().mutableView();
					Simulator nextSimulator = parent.simulator().simulateAround(user, nextEnvironment, candidate.simulation(), targetPosition, rootEnvironment.rotation());
					nextParents.add(new SearchParent(nextEnvironment, nextSimulator, candidate.steps(), null, candidate.id()));
				}
			}
			layers.add(layer.freeze());
			parents = nextParents;
		}
		if (selectedDepth > 0 && selectedPath.isEmpty()) {
			throw new IllegalStateException("Unable to reconstruct selected multi-tick path at depth " + selectedDepth);
		}
		Set<Integer> productionPathIds = new HashSet<>();
		Map<Integer, CandidatePath> candidatesById = new HashMap<>();
		for (CandidatePath candidate : allCandidates) {
			candidatesById.put(candidate.id(), candidate);
		}
		for (int candidateId = selectedCandidateId; candidateId >= 0; ) {
			productionPathIds.add(candidateId);
			CandidatePath candidate = candidatesById.get(candidateId);
			candidateId = candidate == null ? -1 : candidate.parentId();
		}
		int selectedNodeId = selectedCandidateId;
		List<MultiTickCandidateTrace> candidateTree = allCandidates.stream().map(candidate -> candidate.freeze(uniqueEligibleIds.contains(candidate.id()), retainedIds.contains(candidate.id()), expandedIds.contains(candidate.id()), productionPathIds.contains(candidate.id()), candidate.id() == selectedNodeId)).toList();

		return new MultiTickTrace(selectedDepth, selected.simulationCount(), flyingLimit, !selectedPath.isEmpty(), layers, selectedPath, candidateTree);
	}

	private static Set<MovementSearchBranch> searchTickBranches(User user, SimulationEnvironment environment, Simulator simulator) {
		MovementSearchInput input = MovementSearchInput.forTick(user, simulator, environment.immutableView(), false);
		Set<MovementSearchBranch> current = new HashSet<>();
		current.add(MovementSearchBranch.blank(input));
		for (SearchBrancher<MovementSearchInput, MovementSearchBranch> brancher : MovementSearchBranchers.tick()) {
			Set<MovementSearchBranch> next = new HashSet<>();
			for (MovementSearchBranch parent : current) {
				brancher.branch(input, parent, next);
			}
			if (next.isEmpty()) {
				throw new IllegalStateException(brancher.getClass().getSimpleName() + " produced no multi-tick branches");
			}
			current = next;
		}
		return current;
	}

	private static List<CandidatePath> simulateLayerCandidates(User user, SearchParent parent, Set<MovementSearchBranch> branches, int depth, Position startPosition, Position targetPosition, Position lastReportedPosition, double flyingLimit, int parentId, int[] nextCandidateId) {
		List<MovementSearchBranch> sortedBranches = branches.stream().sorted(Comparator.comparingLong(MovementSearchBranch::frequencyKey)).toList();
		List<CandidatePath> candidates = new ArrayList<>(sortedBranches.size());
		for (MovementSearchBranch branch : sortedBranches) {
			SimulationEnvironment branchEnvironment = branch.modifiedMutableView(parent.environment());
			Simulation simulation = parent.simulator().simulateTick(user, branchEnvironment.mutableBaseMotionCopy(), branchEnvironment.immutableView(), branch.moveConfig());
			simulation.setEnvironment(branchEnvironment);
			simulation.setCanFinishExplicitTick(branch.canFinishExplicitTick());
			simulation.setBranchFrequencyKey(branch.frequencyKey());
			Simulation copy = simulation.reusableCopy();
			Position predictedPosition = copy.predictedPosition();
			List<MultiTickStepTrace> steps = new ArrayList<>(parent.steps());
			Position omittedPacketPosition = parent.environment().verifiedLastPosition().add(copy.offsetMotion());
			double omissionDistance = omittedPacketPosition.distance(lastReportedPosition);
			boolean implicitEligible = omissionDistance < flyingLimit;
			steps.add(new MultiTickStepTrace(depth, shortKey(branch.frequencyKey()), formatSampleConfiguration(branch.moveConfig()), formatMotion(copy.offsetMotion()), predictedPosition.getX() - startPosition.getX(), predictedPosition.getY() - startPosition.getY(), predictedPosition.getZ() - startPosition.getZ(), predictedPosition.distance(targetPosition), omissionDistance, implicitEligible, branch.canFinishExplicitTick()));
			candidates.add(new CandidatePath(nextCandidateId[0]++, parentId, copy, List.copyOf(steps), implicitEligible, copy.predictedPosition().distance(lastReportedPosition)));
			simulation.expire();
		}
		return candidates;
	}

	private static Retention retainFlyingCandidates(List<CandidatePath> candidates) {
		Map<Simulation, CandidatePath> unique = new LinkedHashMap<>();
		for (CandidatePath candidate : candidates) {
			if (candidate.implicitEligible()) {
				unique.putIfAbsent(candidate.simulation(), candidate);
			}
		}
		List<CandidatePath> retained = unique.values().stream().sorted(Comparator.comparingDouble(CandidatePath::flyingDistance).thenComparing(candidate -> candidate.simulation().configuration().toString()).thenComparing(candidate -> candidate.steps().getLast().key())).toList();
		Set<Integer> uniqueCandidateIds = unique.values().stream().map(CandidatePath::id).collect(java.util.stream.Collectors.toSet());
		return new Retention(unique.size(), uniqueCandidateIds, retained);
	}

	private static boolean sameSimulation(Simulation left, Simulation right) {
		return right != null && left.equals(right);
	}

	private static EnvironmentTrace captureEnvironment(User user, SimulationEnvironment environment) {
		Position origin = environment.verifiedLastPosition();
		BoundingBox playerBox = environment.boundingBox();
		int originX = (int) Math.floor(origin.getX());
		int originY = (int) Math.floor(origin.getY());
		int originZ = (int) Math.floor(origin.getZ());
		List<BlockTrace> blocks = new ArrayList<>();

		for (int x = originX - BLOCK_RADIUS; x <= originX + BLOCK_RADIUS; x++) {
			for (int y = originY - 2; y <= originY + 3; y++) {
				for (int z = originZ - BLOCK_RADIUS; z <= originZ + BLOCK_RADIUS; z++) {
					BlockState state = user.blockCache().stateAt(x, y, z);
					String material = state.type().name();
					if (material.endsWith("AIR")) {
						continue;
					}
					List<BoundingBox> boxes = state.collisionShape().elementaryBoxes();
					if (boxes.isEmpty()) {
						blocks.add(blockTrace(state, false, new BoundingBox(x, y, z, x + 1, y + 1, z + 1), origin));
						continue;
					}
					for (BoundingBox box : boxes) {
						blocks.add(blockTrace(state, true, box, origin));
					}
				}
			}
		}

		return new EnvironmentTrace(formatPosition(origin), origin.getX(), origin.getY(), origin.getZ(), environment.rotation().yaw(), environment.rotation().pitch(), environment.pose().name(), environment.onGround(), environment.shouldHaveFallFlyingPose(), environment.shouldHaveFallFlyingPose() || user.meta().movement().hasElytraEquipped(), playerBox.maxX - playerBox.minX, playerBox.maxY - playerBox.minY, blocks);
	}

	private static BlockTrace blockTrace(BlockState state, boolean collidable, BoundingBox box, Position origin) {
		return new BlockTrace(state.type().name(), collidable, (box.minX + box.maxX) * 0.5 - origin.getX(), (box.minY + box.maxY) * 0.5 - origin.getY(), (box.minZ + box.maxZ) * 0.5 - origin.getZ(), box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ, formatProperties(state));
	}

	private static String formatProperties(BlockState state) {
		if (state.properties().isEmpty()) {
			return "variant=" + state.variantIndex();
		}
		return state.properties().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(","));
	}

	private static String formatPosition(Position position) {
		return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", position.getX(), position.getY(), position.getZ());
	}

	private static String friendlyName(SearchBrancher<MovementSearchInput, MovementSearchBranch> brancher) {
		return switch (brancher.getClass().getSimpleName()) {
			case "RotationBrancher" -> "Rotation";
			case "PreviousPostTickBrancher" -> "Previous motion";
			case "KeypressBrancher" -> "Keypress";
			case "CrouchingInputBrancher" -> "Crouch input";
			case "SprintingBrancher" -> "Sprinting";
			case "UpdateBrancher" -> "Ambiguous updates";
			case "UseItemBrancher" -> "Item use";
			case "AttackReduceBrancher" -> "Attack reduction";
			case "JumpBrancher" -> "Jump";
			default -> brancher.getClass().getSimpleName().replace("Brancher", "");
		};
	}

	private static String formatMotion(Motion motion) {
		return String.format(Locale.ROOT, "(%.6f, %.6f, %.6f)", motion.motionX(), motion.motionY(), motion.motionZ());
	}

	private static String formatConfiguration(MovementConfiguration config) {
		String keys = config.keysToString();
		return String.format(Locale.ROOT, "keys=%s, sprint=%s, jump=%s, item=%s, attacks=%d, reduceBefore=%s, motion=%s, inside=%s", keys.isEmpty() ? "none" : keys, config.isSprinting(), config.isJumping(), config.isHandActive(), config.reduceTicks(), config.reduceBefore(), config.overrideEndMotionToActualMotion() ? "actual" : "offset", config.usesAlternateBlockInsideCheck() ? "alternate" : "normal");
	}

	private static String formatSampleConfiguration(MovementConfiguration config) {
		List<String> state = new ArrayList<>();
		String keys = config.keysToString();
		state.add("keys=" + (keys.isEmpty() ? "none" : keys));
		if (config.isSprinting()) {
			state.add("sprint");
		}
		if (config.isJumping()) {
			state.add("jump");
		}
		if (config.isHandActive()) {
			state.add("item");
		}
		if (config.isReducing()) {
			state.add("attacks=" + config.reduceTicks() + (config.reduceBefore() ? " before" : " after"));
		}
		if (!config.overrideEndMotionToActualMotion()) {
			state.add("offset motion");
		}
		if (config.usesAlternateBlockInsideCheck()) {
			state.add("alternate inside");
		}
		return String.join(" · ", state);
	}

	private static String shortKey(long key) {
		return String.format(Locale.ROOT, "%016x", key).substring(8);
	}

	private static String toJson(List<TickTrace> ticks) {
		StringBuilder json = new StringBuilder("[");
		for (int index = 0; index < ticks.size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			appendTick(json, ticks.get(index));
		}
		return json.append(']').toString();
	}

	private static void appendTick(StringBuilder json, TickTrace tick) {
		json.append('{');
		field(json, "tick", tick.tick()).append(',');
		field(json, "loss", tick.loss()).append(',');
		field(json, "simulations", tick.simulations()).append(',');
		field(json, "depth", tick.depth()).append(',');
		field(json, "finalBranches", tick.finalBranches()).append(',');
		field(json, "winnerInFirstLayer", tick.winnerInFirstLayer()).append(',');
		field(json, "winner", tick.winner()).append(',');
		field(json, "predicted", tick.predicted()).append(',');
		field(json, "actual", tick.actual()).append(',');
		field(json, "actualX", tick.actualX()).append(',');
		field(json, "actualY", tick.actualY()).append(',');
		field(json, "actualZ", tick.actualZ()).append(',');
		json.append("\"stages\":[");
		for (int index = 0; index < tick.stages().size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			appendStage(json, tick.stages().get(index));
		}
		json.append("],\"branches\":[");
		for (int index = 0; index < tick.branches().size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			appendBranch(json, tick.branches().get(index));
		}
		json.append("],\"environment\":");
		appendEnvironment(json, tick.environment());
		json.append(",\"multiTick\":");
		appendMultiTick(json, tick.multiTick());
		json.append('}');
	}

	private static void appendStage(StringBuilder json, StageTrace stage) {
		json.append('{');
		field(json, "name", stage.name()).append(',');
		field(json, "inputs", stage.inputs()).append(',');
		field(json, "attempted", stage.attempted()).append(',');
		field(json, "outputs", stage.outputs()).append(',');
		field(json, "duplicates", stage.duplicates()).append(',');
		field(json, "finishable", stage.finishable()).append(',');
		field(json, "winner", stage.winner()).append(',');
		json.append("\"fanOut\":{");
		int fanOutIndex = 0;
		for (Map.Entry<Integer, Integer> entry : stage.fanOut().entrySet()) {
			if (fanOutIndex++ > 0) {
				json.append(',');
			}
			json.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
		}
		json.append("},\"samples\":[");
		for (int index = 0; index < stage.samples().size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			string(json, stage.samples().get(index));
		}
		json.append("]}");
	}

	private static void appendMultiTick(StringBuilder json, MultiTickTrace multiTick) {
		json.append('{');
		field(json, "selectedDepth", multiTick.selectedDepth()).append(',');
		field(json, "productionSimulations", multiTick.productionSimulations()).append(',');
		field(json, "omissionLimit", multiTick.omissionLimit()).append(',');
		field(json, "selectedPathFound", multiTick.selectedPathFound()).append(',');
		json.append("\"layers\":[");
		for (int index = 0; index < multiTick.layers().size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			MultiTickLayerTrace layer = multiTick.layers().get(index);
			json.append('{');
			field(json, "depth", layer.depth()).append(',');
			field(json, "parents", layer.parents()).append(',');
			field(json, "simulations", layer.simulations()).append(',');
			field(json, "finishable", layer.finishable()).append(',');
			field(json, "exactFinishable", layer.exactFinishable()).append(',');
			field(json, "acceptedFinishable", layer.acceptedFinishable()).append(',');
			field(json, "mismatchFinishable", layer.mismatchFinishable()).append(',');
			field(json, "rawImplicitEligible", layer.rawImplicitEligible()).append(',');
			field(json, "implicitEligible", layer.implicitEligible()).append(',');
			field(json, "retained", layer.retained()).append(',');
			field(json, "bestLoss", layer.bestLoss()).append(',');
			field(json, "medianLoss", layer.medianLoss()).append(',');
			field(json, "p90Loss", layer.p90Loss()).append(',');
			field(json, "closestOmissionDistance", layer.closestOmissionDistance()).append(',');
			field(json, "furthestRetainedDistance", layer.furthestRetainedDistance()).append(',');
			field(json, "selectedLoss", layer.selectedLoss()).append(',');
			field(json, "selected", layer.selected());
			json.append('}');
		}
		json.append("],\"path\":[");
		for (int index = 0; index < multiTick.path().size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			MultiTickStepTrace step = multiTick.path().get(index);
			json.append('{');
			field(json, "depth", step.depth()).append(',');
			field(json, "key", step.key()).append(',');
			field(json, "config", step.configuration()).append(',');
			field(json, "motion", step.motion()).append(',');
			field(json, "x", step.positionX()).append(',');
			field(json, "y", step.positionY()).append(',');
			field(json, "z", step.positionZ()).append(',');
			field(json, "loss", step.remainingLoss()).append(',');
			field(json, "omissionDistance", step.omissionDistance()).append(',');
			field(json, "implicitEligible", step.implicitEligible()).append(',');
			field(json, "finishable", step.finishable());
			json.append('}');
		}
		json.append("],\"candidates\":[");
		for (int index = 0; index < multiTick.candidates().size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			MultiTickCandidateTrace candidate = multiTick.candidates().get(index);
			json.append('{');
			field(json, "id", candidate.id()).append(',');
			field(json, "parent", candidate.parentId()).append(',');
			field(json, "depth", candidate.depth()).append(',');
			field(json, "key", candidate.key()).append(',');
			field(json, "config", candidate.configuration()).append(',');
			field(json, "motion", candidate.motion()).append(',');
			field(json, "x", candidate.positionX()).append(',');
			field(json, "y", candidate.positionY()).append(',');
			field(json, "z", candidate.positionZ()).append(',');
			field(json, "loss", candidate.remainingLoss()).append(',');
			field(json, "omissionDistance", candidate.omissionDistance()).append(',');
			field(json, "implicitEligible", candidate.implicitEligible()).append(',');
			field(json, "finishable", candidate.finishable()).append(',');
			field(json, "retention", candidate.retention()).append(',');
			field(json, "production", candidate.production()).append(',');
			field(json, "selected", candidate.selected());
			json.append('}');
		}
		json.append("]}");
	}

	private static void appendBranch(StringBuilder json, BranchTrace branch) {
		json.append('{');
		field(json, "key", branch.key()).append(',');
		field(json, "config", branch.configuration()).append(',');
		field(json, "loss", branch.loss()).append(',');
		field(json, "finishable", branch.finishable()).append(',');
		field(json, "selected", branch.selected()).append(',');
		field(json, "x", branch.motionX()).append(',');
		field(json, "y", branch.motionY()).append(',');
		field(json, "z", branch.motionZ());
		json.append('}');
	}

	private static void appendEnvironment(StringBuilder json, EnvironmentTrace environment) {
		json.append('{');
		field(json, "origin", environment.origin()).append(',');
		field(json, "originX", environment.originX()).append(',');
		field(json, "originY", environment.originY()).append(',');
		field(json, "originZ", environment.originZ()).append(',');
		field(json, "yaw", environment.yaw()).append(',');
		field(json, "pitch", environment.pitch()).append(',');
		field(json, "pose", environment.pose()).append(',');
		field(json, "onGround", environment.onGround()).append(',');
		field(json, "fallFlying", environment.fallFlying()).append(',');
		field(json, "elytraEquipped", environment.elytraEquipped()).append(',');
		field(json, "playerWidth", environment.playerWidth()).append(',');
		field(json, "playerHeight", environment.playerHeight()).append(',');
		json.append("\"blocks\":[");
		for (int index = 0; index < environment.blocks().size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			BlockTrace block = environment.blocks().get(index);
			json.append('[');
			string(json, block.material()).append(',');
			json.append(block.collidable()).append(',');
			json.append(formatJsonDouble(block.centerX())).append(',');
			json.append(formatJsonDouble(block.centerY())).append(',');
			json.append(formatJsonDouble(block.centerZ())).append(',');
			json.append(formatJsonDouble(block.sizeX())).append(',');
			json.append(formatJsonDouble(block.sizeY())).append(',');
			json.append(formatJsonDouble(block.sizeZ())).append(',');
			string(json, block.properties()).append(']');
		}
		json.append("]}");
	}

	private static String formatJsonDouble(double value) {
		return formatJsonDouble(value, 9);
	}

	private static String formatJsonDouble(double value, int precision) {
		return BigDecimal.valueOf(value).round(new MathContext(precision)).stripTrailingZeros().toPlainString();
	}

	private static StringBuilder field(StringBuilder json, String name, String value) {
		string(json, name).append(':');
		return string(json, value);
	}

	private static StringBuilder field(StringBuilder json, String name, int value) {
		string(json, name).append(':').append(value);
		return json;
	}

	private static StringBuilder field(StringBuilder json, String name, double value) {
		string(json, name).append(':').append(formatJsonDouble(value, 12));
		return json;
	}

	private static StringBuilder field(StringBuilder json, String name, boolean value) {
		string(json, name).append(':').append(value);
		return json;
	}

	private static StringBuilder string(StringBuilder json, String value) {
		json.append('"');
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '"' -> json.append("\\\"");
				case '\\' -> json.append("\\\\");
				case '\n' -> json.append("\\n");
				case '\r' -> json.append("\\r");
				case '\t' -> json.append("\\t");
				default -> json.append(character);
			}
		}
		return json.append('"');
	}

	private record TickTrace(int tick, double loss, int simulations, int depth, int finalBranches,
	                         boolean winnerInFirstLayer, String winner, String predicted, String actual, double actualX,
	                         double actualY, double actualZ, List<StageTrace> stages, List<BranchTrace> branches,
	                         EnvironmentTrace environment, MultiTickTrace multiTick) {
	}

	private record StageTrace(String name, int inputs, int attempted, int outputs, int duplicates, int finishable,
	                          String winner, Map<Integer, Integer> fanOut, List<String> samples) {
	}

	private record BranchTrace(String key, String configuration, double loss, boolean finishable, boolean selected,
	                           double motionX, double motionY, double motionZ) {
	}

	private record EnvironmentTrace(String origin, double originX, double originY, double originZ, double yaw,
	                                double pitch, String pose, boolean onGround, boolean fallFlying,
	                                boolean elytraEquipped, double playerWidth, double playerHeight,
	                                List<BlockTrace> blocks) {
	}

	private record BlockTrace(String material, boolean collidable, double centerX, double centerY, double centerZ,
	                          double sizeX, double sizeY, double sizeZ, String properties) {
	}

	private record MultiTickTrace(int selectedDepth, int productionSimulations, double omissionLimit,
	                              boolean selectedPathFound, List<MultiTickLayerTrace> layers,
	                              List<MultiTickStepTrace> path, List<MultiTickCandidateTrace> candidates) {
	}

	private record MultiTickLayerTrace(int depth, int parents, int simulations, int finishable, int exactFinishable,
	                                   int acceptedFinishable, int mismatchFinishable, int rawImplicitEligible,
	                                   int implicitEligible, int retained, double bestLoss, double medianLoss,
	                                   double p90Loss, double closestOmissionDistance, double furthestRetainedDistance,
	                                   double selectedLoss, boolean selected) {
	}

	private record MultiTickStepTrace(int depth, String key, String configuration, String motion, double positionX,
	                                  double positionY, double positionZ, double remainingLoss, double omissionDistance,
	                                  boolean implicitEligible, boolean finishable) {
	}

	private record MultiTickCandidateTrace(int id, int parentId, int depth, String key, String configuration,
	                                       String motion, double positionX, double positionY, double positionZ,
	                                       double remainingLoss, double omissionDistance, boolean implicitEligible,
	                                       boolean finishable, String retention, boolean production, boolean selected) {
	}

	private record SearchParent(SimulationEnvironment environment, Simulator simulator, List<MultiTickStepTrace> steps,
	                            Set<MovementSearchBranch> branches, int candidateId) {
	}

	private record CandidatePath(int id, int parentId, Simulation simulation, List<MultiTickStepTrace> steps,
	                             boolean implicitEligible, double flyingDistance) {
		private MultiTickCandidateTrace freeze(boolean uniqueEligible, boolean retained, boolean expanded, boolean production, boolean selected) {
			MultiTickStepTrace step = steps.getLast();
			String retention = !implicitEligible ? "explicit-only" : expanded ? "expanded" : retained ? "retained" : step.depth() >= THREE_TICK_SEARCH_LAST_DEPTH ? "depth-limit" : uniqueEligible ? "retained" : "merged";
			return new MultiTickCandidateTrace(id, parentId, step.depth(), step.key(), step.configuration(), step.motion(), step.positionX(), step.positionY(), step.positionZ(), step.remainingLoss(), step.omissionDistance(), step.implicitEligible(), step.finishable(), retention, production, selected);
		}
	}

	private record Retention(int uniqueEligible, Set<Integer> uniqueCandidateIds, List<CandidatePath> retained) {
	}

	private static final class MutableMultiTickLayer {
		private final int depth;
		private int parents;
		private int simulations;
		private int finishable;
		private int exactFinishable;
		private int acceptedFinishable;
		private int mismatchFinishable;
		private int rawImplicitEligible;
		private int implicitEligible;
		private int retained;
		private double bestLoss = Double.POSITIVE_INFINITY;
		private double closestOmissionDistance = Double.POSITIVE_INFINITY;
		private double furthestRetainedDistance = Double.NEGATIVE_INFINITY;
		private double selectedLoss = Double.NaN;
		private final List<Double> finishableLosses = new ArrayList<>();
		private boolean selected;

		private MutableMultiTickLayer(int depth) {
			this.depth = depth;
		}

		private void capture(List<CandidatePath> candidates, Simulation selectedSimulation) {
			parents++;
			simulations += candidates.size();
			for (CandidatePath candidate : candidates) {
				MultiTickStepTrace step = candidate.steps().getLast();
				closestOmissionDistance = Math.min(closestOmissionDistance, step.omissionDistance());
				if (candidate.implicitEligible()) {
					rawImplicitEligible++;
				}
				if (candidate.simulation().canFinishExplicitTick()) {
					finishable++;
					double loss = step.remainingLoss();
					finishableLosses.add(loss);
					bestLoss = Math.min(bestLoss, loss);
					if (loss <= EXACT_BRANCH_LOSS) {
						exactFinishable++;
					} else if (loss <= ACCEPTED_BRANCH_LOSS) {
						acceptedFinishable++;
					} else {
						mismatchFinishable++;
					}
				}
				if (sameSimulation(candidate.simulation(), selectedSimulation)) {
					selected = true;
					selectedLoss = step.remainingLoss();
				}
			}
		}

		private void captureRetention(Retention retention) {
			implicitEligible += retention.uniqueEligible();
			retained += retention.retained().size();
			for (CandidatePath candidate : retention.retained()) {
				furthestRetainedDistance = Math.max(furthestRetainedDistance, candidate.steps().getLast().omissionDistance());
			}
		}

		private MultiTickLayerTrace freeze() {
			finishableLosses.sort(Double::compareTo);
			return new MultiTickLayerTrace(depth, parents, simulations, finishable, exactFinishable, acceptedFinishable, mismatchFinishable, rawImplicitEligible, implicitEligible, retained, Double.isFinite(bestLoss) ? bestLoss : -1, percentile(finishableLosses, 0.5), percentile(finishableLosses, 0.9), Double.isFinite(closestOmissionDistance) ? closestOmissionDistance : -1, Double.isFinite(furthestRetainedDistance) ? furthestRetainedDistance : -1, Double.isFinite(selectedLoss) ? selectedLoss : -1, selected);
		}

		private static double percentile(List<Double> sortedValues, double quantile) {
			if (sortedValues.isEmpty()) {
				return -1;
			}
			int index = (int) Math.round(quantile * (sortedValues.size() - 1));
			return sortedValues.get(index);
		}
	}

	private static final class MutableStage {
		private final String name;
		private final int inputs;
		private final Map<Integer, Integer> fanOut = new LinkedHashMap<>();
		private final Map<Long, Long> predecessors = new HashMap<>();
		private int attemptedChildren;
		private int duplicateChildren;
		private int finishable;
		private long winnerFrequencyKey = Long.MIN_VALUE;
		private List<MovementSearchBranch> outputs = List.of();

		private MutableStage(String name, int inputs) {
			this.name = name;
			this.inputs = inputs;
		}

		private static MutableStage start(MovementSearchBranch blank) {
			MutableStage stage = new MutableStage("Packet state", 1);
			stage.attemptedChildren = 1;
			stage.finishable = 1;
			stage.outputs = List.of(blank);
			stage.fanOut.put(1, 1);
			return stage;
		}

		private void captureOutputs(Set<MovementSearchBranch> branches) {
			outputs = branches.stream().sorted(Comparator.comparing((MovementSearchBranch branch) -> formatConfiguration(branch.moveConfig())).thenComparingLong(MovementSearchBranch::frequencyKey)).toList();
			finishable = (int) branches.stream().filter(MovementSearchBranch::canFinishExplicitTick).count();
		}

		private StageTrace freeze() {
			String winner = outputs.stream().filter(branch -> branch.frequencyKey() == winnerFrequencyKey).findFirst().map(branch -> shortKey(branch.frequencyKey()) + " · " + formatSampleConfiguration(branch.moveConfig())).orElse("");
			List<String> samples = outputs.stream().limit(SAMPLE_CONFIGURATIONS).map(branch -> shortKey(branch.frequencyKey()) + " · " + formatSampleConfiguration(branch.moveConfig())).toList();
			return new StageTrace(name, inputs, attemptedChildren, outputs.size(), duplicateChildren, finishable, winner, Map.copyOf(fanOut), samples);
		}
	}

	private static final class RecordingOutput extends AbstractCollection<MovementSearchBranch> {
		private final Set<MovementSearchBranch> output;
		private final MovementSearchBranch parent;
		private final MutableStage stage;

		private RecordingOutput(Set<MovementSearchBranch> output, MovementSearchBranch parent, MutableStage stage) {
			this.output = output;
			this.parent = parent;
			this.stage = stage;
		}

		@Override
		public boolean add(MovementSearchBranch child) {
			stage.attemptedChildren++;
			stage.predecessors.putIfAbsent(child.frequencyKey(), parent.frequencyKey());
			boolean added = output.add(child);
			if (!added) {
				stage.duplicateChildren++;
			}
			return added;
		}

		@Override
		public Iterator<MovementSearchBranch> iterator() {
			return output.iterator();
		}

		@Override
		public int size() {
			return output.size();
		}
	}

	private static final String HTML_HEAD = """
		<!doctype html>
		<html lang="en">
		<head>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<title>Physics Branch Visualization · __RECORDING_NAME__</title>
		<style>
		:root {
			color-scheme: light dark;
			--bg: light-dark(#f4f6f8, #101318);
			--surface: light-dark(#ffffff, #181d24);
			--surface-2: light-dark(#eef2f5, #212833);
			--text: light-dark(#17202a, #edf2f7);
			--muted: light-dark(#657180, #a9b4c2);
			--line: light-dark(#cbd5df, #394452);
			--accent: light-dark(#1769aa, #69b7ff);
			--accent-soft: light-dark(#ddecfa, #193650);
			--winner: light-dark(#18744a, #65d49a);
			--exact: light-dark(#18744a, #65d49a);
			--accepted: light-dark(#9a6112, #f4bd61);
			--mismatch: light-dark(#a33b34, #ff8a80);
			--multi: light-dark(#6445b8, #b3a0ff);
			--danger: light-dark(#a33b34, #ff8a80);
			font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
		}
		* { box-sizing: border-box; }
		body { margin: 0; background: var(--bg); color: var(--text); }
		main { width: min(1320px, 100%); margin: 0 auto; padding: 32px 22px 48px; }
		h1 { margin: 0; font-size: clamp(1.55rem, 3vw, 2.25rem); font-weight: 650; letter-spacing: -0.025em; }
		.subtitle { margin: 8px 0 20px; color: var(--muted); }
		.subtitle code { overflow-wrap: anywhere; white-space: normal; }
		code { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
		.toolbar { display: grid; grid-template-columns: auto auto minmax(160px, 1fr) auto auto; gap: 12px; align-items: center; margin-bottom: 18px; }
		button, input { font: inherit; }
		button { border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: var(--text); padding: 8px 12px; cursor: pointer; }
		button:hover { border-color: var(--accent); }
		button:disabled { opacity: .45; cursor: default; }
		#play { min-width: 86px; }
		input[type="range"] { width: 100%; accent-color: var(--accent); }
		#tick-label { min-width: 92px; text-align: center; font-variant-numeric: tabular-nums; }
		.detail { background: var(--surface); border: 1px solid var(--line); border-radius: 12px; }
		.possibility-browser { display: grid; grid-template-columns: minmax(280px, .34fr) minmax(0, 1fr); min-height: 720px; overflow: hidden; background: var(--surface); border: 1px solid var(--line); border-radius: 14px; }
		.browser-sidebar { display: flex; flex-direction: column; min-width: 0; border-right: 1px solid var(--line); background: color-mix(in srgb, var(--surface-2) 55%, var(--surface)); }
		.browser-sidebar-header { padding: 16px; border-bottom: 1px solid var(--line); }
		.browser-sidebar-header h2 { margin: 0; font-size: 1rem; }
		.browser-sidebar-header p { margin: 5px 0 0; color: var(--muted); font-size: .8rem; }
		.tree-section { padding: 13px 10px 4px; }
		.tree-section + .tree-section { border-top: 1px solid color-mix(in srgb, var(--line) 70%, transparent); }
		.tree-section.grow { flex: 1; min-height: 0; padding-bottom: 12px; }
		.tree-heading { display: flex; justify-content: space-between; gap: 10px; align-items: baseline; margin: 0 6px 8px; color: var(--muted); font-size: .7rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
		.tree-heading span { font-weight: 500; letter-spacing: 0; text-transform: none; }
		.tree-list { display: grid; gap: 4px; }
		.tree-item { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 8px; align-items: center; width: 100%; min-height: 42px; padding: 7px 9px; border-color: transparent; background: transparent; text-align: left; }
		.tree-item:hover { background: var(--surface); }
		.tree-item[aria-pressed="true"] { border-color: var(--accent); background: var(--accent-soft); box-shadow: inset 3px 0 var(--accent); }
		.tree-icon { color: var(--muted); font: 700 .82rem ui-monospace, SFMono-Regular, Consolas, monospace; }
		.tree-primary { min-width: 0; overflow: hidden; color: var(--text); font-size: .82rem; font-weight: 620; text-overflow: ellipsis; white-space: nowrap; }
		.tree-secondary { color: var(--muted); font-size: .72rem; font-variant-numeric: tabular-nums; white-space: nowrap; }
		.browser-page { min-width: 0; background: var(--surface); }
		.page-header { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; min-height: 92px; padding: 15px 18px; border-bottom: 1px solid var(--line); }
		.page-kicker { color: var(--accent); font-size: .72rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
		.page-header h2 { margin: 4px 0 2px; font-size: 1.12rem; }
		.page-header p { margin: 0; color: var(--muted); font-size: .82rem; overflow-wrap: anywhere; }
		.page-status { flex: 0 0 auto; margin-top: 5px; padding: 5px 9px; border: 1px solid currentColor; border-radius: 999px; font-size: .74rem; font-weight: 650; }
		.page-content { display: grid; gap: 14px; padding: 16px; }
		.page-section { min-width: 0; padding: 14px; border: 1px solid var(--line); border-radius: 12px; background: color-mix(in srgb, var(--surface-2) 28%, var(--surface)); }
		.page-section > h3 { margin: 0 0 12px; font-size: .95rem; }
		.window { background: var(--surface); border: 1px solid var(--line); border-radius: 14px; overflow: hidden; min-width: 0; }
		.window-header { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; padding: 14px 16px; border-bottom: 1px solid var(--line); }
		.window-header h2 { margin: 0; font-size: 1rem; }
		.window-header span { color: var(--muted); font-size: .82rem; text-align: right; }
		.world-viewport { position: relative; height: 460px; background: var(--bg); border-bottom: 1px solid var(--line); }
		.world-viewport canvas { display: block; width: 100%; height: 100%; }
		.world-overlay { position: absolute; inset: auto 12px 12px 12px; display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 18px; pointer-events: none; color: var(--muted); font-size: .78rem; }
		.legend { display: flex; flex-wrap: wrap; gap: 8px 14px; align-items: center; }
		.legend-item { display: inline-flex; gap: 6px; align-items: center; color: var(--muted); font-size: .78rem; }
		.window-header .legend-item.exact { color: var(--exact); }
		.window-header .legend-item.accepted { color: var(--accepted); }
		.window-header .legend-item.mismatch { color: var(--mismatch); }
		.window-header .legend-item.multi { color: var(--multi); }
		.swatch { width: 10px; height: 10px; border-radius: 50%; background: currentColor; }
		.exact { color: var(--exact); }
		.accepted { color: var(--accepted); }
		.mismatch { color: var(--mismatch); }
		.multi { color: var(--multi); }
		.branch-body { padding: 14px; }
		.branch-grid { display: grid; gap: 3px; max-height: 470px; overflow-y: auto; }
		.candidate-row { display: grid; grid-template-columns: 24px minmax(0, 1fr); align-items: stretch; padding-left: calc(var(--depth) * 14px); }
		.candidate-toggle { display: grid; place-items: center; width: 24px; min-height: 42px; padding: 0; border-color: transparent; background: transparent; color: var(--muted); font-size: .72rem; }
		.candidate-toggle:hover { background: var(--surface); }
		.candidate-toggle-placeholder { width: 24px; }
		.branch-tile { position: relative; grid-template-columns: auto minmax(0, 1fr) auto; min-height: 42px; padding: 7px 9px; border-width: 1px; }
		.branch-tile.fit-exact { color: var(--exact); border-color: color-mix(in srgb, var(--exact) 52%, var(--line)); background: color-mix(in srgb, var(--exact) 12%, var(--surface)); }
		.branch-tile.fit-accepted { color: var(--accepted); border-color: color-mix(in srgb, var(--accepted) 52%, var(--line)); background: color-mix(in srgb, var(--accepted) 12%, var(--surface)); }
		.branch-tile.fit-mismatch { color: var(--mismatch); border-color: color-mix(in srgb, var(--mismatch) 42%, var(--line)); background: color-mix(in srgb, var(--mismatch) 9%, var(--surface)); }
		.branch-tile[aria-pressed="true"] { border-color: var(--accent); outline: none; box-shadow: inset 3px 0 var(--accent); }
		.branch-detail { margin-top: 14px; padding-top: 13px; border-top: 1px solid var(--line); }
		.branch-detail strong { display: block; margin-bottom: 7px; }
		.branch-detail p { margin: 5px 0; color: var(--muted); font-size: .84rem; overflow-wrap: anywhere; }
		.branch-detail .fit-line { color: var(--text); font-size: .94rem; }
		.candidate-children { margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--line); }
		.candidate-children-heading { display: flex; justify-content: space-between; gap: 12px; align-items: baseline; margin-bottom: 9px; }
		.candidate-children-heading h3 { margin: 0; font-size: .9rem; }
		.candidate-children-heading span { color: var(--muted); font-size: .76rem; }
		.candidate-child-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 6px; }
		.candidate-child-list .tree-item { border-color: var(--line); background: var(--surface); }
		.candidate-empty { margin: 0; padding: 10px 12px; border-radius: 8px; background: var(--surface-2); color: var(--muted); font-size: .82rem; line-height: 1.45; }
		.graph-shell { min-width: 0; overflow-x: auto; }
		.graph-heading { display: flex; justify-content: space-between; gap: 12px; align-items: baseline; padding: 0 4px 8px; }
		.graph-heading h2 { margin: 0; font-size: 1rem; }
		.graph-heading span { color: var(--muted); font-size: .82rem; }
		#flow { display: block; width: 100%; min-width: 920px; min-height: 260px; }
		#multi-flow { display: block; width: 100%; min-width: 620px; min-height: 190px; }
		.multi-edge { fill: none; stroke: var(--line); stroke-width: 2; }
		.multi-edge.selected { stroke: var(--multi); stroke-width: 4; }
		.multi-node { fill: var(--surface-2); stroke: var(--line); stroke-width: 2; cursor: pointer; }
		.multi-node.selected { fill: color-mix(in srgb, var(--multi) 16%, var(--surface)); stroke: var(--multi); stroke-width: 4; }
		.multi-node.active { stroke: var(--accent); stroke-width: 4; }
		.multi-node.selected.active { stroke: var(--winner); }
		.multi-title { fill: var(--text); font-size: 12px; font-weight: 600; text-anchor: middle; }
		.multi-count { fill: var(--text); font-size: 15px; font-weight: 700; text-anchor: middle; }
		.multi-caption { fill: var(--muted); font-size: 10px; text-anchor: middle; }
		.multi-layer-picker { display: grid; gap: 4px; }
		.multi-inspector { display: grid; grid-template-columns: minmax(280px, .8fr) minmax(300px, 1.2fr); gap: 20px; padding-top: 14px; border-top: 1px solid var(--line); }
		.multi-inspector-heading { display: flex; justify-content: space-between; gap: 12px; align-items: baseline; margin-bottom: 12px; }
		.multi-inspector-heading span { color: var(--muted); font-size: .78rem; text-align: right; }
		.multi-funnel { display: grid; gap: 9px; }
		.multi-funnel-row { display: grid; grid-template-columns: 128px minmax(80px, 1fr) auto; gap: 9px; align-items: center; color: var(--muted); font-size: .78rem; }
		.multi-funnel-track { height: 8px; overflow: hidden; border-radius: 999px; background: var(--surface-2); }
		.multi-funnel-fill { display: block; width: var(--share); height: 100%; background: var(--multi); }
		.multi-funnel-fill.unique { background: var(--winner); }
		.multi-funnel-fill.retained { background: var(--exact); }
		.multi-funnel-fill.accepted { background: var(--accepted); }
		.multi-funnel-value { color: var(--text); font-variant-numeric: tabular-nums; white-space: nowrap; }
		.multi-layer-metrics { align-content: start; }
		.multi-path { display: grid; gap: 7px; margin: 0; padding: 0; list-style: none; }
		.multi-path li { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 10px; align-items: start; padding-top: 9px; border-top: 1px solid var(--line); color: var(--muted); font-size: .84rem; }
		.multi-path strong { color: var(--multi); white-space: nowrap; }
		.multi-path-main { display: grid; gap: 4px; min-width: 0; }
		.multi-path code { overflow-wrap: anywhere; }
		.multi-path-state { color: var(--muted); font-size: .78rem; font-variant-numeric: tabular-nums; }
		.multi-path-outcome { display: grid; gap: 4px; text-align: right; }
		.multi-path .path-loss { white-space: nowrap; font-variant-numeric: tabular-nums; }
		.flow-edge { fill: none; stroke: var(--line); stroke-width: 1.25; opacity: .7; }
		.flow-edge.winner { stroke: var(--winner); stroke-width: 3; opacity: 1; }
		.flow-node { fill: var(--surface-2); stroke: var(--line); stroke-width: 1.5; cursor: pointer; }
		.flow-node:hover, .flow-node.selected { stroke: var(--accent); stroke-width: 3; }
		.flow-node.winner { fill: var(--accent-soft); stroke: var(--winner); }
		.flow-name { fill: var(--text); font-size: 12px; font-weight: 600; text-anchor: middle; pointer-events: none; }
		.flow-count { fill: var(--text); font-size: 13px; font-weight: 700; text-anchor: middle; pointer-events: none; }
		.flow-dot { fill: var(--accent); opacity: .48; pointer-events: none; }
		.stage-drilldown { border: 1px solid var(--line); border-radius: 12px; background: var(--surface); }
		.stage-drilldown > summary { padding: 13px 15px; cursor: pointer; color: var(--text); font-size: .9rem; font-weight: 650; }
		.stage-drilldown[open] > summary { border-bottom: 1px solid var(--line); }
		.stage-drilldown-body { padding: 14px; }
		.stage-picker { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0; }
		.stage-picker button { padding: 6px 10px; font-size: .84rem; }
		.stage-picker button[aria-pressed="true"] { color: var(--surface); background: var(--accent); border-color: var(--accent); }
		.details { display: grid; grid-template-columns: minmax(0, .85fr) minmax(0, 1.15fr); gap: 14px; }
		.detail { padding: 17px 18px; min-width: 0; }
		.detail h2 { margin: 0 0 13px; font-size: 1rem; }
		.stage-summary { margin: 0; color: var(--text); font-variant-numeric: tabular-nums; }
		.metrics { display: grid; grid-template-columns: 1fr auto; gap: 9px 18px; margin: 0; }
		.metrics dt { color: var(--muted); }
		.metrics dd { margin: 0; font-variant-numeric: tabular-nums; text-align: right; }
		#fanout { margin-top: 14px; color: var(--muted); font-size: .88rem; }
		.winner-box { border-left: 3px solid var(--winner); padding: 9px 12px; margin-bottom: 13px; background: var(--surface-2); overflow-wrap: anywhere; }
		.winner-box.missing { border-left-color: var(--muted); color: var(--muted); }
		.samples { margin: 0; padding-left: 20px; color: var(--muted); }
		.samples li { margin: 7px 0; overflow-wrap: anywhere; }
		.explainer { margin-top: 16px; color: var(--muted); font-size: .88rem; line-height: 1.55; }
		@media (max-width: 760px) {
			main { padding-inline: 14px; }
			.toolbar { grid-template-columns: auto auto minmax(52px, 1fr) auto; }
			#tick-label { grid-column: 1 / -1; grid-row: 1; }
			.details { grid-template-columns: 1fr; }
			.multi-inspector { grid-template-columns: 1fr; }
			.graph-heading { align-items: flex-start; flex-direction: column; }
		}
		@media (max-width: 560px) {
			.window-header { flex-direction: column; }
			.window-header span { text-align: left; }
			.multi-funnel-row { grid-template-columns: 110px minmax(64px, 1fr) auto; }
			.multi-path li { grid-template-columns: 1fr; }
			.multi-path-outcome { text-align: left; }
		}
		@media (max-width: 940px) {
			.possibility-browser { grid-template-columns: 1fr; }
			.browser-sidebar { max-height: 520px; border-right: 0; border-bottom: 1px solid var(--line); }
			.world-viewport { height: 430px; }
		}
		</style>
		<script type="importmap">
		{
			"imports": {
				"three": "https://cdn.jsdelivr.net/npm/three@0.185.0/build/three.module.js",
				"three/addons/": "https://cdn.jsdelivr.net/npm/three@0.185.0/examples/jsm/"
			}
		}
		</script>
		</head>
		<body>
		<main>
			<h1>Physics Branch Visualization</h1>
			<p class="subtitle"><code>__RECORDING__</code> · protocol __CLIENT_PROTOCOL__ · server __SERVER_VERSION__</p>
		
			<div class="toolbar" aria-label="Tick navigation">
				<button id="previous" type="button">← Previous</button>
				<button id="play" type="button" aria-pressed="false">▶ Play</button>
				<input id="tick" type="range" min="0" value="0" aria-label="Recorded movement tick">
				<button id="next" type="button">Next →</button>
				<strong id="tick-label">Tick</strong>
			</div>
		
			<section class="possibility-browser" aria-label="Physics possibility browser">
				<aside class="browser-sidebar">
					<header class="browser-sidebar-header">
						<h2>Possibilities</h2>
						<p>Choose an item to inspect it in the detailed view.</p>
					</header>
					<section class="tree-section">
						<h3 class="tree-heading">Production route <span id="route-summary">Selected</span></h3>
						<div id="multi-path-tree" class="tree-list" aria-label="Selected production route"></div>
					</section>
					<section class="tree-section">
						<h3 class="tree-heading">Search layers <span id="multi-summary">Search</span></h3>
						<div id="multi-layer-picker" class="multi-layer-picker" aria-label="Inspect a simulated tick layer"></div>
					</section>
					<section class="tree-section grow">
						<h3 class="tree-heading">Candidate tree <span id="fit-summary">Possibilities</span></h3>
						<div id="branch-grid" class="branch-grid" aria-label="Implicit and explicit candidates by simulated tick"></div>
					</section>
				</aside>
		
				<article class="browser-page">
					<header class="page-header">
						<div>
							<span id="selection-kicker" class="page-kicker">Possibility</span>
							<h2 id="selection-title">Selected possibility</h2>
							<p id="selection-summary"></p>
						</div>
						<span id="selection-status" class="page-status">Selected</span>
					</header>
		
					<div class="window-header">
						<div class="legend" aria-label="3D player states">
							<span class="legend-item"><span class="swatch" style="color: var(--muted)"></span>start</span>
							<span class="legend-item exact"><span class="swatch"></span>recorded</span>
							<span id="branch-legend" class="legend-item"><span class="swatch"></span><span id="branch-legend-label">possibility</span></span>
							<span class="legend-item multi"><span class="swatch"></span>production route</span>
						</div>
						<span id="world-origin">World origin</span>
					</div>
					<div id="world-viewport" class="world-viewport" aria-label="Interactive 3D view of the selected possibility">
						<div class="world-overlay"><span>Drag to orbit · wheel to zoom · 1-block grid</span></div>
					</div>
		
					<div class="page-content">
						<section id="path-page" class="page-section">
							<h3>Production route</h3>
							<ol id="multi-path" class="multi-path" aria-label="Selected multi-tick path"></ol>
						</section>
		
						<section id="layer-page" class="page-section" hidden>
							<div class="graph-heading">
								<h3>Layer expansion</h3>
							</div>
							<div class="graph-shell">
								<svg id="multi-flow" role="img" aria-label="Search expansion across implicit client ticks"></svg>
							</div>
							<div class="multi-inspector">
								<div>
									<div class="multi-inspector-heading">
										<strong id="multi-layer-title">Simulated tick</strong>
										<span id="multi-layer-status">Candidate funnel</span>
									</div>
									<div id="multi-funnel" class="multi-funnel" aria-label="Candidate retention funnel"></div>
								</div>
								<dl id="multi-layer-metrics" class="metrics multi-layer-metrics"></dl>
							</div>
						</section>
		
						<section id="branch-page" class="page-section" hidden>
							<h3>Candidate state</h3>
							<div class="branch-detail" aria-live="polite">
								<strong id="branch-title" class="fit-line">Branch</strong>
								<p id="branch-config"></p>
								<p id="branch-motion"></p>
								<p id="branch-finish"></p>
							</div>
							<div class="candidate-children">
								<div class="candidate-children-heading">
									<h3>Alternatives from this candidate</h3>
									<span id="candidate-child-summary"></span>
								</div>
								<div id="candidate-children" class="candidate-child-list"></div>
							</div>
						</section>
		
						<details id="stage-drilldown" class="stage-drilldown">
							<summary>How first-tick alternatives were constructed</summary>
							<div class="stage-drilldown-body">
								<div class="graph-shell">
									<svg id="flow" role="img" aria-label="Branch expansion across physics search stages"></svg>
								</div>
								<div id="stage-picker" class="stage-picker" aria-label="Inspect a branch stage"></div>
								<section class="details">
									<div class="detail">
										<h2 id="stage-title">Stage</h2>
										<p id="stage-summary" class="stage-summary"></p>
										<div id="fanout"></div>
									</div>
									<div class="detail">
										<h2>Configurations</h2>
										<div id="winner" class="winner-box"></div>
										<ol id="samples" class="samples"></ol>
									</div>
								</section>
							</div>
						</details>
					</div>
				</article>
			</section>
		
			<p class="explainer">The production route is the simulation actually selected. The candidate tree diagnostically replays every unique omission-safe continuation for up to three ticks; equivalent candidates remain visible as merged nodes without duplicating the same child tree.</p>
		</main>
		
		<script type="module">
		import * as THREE from 'three';
		import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
		
		const trace = __TRACE_DATA__;
		const ASSET_VERSION = '__ASSET_VERSION__';
		const CLIENT_PROTOCOL = __CLIENT_PROTOCOL__;
		const EXACT_LOSS = 0.0001;
		const ACCEPTED_LOSS = 0.01;
		const TICK_DURATION_MS = 100;
		const TERRAIN_RETENTION_TICKS = 200;
		const TERRAIN_BLOCK_RADIUS = 4;
		const TERRAIN_BELOW_PLAYER = 2;
		const TERRAIN_ABOVE_PLAYER = 3;
		const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
		const state = {
			tickIndex: 0,
			stageIndex: 0,
			multiLayerIndex: 0,
			branchIndex: 0,
			candidateId: -1,
			expandedCandidates: new Set(),
			pathIndex: 0,
			focusKind: 'path',
			progress: 1,
			playing: false
		};
		const requestedTick = Number(new URLSearchParams(window.location.search).get('tick'));
		if (Number.isInteger(requestedTick)) {
			const requestedIndex = trace.findIndex(tick => tick.tick === requestedTick);
			if (requestedIndex >= 0) state.tickIndex = requestedIndex;
		}
		const tickInput = document.getElementById('tick');
		const previous = document.getElementById('previous');
		const play = document.getElementById('play');
		const next = document.getElementById('next');
		tickInput.max = String(Math.max(0, trace.length - 1));
		const recordedOrigins = trace.map(tick => [
			tick.environment.originX,
			tick.environment.originY,
			tick.environment.originZ
		]);
		const hasElytraInTrace = trace.some(tick => tick.environment.elytraEquipped);
		// PTRs do not contain the client's render state. Reconstruct its walking
		// accumulator from recorded movement, independently of the focused branch.
		// LivingEntity/WalkAnimationState: distance * 4, capped at 1, damping .4.
		function walkingStatesFor(ticks) {
			let speed = 0;
			let position = 0;
			return ticks.map(tick => {
				const previousSpeed = speed;
				const previousPosition = position;
				const target = Math.min(1, Math.hypot(tick.actualX, tick.actualZ) * 4);
				speed += (target - speed) * .4;
				position += speed;
				return { previousSpeed, speed, previousPosition, position };
			});
		}
		const walkingStates = walkingStatesFor(trace);
		const baseOrigin = recordedOrigins[0] ?? [0, 0, 0];
		const originOffset = index => recordedOrigins[index].map(
			(value, axis) => value - baseOrigin[axis]
		);
		const lowestReplayY = trace.length === 0
			? 0
			: trace.reduce((lowest, tick, index) => {
				const startY = recordedOrigins[index][1] - baseOrigin[1];
				return Math.min(lowest, startY, startY + tick.actualY);
			}, Number.POSITIVE_INFINITY);
		
		const svg = document.getElementById('flow');
		const multiSvg = document.getElementById('multi-flow');
		const NS = 'http://www.w3.org/2000/svg';
		const element = (name, attributes = {}) => {
			const node = document.createElementNS(NS, name);
			for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
			return node;
		};
		const expandScientificNotation = text => {
			if (!/[eE]/.test(text)) return text;
			const [coefficient, exponentText] = text.toLowerCase().split('e');
			const exponent = Number(exponentText);
			const negative = coefficient.startsWith('-');
			const unsigned = negative ? coefficient.slice(1) : coefficient;
			const [whole, fraction = ''] = unsigned.split('.');
			const digits = whole + fraction;
			const decimalIndex = whole.length + exponent;
			const sign = negative ? '-' : '';
			if (decimalIndex <= 0) return `${sign}0.${'0'.repeat(-decimalIndex)}${digits}`;
			if (decimalIndex >= digits.length) return `${sign}${digits}${'0'.repeat(decimalIndex - digits.length)}`;
			return `${sign}${digits.slice(0, decimalIndex)}.${digits.slice(decimalIndex)}`;
		};
		const trimDecimal = text => text.includes('.')
			? text.replace(/(\\.\\d*?[1-9])0+$/, '$1').replace(/\\.0+$/, '')
			: text;
		const formatDecimal = (value, significantDigits = 15) => {
			if (!Number.isFinite(value)) return '—';
			if (value === 0) return '0';
			const precision = Math.max(1, Math.min(15, significantDigits));
			return trimDecimal(expandScientificNotation(Number(value).toPrecision(precision)));
		};
		
		const fitOf = loss => loss <= EXACT_LOSS
			? { key: 'exact', label: 'Exact', color: 0x35b779 }
			: loss <= ACCEPTED_LOSS
				? { key: 'accepted', label: 'Accepted', color: 0xe4a23a }
				: { key: 'mismatch', label: 'Mismatch', color: 0xe5655d };
		
		const viewport = document.getElementById('world-viewport');
		const darkTheme = window.matchMedia('(prefers-color-scheme: dark)').matches;
		const scene = new THREE.Scene();
		scene.background = new THREE.Color(darkTheme ? 0x101318 : 0xf4f6f8);
		const camera = new THREE.PerspectiveCamera(52, 1, 0.02, 100);
		camera.position.set(5.6, 4.1, 6.4);
		const renderer = new THREE.WebGLRenderer({ antialias: true });
		renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
		renderer.shadowMap.enabled = true;
		renderer.shadowMap.type = THREE.PCFShadowMap;
		viewport.prepend(renderer.domElement);
		const controls = new OrbitControls(camera, renderer.domElement);
		controls.target.set(0, .75, 0);
		controls.enableDamping = true;
		controls.minDistance = 2;
		controls.maxDistance = 18;
		const ambient = new THREE.HemisphereLight(0xffffff, 0x49515d, darkTheme ? 1.55 : 1.25);
		scene.add(ambient);
		const sun = new THREE.DirectionalLight(0xffffff, darkTheme ? 2.3 : 1.8);
		sun.position.set(4, 8, 5);
		sun.castShadow = true;
		scene.add(sun);
		const terrainGroup = new THREE.Group();
		const cherryParticleGroup = new THREE.Group();
		const cherryEmitters = new Map();
		let cherryParticleMaterials = null;
		// Integrate FallingLeavesParticle's flow acceleration once (300 ticks).
		const cherryFlow = [0];
		let cherryFlowSpeed = 0;
		for (let age = 1; age <= 300; age++) {
			cherryFlowSpeed += .005 * (age / 300) ** 1.25;
			cherryFlow.push(cherryFlow[age - 1] + cherryFlowSpeed);
		}
		const pathGroup = new THREE.Group();
		const possibilitiesGroup = new THREE.Group();
		const playerGroup = new THREE.Group();
		const referencePlane = new THREE.Mesh(
			new THREE.PlaneGeometry(64, 64),
			new THREE.MeshBasicMaterial({
				color: darkTheme ? 0x171c23 : 0xe9edf2,
				transparent: true,
				opacity: darkTheme ? .22 : .34,
				side: THREE.DoubleSide,
				depthWrite: false
			})
		);
		referencePlane.rotation.x = -Math.PI / 2;
		referencePlane.position.y = lowestReplayY - .006;
		referencePlane.renderOrder = -2;
		const referenceGrid = new THREE.GridHelper(
			64,
			64,
			darkTheme ? 0x8dc8ff : 0x1769aa,
			darkTheme ? 0x3b4653 : 0xb6c1cc
		);
		referenceGrid.position.y = lowestReplayY - .003;
		referenceGrid.material.transparent = true;
		referenceGrid.material.opacity = darkTheme ? .46 : .5;
		referenceGrid.material.depthWrite = false;
		referenceGrid.renderOrder = -1;
		scene.add(referencePlane, referenceGrid, terrainGroup, cherryParticleGroup, pathGroup, possibilitiesGroup, playerGroup);
		const terrainCells = new Map();
		let renderedPathTick = -1;
		let worldActors = null;
		let playbackStartedAt = 0;
		
		const textureData = Object.freeze({
			sand: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAACmElEQVR42j1TWU8TURS+P9gnH3zEqMRIlCgUIlD21pYiiyyphKW1DVhKW9rpNtNlOqWFUhqtIUFRSfy83xn04WY6nXO+7ZyrEpFhtKwlnJlB1ErT6NgrqBankU+PwcpPoVV/B9uch5Xzwin5UTcWcPs9j5PDYVTyXqiGuSCFzdIyLmsbiG6/kObcySgK6XG0nAg61XUADurlWTQKPqkn2FX7PVSrvIx+K4xf/TjK2QnNNIlybkKrmZHDJqogSLe+gUp2FtnjN0Ly80sMKp14jfLphEik1FzSA6eyAOPEA7vow4flhxr4rcglUNdZw2VjVWxGd55AnTeCUkD0f0+eTmVNnrnEODIJl5EqzNQc4vuD6J+HkYy/hMpoBY41r2WNap/X4vuqvQJmYxtBsXPd2xYrR5HnUmekPKKAQGo/PACnEEIqOiaeoztP0ar5xBJZmQtBqAa3adRLc6KEdiuZRSgisaBTXZN0CUL/F40l3N0cuLkcusFSHfPhxPq9tACpZmVRFHw9C+sxrqNZDEkhGfl/KT0F/MkjtvtM2Bl4LeuHrcdJMhXbG4RleHFurUrSVGHnAxIeLVAhG3n63Q15b5ZCMDMz6OmslKMVNItLCPke4EInn/g0JGpMY1KYrju7OsRNdJohmRIB7u562pL7W53qEbHxwgkgeTAinhkcGblUfJffWhl+x0UVD3Pa2xqA4hayiL6/XW5KSPmUXuGqH5GPj5GIDt2DeaWJ8kmYS45IHqphBSRNbuTW6iMpIgMBb7oHkviPK/c+0G7b9t9PbR2l5Kx7Fyw9T1PLzmrURiGoP7h34Ezvw3HslV60RextD/xvTEU9UktwRRltOyAfORHAkM1MxNwwGRSDpDLeRKfol/Gx/rMO/C9Xyk2G8nSvpQAAAABJRU5ErkJggg==',
			sandstoneSide: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAC0ElEQVR42j1TaVPaUBTNH24tase2o92s2+h0mel0puICIpW6UjYVBAOyCAKyExIgLAEBddraH3B6c1E/nORlXu55955zntBTf+G65WF0aw40yrtoSLu463vRb7gZw7Wd14Omh791DJouCB3lJ8rJDVQyG1ByZn6XUhbkEqtIRj6jeGlEIWWElF3n/XzaCE21Q6t70K7uQ4iIsyjQT7mLT4ievkP87D2iwRkkIgsIn04jIk4jJr7GmfcVQt5RpGJzSMW/IeRfRNA7BSEe+oi7mxA6NTduNTcS4Tf4Owg/ttlr+KBVPfC7R/hbX1+prsd9IRF+CylnoRZXULr8zgSqZCJCG2lhhUTdBb0GtCr7tG9EtWCCVnOilF5FrWiCUM5scqEOpbDOBI3KHm1aSTQn1PImwv5xItiDlFmFnF1DrWAlol2I3hcQ5NwPpM+XGInIPBNc0Pyq5EKr6sTpoYEJoqRVU9lHTdpBKbOOWumeQBfmTz/INvZUB89/0wkgHl6CLvBN28X4N/CxoMnYVxZaL77tnkLQH5W8FW15H0rWTK2ZyY1JLuo1fZBzO6jkVtBStpFJLN7770YkMDUkKNLsDZkEqwwhk9/hkzH0VSeqRduw7bINxfQaxKMREtcOVd5GyPcSdRqHRhgjtslHFNPLSEZn+FQdnbqT3stQSmbEQzOkwQGigXGGePgMwu/uER7QlHdxTiE6cT9FxP+coReHfBOMQfuYC1vyFm47fobQbzooGMdcnIwu4LrtZLIH8fTCm26YBZbzNhJ3Hl1adyhgjYodQpda1AmKqRUeoZQ2k9/Wod80u05Qzm5RTpb5H/FolO6Bg2wmcbMWCKnYF8r+LDQSS8+/krfgigJUl+wIHE5APyAe0tO5Ta3b2Eqf8wkFbAMBz1PqgFq5bh/SJaHwlE2IBT8wguSzphxQnC3sjHhkoNs5hx6dfqt5yCkDjXqM/6F/Rx5wEVOFAAAAAElFTkSuQmCC',
			sandstoneTop: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAACpklEQVR42i1T6W7TQBjc9xdCKm0c57CT2E56AxIST9C0JYcd3/fVJFAkxCsMsyt+WNba3zHXilN5g7fCQepOkR0M9NkSZbBAvBsj2F6hTizUsY3Um6BNLf6fIz3McKxW+N1/hmjiFapgiS65Rh1aSHYGvLUO70WD+3KJIhoj86eI3TGOhY06NfCzu+Nzj/fuC0QZ2aqpSWzkBxPe65CNOtz1JQ4/rpC4A6S+jsjVcGqWOLd3aHMHPYedqluILnNQBCY3GXzPEO11ZJ7JYQZRLRF7GhGMcXjVUMUGTjURx3NFK9oOIXIJb6+hjKbcyG0Hnc1T8iUtDvfZWEczpU/qm/8HLBRiqZsINkPkvoEynGK3/oTt0wXCnY43ivv3/B1NbHHTBJlrctGEAptqmBRaUhbyo4RUSUj7EZFMqP6QxTp+tV9VYUZEx9JBHhjUhPUR68MFHxtCHlJvSkgWBRxw6ozQTHIewKeIDXVokgUFW7JhrrSpIosUHQ6fQ2TBCNFuQKi2KijIs0tXqlGeK1pbkF4V0/tiRScemYF7vOUrajKC8DcDhLRKapF4OgWzGCwZphn21KTPl/w+JqoBh8gBD2jSa5Qx3QsNCPf5goeJmibtSsmxz2+IiENCU/Fvspmq2a0/osuv8ef4DefmVuVEyE0SmtSippiJa6gc9KSxf7mgPiMiGMHfXikBa9YV5J4w2jI7olaCEBK5lpF8O9zIWCcmAmqzefqAyCPFrYb98yWF1RT0LreIjjaeihu05JNwa0voZSgD4tD7kQqVRBDTXili6s2UrWXkqHNPIUWX2+iVx1N0pJL5C7REEBOutDXcjJR16UFC5s1M5jjXt+pmSnvFe/fA1DmKWx2bKnGSf+XP1b1IiayOFuoKn6s78jfUAhm8kpH+Bz/jSsbSjAQ4AAAAAElFTkSuQmCC',
			sandstoneBottom: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAACRUlEQVR42lVTyW4TQRCdX+aAxC8gTiFmbOx4nRnjKCgs4hQJCQ6GxI49i2dfPIuNgyD/8KiqYUZwaHV3dVW9qveqld/VNdKdhtXyOb7ePMExuUTqTPF4+CR3zxygjAxk3hR7fya2xKVzoNPdgHJKr8WYOjMUZJCg1QX8zaC2+yNk/hTOugePbO62L3ZeD8V7KIxcULZkN24fdpRguzyHvXopd+u2g+33MxzzGuDx9A5FpMlZEmReHfwz/yiLz5E9xN7TYa1ewN2oCKx+6/MjnyO0RgTUhcKOzUNoGQjM1xLEHDQ8MAgv9qligzjRCWBM3E2gcAA7+dQbJ+DFCZp2jtmcHCnAqqv01gPyGcL8psJZ9aCwkVsI7ZEg+H8rYHuVvG3bYVR7rf5XjXBwTOdtC/8iM3HW7bmci1BHHmjEyYyWhod8IfbTfgHFuVdh39WOCelfhgaq6I2gBOYQiT1tk0ZU+q/jh1pe6t/dqlC45NjtS4B116EkE2Suhi83z6iCV0Kau+nCJBk5cPn5qfgW0Zza7VMF6448sFS7+x5yX5cq2BYTSuZN0BDNqmQ0bJygjOd0HtYkNpKV0YwYHkuCQ1xz41LSRk7eeeylBUqcuCQjIzb/oIyvWpZlIql0Z33WBifugMZZRR5O2/lQKuqlDIwWRchyaLSdUa37tiuOkTNETCQXodaqJoNUJbrMdUCENEkyf06lD1qUZk/dWRsc0dzw71SqeCEPzX9g2Q7RArFVV8D6s7yheSH3kIaKiUx9jab0En8ACNs1Tj5jt3MAAAAASUVORK5CYII=',
			cactusSide: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAABdUlEQVR42m2Sy07DMBBFs2BR1KZ206Y0TR8UseAX2PILUCQWLHgICVUgQLwWQBFILCjwy8Zz4xnHaSONHNnjO54zN4rst76nTet9hOj8bhv9MTLJz8TEJz2Ds/kmIvkqgvLoLOKPkvTbEBfVXR8i9WmHEiCg74cQoP1VAkhUsxwi6iFHYnzWM7VdHQjwC+KjruF7UFjbqZnmRSYClESXXBVTP0jlBRTqMV9ugS6Cwzzsk8WZDxh58ZLAzAtwMES0YM+S70Kcii0xoCT1PEAitVIVQFsvxUotBQwoiZ8GAbsCpGPQ2E+LyrYAIJ53AThoAXCIw+dYeJQZtBcTKVBtwb/gyUG0JmocpuID8gbtEX2sVQaUxHAAiwROU/FB63Vs2n9b4hEWDxiIgJs3z5oFmFHZZMEUmpd9PFUE7L9APN4QATYbn3kn3mSohBYccYF4lUEY47TtlSbkIUplZ2X2e/lM/LDKiWxhGdX1wEN0DlW33mgs8A9AxWaJn/oUWAAAAABJRU5ErkJggg==',
			cactusTop: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAABCElEQVR42q1TywrCMBDswYMgamN7EKU+qgd/yA/w6A94EhFEEEQQ8X/XzJqNmybeWliSTaazM9kky9r4+rsxIeyUOpsuzzEil73mvst/BKNrTb1tSeYyp/xYUX6qOC8fa14ztwUN9hPGIQ8IwMzg9xcMEMBYx4i1Zh4QQJYwA1DclxwMtpW1AskjC80qsCB2zHnGP3IRO0ItcIGC4WFKxbP21bQigIVQInkGqMQWXqtYujobzF2XlAUH1BYYDPmuqt4PLAiBgFJdAJFWkLbgZPqqIHW2vKK/XXAbvn2J+6AVRRbwo/bp1ahDxIibifVIgVgQBci5tTbX9jBHRASJh5J8VBrXykv+AGuJLE9fjzDWAAAAAElFTkSuQmCC',
			cactusBottom: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA2ElEQVR42q2TbQ6DIAyGvcD+73q7l6DbjfwYTL52FNYiNQJqljmSptjSh5eCVfWPcbld/S+WALRhXmkW/NrWMWMbb10b5gUAk73gfpDgwbonCz638dV6Y/i2gpAEkINdyOibdkfIpgINVFTgokQsyL07BrAEQHJJOkFwzT5AzjJpoY3HSABHCoR6JAoIMMSiAAMwggoAXhcW0Znz7n/XRNlEBXwBrd8A5rAvhz1w7/vSB+vilUIMPSnYfQedKLvfS77ExokU7AByyUrXYT6pusgXgFM/05nxAYA6LLbB+a9mAAAAAElFTkSuQmCC',
			slime: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAAXNSR0IArs4c6QAAAAZiS0dEAAAAAAAA+UO7fwAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB90MEg4qGCsNrjAAAAKTSURBVDjLBcFrcts2FIDRD8AFQFKUbfkRu05mMtNf7dK6hywtG+gy2riJI1m2HIovPHuO+vL3X1+1KF6OJ5Y08PH2E0MJ7OyWaFZc9Pz7+g8mGtzW0XcdtjhqBmUEXYrBOCGHQGs2KK9olbCqmboqkIAAqi2E0wKisWKxzqI1iNVCqoGHmwfEa9a3hWM+UKj8fv2Zl/jOzd0HFGC2DpcdwQZCCIhR6DkPsILdWIKNdI9bzKhojGdYJjayQYsC76DLDDLw8rzn9OuI1Qbddg0pVU7zCVeF6X3h8mHH7uYK5zYsS6CpHvW2kCZNV1uSRMbzSi4gUj2lm7DZkYeC7TV5tJR3hVwm3Go5DzMvwyu9b1G7lpvrO+Syoi3owC9iyvz39Mzz8I0UA6XO6K4yvi14bVHOEOvKuEy0eAiVGCC8g1RlMFGBM1TRxHOl6S7wIhzTE+/nA3e3n/jj/k+im8kZXONY9EhKBenZsriZi82Gi6sdyzQT0kqOiVIgLRVTFaf5hNWWGjPGOk7PI8UEJFPQWnHZXqEWwWqPNKCy5v76kUlGtGiupOPnMnC93SEhoTWwGPTT/hvf9z/IZLqmkmvEWkvUAeqK6itKFMFUOuWIaWKUhaIiZmMQiyWoGe8947zgPaQ1YJWlSGF6e2MOM/11g1EeZRTmbPlw90iIEe1dz+PuM2nNzGrl6ceew/6A9ZZaEsuQyDnTsuXwuuf48xXtQa8aowVxvaJKIs8VWkPbeuZxYVzObNKW+9+gBFjNhNIKI4o8BiYd8cEiTdOwDonNpoVz4TjNNLbD25ZzGWnpoa9oKay3ld42xKnw+v2AciBxDphOKFERdOLx4SN5hKLAYck5UNdKbAtdsBityRSUAeUV/wODp19CzoDzswAAAABJRU5ErkJggg==',
			deadBush: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAb0lEQVR42mNgIACmpGj8Z6AEUGQASHO0Cdt/sg3EZQC6GIoksg0wPkgDCKPLYwUwheiGoBtElCHINMzZOJ2PzxCiNeFyNrKhBDUga8TGxhuQMOfCbEIPPGQ1OA1Bl8AVDkQZgByQZCflgTWAGC8AAK2kmu5uaXl2AAAAAElFTkSuQmCC',
			skin: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAGVklEQVR4Xu1aPUsdTRjd2koQK0HsUlgYQkC0TRqxs7HQ0gSCtYXiDxALwVSJgoKgICkUQZGUfpeWklr9B6n35Qyefc89d/bu3g/vvRoPHNzdeWbunDPPzO7OmiQFuLi4SO/u7tLr6+sKnp6eBi4sLASurq6mW1tbVfT2GsHDw0NL2mkIEAkTQIo/OTkJVAPm5uaC4Kurq2BYKw3oKPb29lIS4n/8+BHEr6yspL9//87EQzh5f38fCCO8vXagpRkDwTHCgPPz8yD+8PAwEOcQ/ffv3ywLvL3nBsW3zASIXVxcrBIPwcgAGEATaADZCQOAlokHIB7kXIf4PANoAtkpA1qKmPidnZ0qA6ampioMeDWLIA1Q8TQA/PbtWyANAH/9+hW4vr7e/QZgruIWh1va2tpa4Pz8fEh7HX2Q4tUAZgJG/fHxMcsAXOOzAom7CH+PC6X3pxmUnfsVCyXF0wCI//r1a0aKR5k/5NAAHEM0RUE8TWHbfJbgAslz71yjqHf1z+LQuc3NzXRpaSkzYHZ2NhAGfPnyJcsMiEXnIQzHu7u7Gf/8+RPKcP3m5iYjYm9vbzPBagaOrV+FqCUwVha7VgGI19SfnJwMhAEzMzPp8vJyNto6yjg/ODgIpAkceQjnSDMeJpydnVVNC+9PLTQ6yjXjaQDE04CxsbH08+fPVQZQFIzAIucGfP/+PSPjKJ4GHB0dpRsbG4H4be9PEWqKiaAwHi8xIG5lEAzCAB7TAJ/3agCuq3is/jQAokmIB7e3txs2oOWAeD7Pqwkkb3OgGwChMSLORat4GgB6f9oOvc3RBFCFkyo+zwCIx+jjL8U6IZyZ5/3pOPr6+tIPHz6kw8PD4e/AwEAFPd6BeuTHjx8DR0dHA3HMdvlXj/HX23OMjIxk7Vs9sjk0awBFayfVDBftx96eQw3Q+kk3GcAOcuNEjYiJrteAnz9/VtVPntOA/v7+wLIGkDQAnSbZrgsQITWh7Vi9xgyAYKd30MkyxOJYRRcZoJ1nnK4RPn28rrcRI8qTsoYMDQ2lg4ODgTjWczeG1Hh2emJiIqMKQWd47iIYPz09HYhjGvHp06eszvHxcdbO+Ph4YC0j6jLg3bt36fv37wNxTEKcCydpFOIoniJcDAlBeq7EnNa6mhFsy811M0mWJWUNUPF6XJQBjEdHtYNuBgWiUyqanY1l0OXlZYUBoMZrBpBslyYlZQ3Q1NcMwKLnwkmUcRroSLODFO0jpx1GqiIrNDPcPB1xp7aFKeIZkpQ1QFNeDSjKAGYBfiy2YOloawyoZWWEq2n+W6xP8veSsgYw7ZVl1gBOAV+AvJMuVDvppugo8jpGF+fajhoNajmOUScpawDv8TG6cFJjXDTIkcsbYV3o2Gkesw0+7LhxjNfMoAm8hpikrAE9PT1pHpP/G6mgxmgn1AB2moIhSA1RQfv7+6EOb30qTk1DG2wnzwD+pvS3Nnp7e9M8JhHxoMb46NMAjkRMsBqmIrQOs0HXBa+v4vkbvKVKf2vDRYNMb88I0g3QJ0Seeye93M/rjVfDlSxLyhrgLzv+7F/EWh3U617u537dy/2cx866DXjDG95QCK4Jfv3VAguJ/i1b9oY3vHDoy5CXNQM8nfm1rgL3A/x6M+h60W+IQPcEvKxTiO0HeIzjaT+gPPw+/0/d82OQ/YAoispbibz9AI8jZD/gdUDF6H6AxihkP6Bx+JSoF7GnxNg1IlYWu9Y2tMoAvsfrtRjqje9qoOPcmMAGqm+geLxDt8Ze5AbHcxjwlA3/lgH+PwBJJw2oZ02IGaB7jB7v0I3RjkyBMouQxsSoBsSoMb4b7NTvCbolnrTLkCK4ODDv/w38HLEuOI/88MEvzUk3G6DfDmPUb48uFMSzvH4cAT0m6WYD9OtxjLUyQL/y6NegFzUFsOC5AZr6IGLUABeqJvD7f9sNKPN67OLB2BRwMzgF+KrLOe6vvm4GY5N2GKDI2zFy8aCPvor2RVDF6zRwM7gWgDhPCgzIq+9xufD9gbznABcPMr6IiMWoqng3wc85HZICA5pC0fu+lWtnAv1rch4Rywxw8fruz3d8sigDiup7fLOoMsA/t+cRsS6eBtRaA8oYIHEBWl9jo6iV8g4fVZD1XbATsTq3ST4p8tjPn+ZyrgFNox4D/GXHn/2L6OLbbcB/gDSQbVm5hTIAAAAASUVORK5CYII='
		});
		const textureLoader = new THREE.TextureLoader().setCrossOrigin('anonymous');
		const pixelTexture = source => {
			const texture = textureLoader.load(source, loaded => {
				const image = loaded.image;
				if (image?.width && image.height > image.width && image.height % image.width === 0) {
					const firstFrame = image.width / image.height;
					loaded.repeat.set(1, firstFrame);
					loaded.offset.set(0, 1 - firstFrame);
					loaded.needsUpdate = true;
				}
			});
			texture.colorSpace = THREE.SRGBColorSpace;
			texture.magFilter = THREE.NearestFilter;
			texture.minFilter = THREE.NearestFilter;
			texture.generateMipmaps = false;
			return texture;
		};
		const textures = { skin: pixelTexture(textureData.skin) };
		const assetTextures = new Map([['embedded:skin', textures.skin]]);
		const legacyAssets = ASSET_VERSION === '1.8.8';
		const assetVersionParts = ASSET_VERSION.split('.').map(Number);
		const equipmentAssets = assetVersionParts[0] > 1 || assetVersionParts[1] > 21
			|| (assetVersionParts[1] === 21 && (assetVersionParts[2] ?? 0) >= 2);
		const assetTexture = reference => {
			if (assetTextures.has(reference)) return assetTextures.get(reference);
			const separator = reference.indexOf(':');
			const requestedFolder = separator < 0 ? 'block' : reference.slice(0, separator);
			const name = separator < 0 ? reference : reference.slice(separator + 1);
			const folder = legacyAssets && requestedFolder === 'block'
				? 'blocks'
				: legacyAssets && requestedFolder === 'item'
					? 'items'
					: requestedFolder;
			const source = `https://assets.mcasset.cloud/${encodeURIComponent(ASSET_VERSION)}/assets/minecraft/textures/${folder}/${name}.png`;
			const texture = pixelTexture(source);
			assetTextures.set(reference, texture);
			return texture;
		};
		
		const textureAliases = Object.freeze({
			BARRIER: 'item:barrier',
			BUBBLE_COLUMN: 'water_still',
			COBBLESTONE_WALL: 'cobblestone',
			DEAD_BUSH: legacyAssets ? 'deadbush' : 'dead_bush',
			DEEPSLATE_TILE_WALL: 'deepslate_tiles',
			LEGACY_HUGE_MUSHROOM_2: 'mushroom_block_skin_red',
			LEGACY_LONG_GRASS: 'tallgrass',
			NETHER_BRICK_FENCE: legacyAssets ? 'nether_brick' : 'nether_bricks',
			NETHER_BRICK_SLAB: legacyAssets ? 'nether_brick' : 'nether_bricks',
			PLAYER_HEAD: 'embedded:skin',
			POLISHED_DEEPSLATE_SLAB: 'polished_deepslate',
			REDSTONE_WIRE: legacyAssets ? 'redstone_dust_cross' : 'redstone_dust_dot',
			SKELETON_SKULL: 'entity:skeleton/skeleton',
			SLIME_BLOCK: legacyAssets ? 'slime' : 'slime_block',
			SPRUCE_FENCE: 'spruce_planks',
			SPRUCE_WALL_SIGN: 'spruce_planks',
			WATER: 'water_still',
			LAVA: 'lava_still'
		});
		const crossedBlocks = new Set([
			'AZURE_BLUET', 'BARRIER', 'BUBBLE_CORAL_FAN', 'BUBBLE_CORAL_WALL_FAN',
			'BUSH', 'COBWEB', 'DEAD_BUSH', 'FIRE_CORAL_FAN', 'FIRE_CORAL_WALL_FAN',
			'KELP', 'KELP_PLANT', 'LEGACY_LONG_GRASS', 'SHORT_GRASS', 'TALL_GRASS',
			'TUBE_CORAL', 'WHITE_TULIP'
		]);
		const flatBlocks = new Set(['LEAF_LITTER', 'PINK_PETALS', 'REDSTONE_WIRE']);
		const foliageBlocks = new Set([
			'BIRCH_LEAVES', 'BUSH', 'CHERRY_LEAVES', 'LEGACY_LEAVES_2',
			'LEGACY_LONG_GRASS', 'OAK_LEAVES', 'SHORT_GRASS', 'TALL_GRASS'
		]);
		const solidRenderBlocks = new Set(['LAVA', 'SLIME_BLOCK']);
		const translucentBlocks = Object.freeze({
			BARRIER: .72,
			BUBBLE_COLUMN: .56,
			GLASS: .32,
			HONEY_BLOCK: .82,
			WATER: .56
		});
		
		const resizeWorld = () => {
			const width = Math.max(1, viewport.clientWidth);
			const height = Math.max(1, viewport.clientHeight);
			renderer.setSize(width, height, false);
			camera.aspect = width / height;
			camera.updateProjectionMatrix();
		};
		new ResizeObserver(resizeWorld).observe(viewport);
		resizeWorld();
		renderer.setAnimationLoop(now => {
			updatePlayback(now);
			updateCherryParticles(now / 1000);
			controls.update();
			renderer.render(scene, camera);
		});
		
		function clearGroup(group) {
			while (group.children.length) {
				const child = group.children[0];
				group.remove(child);
				child.traverse(object => {
					if (object.isInstancedMesh) object.dispose();
					object.geometry?.dispose();
					if (Array.isArray(object.material)) object.material.forEach(material => material.dispose());
					else object.material?.dispose();
				});
			}
		}
		
		function stairTextureMaterial(material) {
			// Stairs use their block family's textures; their recorded collision
			// boxes already supply the straight, corner, and upside-down geometry.
			const base = material.slice(0, -'_STAIRS'.length);
			if (['OAK', 'SPRUCE', 'BIRCH', 'JUNGLE', 'ACACIA', 'DARK_OAK',
				'MANGROVE', 'CHERRY', 'PALE_OAK', 'BAMBOO', 'CRIMSON', 'WARPED'].includes(base)) {
				const wood = base.toLowerCase();
				return legacyAssets ? `planks_${wood === 'dark_oak' ? 'big_oak' : wood}` : `${wood}_planks`;
			}
			const aliases = {
				BRICK: legacyAssets ? 'brick' : 'bricks',
				STONE_BRICK: legacyAssets ? 'stonebrick' : 'stone_bricks',
				NETHER_BRICK: legacyAssets ? 'nether_brick' : 'nether_bricks',
				PURPUR: 'purpur_block', QUARTZ: 'quartz_block_side', SMOOTH_QUARTZ: 'quartz_block_bottom',
				SMOOTH_SANDSTONE: 'sandstone_top', SMOOTH_RED_SANDSTONE: 'red_sandstone_top'
			};
			const plural = base.endsWith('_BRICK') || base.endsWith('_TILE') ? 's' : '';
			return aliases[base] ?? base.replace('WAXED_', '').toLowerCase() + plural;
		}

		function textureReference(material, properties) {
			if (material.endsWith('_STAIRS')) return stairTextureMaterial(material);
			if (material.endsWith('_CORAL_WALL_FAN')) {
				return material.replace('_WALL_FAN', '_FAN').toLowerCase();
			}
			if (material === 'TALL_GRASS') {
				return properties.toLowerCase().includes('half=top')
					? 'tall_grass_top'
					: 'tall_grass_bottom';
			}
			if (material === 'LEGACY_LEAVES_2') {
				return properties.toLowerCase().includes('dark')
					? 'leaves_big_oak'
					: 'leaves_acacia';
			}
			return textureAliases[material] ?? material.toLowerCase();
		}
		
		function blockFaces(material, properties) {
			const reference = textureReference(material, properties);
			let side = reference;
			let top = reference;
			let bottom = reference;
			let sideTint = 0xffffff;
			let topTint = 0xffffff;
			let bottomTint = 0xffffff;
		
			if (material === 'GRASS_BLOCK' || material === 'LEGACY_GRASS') {
				side = legacyAssets ? 'grass_side' : 'grass_block_side';
				top = legacyAssets ? 'grass_top' : 'grass_block_top';
				bottom = 'dirt';
				topTint = 0x91bd59;
			} else if (material === 'PODZOL') {
				side = 'podzol_side';
				top = 'podzol_top';
				bottom = 'dirt';
			} else if (['SANDSTONE', 'SANDSTONE_STAIRS', 'RED_SANDSTONE_STAIRS'].includes(material)) {
				const stone = material.startsWith('RED_') ? 'red_sandstone' : 'sandstone';
				side = legacyAssets ? `${stone}_normal` : stone;
				top = `${stone}_top`;
				bottom = `${stone}_bottom`;
			} else if (material === 'QUARTZ_STAIRS') {
				top = 'quartz_block_top';
				bottom = 'quartz_block_bottom';
			} else if (material === 'CACTUS') {
				side = 'cactus_side';
				top = 'cactus_top';
				bottom = 'cactus_bottom';
			} else if (material === 'DAYLIGHT_DETECTOR') {
				side = legacyAssets ? 'daylight_detector_side' : 'daylight_detector_side';
				top = legacyAssets ? 'daylight_detector_top' : 'daylight_detector_top';
				bottom = legacyAssets ? 'planks_oak' : 'oak_planks';
			} else if (material === 'JUKEBOX') {
				side = 'jukebox_side';
				top = 'jukebox_top';
				bottom = legacyAssets ? 'planks_oak' : 'oak_planks';
			} else if (material === 'HONEY_BLOCK') {
				side = 'honey_block_side';
				top = 'honey_block_top';
				bottom = 'honey_block_bottom';
			} else if (material === 'OAK_LOG' || material === 'CHERRY_LOG') {
				const wood = material === 'OAK_LOG' ? 'oak' : 'cherry';
				side = `${wood}_log`;
				top = `${wood}_log_top`;
				bottom = top;
			} else if (material === 'LEGACY_LOG_2') {
				const wood = properties.toLowerCase().includes('dark') ? 'big_oak' : 'acacia';
				side = `log_${wood}`;
				top = `log_${wood}_top`;
				bottom = top;
			}
		
			if (foliageBlocks.has(material)) {
				const tint = material === 'CHERRY_LEAVES' ? 0xffffff : material === 'BIRCH_LEAVES' ? 0x80a755 : 0x77ab2f;
				sideTint = tint;
				topTint = tint;
				bottomTint = tint;
			}
		
			return {
				side: assetTexture(side),
				top: assetTexture(top),
				bottom: assetTexture(bottom),
				sideTint,
				topTint,
				bottomTint,
				opacity: translucentBlocks[material] ?? 1,
				alphaTest: crossedBlocks.has(material) || flatBlocks.has(material) || foliageBlocks.has(material) ? .18 : .01,
				crossed: crossedBlocks.has(material),
				flat: flatBlocks.has(material),
				centered: material === 'BARRIER' || material === 'COBWEB',
				emissive: material === 'LAVA' ? 0x5c2200 : 0x000000
			};
		}
		
		function faceMaterial(map, opacity = 1, tint = 0xffffff, alphaTest = .01, emissive = 0x000000) {
			return new THREE.MeshStandardMaterial({
				map,
				color: tint,
				emissive,
				emissiveIntensity: emissive ? .35 : 0,
				transparent: opacity < 1,
				opacity,
				alphaTest,
				roughness: .92,
				metalness: 0,
				depthWrite: opacity > .8
			});
		}
		
		function addCrossedBlock(group, block, offset, faces) {
			const [, , x, y, z, sx, sy, sz] = block;
			const width = Math.max(.18, Math.max(sx, sz) * .9);
			const height = Math.max(.18, sy * (faces.centered ? .92 : .86));
			const plant = new THREE.Group();
			plant.position.set(
				x + offset[0],
				(faces.centered ? y : y - sy / 2 + height / 2) + offset[1],
				z + offset[2]
			);
			for (const angle of [Math.PI / 4, -Math.PI / 4]) {
				const geometry = new THREE.PlaneGeometry(width, height);
				const material = faceMaterial(
					faces.side, faces.opacity, faces.sideTint, faces.alphaTest, faces.emissive
				);
				material.side = THREE.DoubleSide;
				const plane = new THREE.Mesh(geometry, material);
				plane.rotation.y = angle;
				plane.castShadow = faces.opacity > .8;
				plant.add(plane);
			}
			group.add(plant);
		}
		
		function addFlatBlock(group, block, offset, faces) {
			const [, , x, y, z, sx, sy, sz] = block;
			const geometry = new THREE.PlaneGeometry(sx * .92, sz * .92);
			const material = faceMaterial(
				faces.top, faces.opacity, faces.topTint, faces.alphaTest, faces.emissive
			);
			material.side = THREE.DoubleSide;
			const plane = new THREE.Mesh(geometry, material);
			plane.rotation.x = -Math.PI / 2;
			plane.position.set(x + offset[0], y - sy / 2 + .012 + offset[1], z + offset[2]);
			plane.receiveShadow = true;
			group.add(plane);
		}
		
		function addBlockBatch(group, batch, offset) {
			const { material, collidable, properties, blocks } = batch;
			if (material === 'BREWING_STAND') {
				// Only the wide base collider anchors a model. The narrow post is
				// another collision box for the same stand, not a second model.
				for (const block of blocks) {
					if (block[5] > .125 && block[7] > .125) addBrewingStand(group, block, offset);
				}
				return;
			}
			const faces = blockFaces(material, properties);
			const geometry = new THREE.BoxGeometry(1, 1, 1);
			if (material === 'PLAYER_HEAD') setSkinUVs(geometry, 0, 0, 8, 8, 8);
			if (material === 'SKELETON_SKULL') setSkinUVs(geometry, 0, 0, 8, 8, 8, 64, 32);
			const opacity = collidable || solidRenderBlocks.has(material)
				? faces.opacity
				: Math.min(faces.opacity, .72);
			const materials = [
				faceMaterial(faces.side, opacity, faces.sideTint, faces.alphaTest, faces.emissive),
				faceMaterial(faces.side, opacity, faces.sideTint, faces.alphaTest, faces.emissive),
				faceMaterial(faces.top, opacity, faces.topTint, faces.alphaTest, faces.emissive),
				faceMaterial(faces.bottom, opacity, faces.bottomTint, faces.alphaTest, faces.emissive),
				faceMaterial(faces.side, opacity, faces.sideTint, faces.alphaTest, faces.emissive),
				faceMaterial(faces.side, opacity, faces.sideTint, faces.alphaTest, faces.emissive)
			];
			const mesh = new THREE.InstancedMesh(geometry, materials, blocks.length);
			const matrix = new THREE.Matrix4();
			for (let index = 0; index < blocks.length; index++) {
				const [, , x, y, z, sx, sy, sz] = blocks[index];
				matrix.makeScale(sx, sy, sz);
				matrix.setPosition(x + offset[0], y + offset[1], z + offset[2]);
				mesh.setMatrixAt(index, matrix);
			}
			mesh.instanceMatrix.setUsage(THREE.StaticDrawUsage);
			mesh.receiveShadow = true;
			mesh.castShadow = opacity > .8 && material !== 'SLIME_BLOCK';
			group.add(mesh);
		}
		
		function addBlocks(group, blocks, offset) {
			const batches = new Map();
			for (const block of blocks) {
				const [material, collidable, , , , , , , properties = ''] = block;
				if (crossedBlocks.has(material)) {
					const faces = blockFaces(material, properties);
					addCrossedBlock(group, block, offset, faces);
					continue;
				}
				if (flatBlocks.has(material)) {
					const faces = blockFaces(material, properties);
					addFlatBlock(group, block, offset, faces);
					continue;
				}
				const key = `${material}|${collidable}|${properties}`;
				let batch = batches.get(key);
				if (!batch) {
					batch = { material, collidable, properties, blocks: [] };
					batches.set(key, batch);
				}
				batch.blocks.push(block);
			}
			for (const batch of batches.values()) addBlockBatch(group, batch, offset);
		}
		
		const stableCoordinate = value => Math.round(value * 10000000) / 10000000;
		
		function terrainCellCoordinate(relative, origin) {
			return Math.floor(stableCoordinate(relative + origin));
		}
		
		function terrainCellKey(x, y, z) {
			return `${x}|${y}|${z}`;
		}
		
		function sceneBlock(block, offset) {
			const global = block.slice();
			for (let axis = 0; axis < 3; axis++) {
				global[axis + 2] = stableCoordinate(block[axis + 2] + offset[axis]);
			}
			return global;
		}
		
		function sameBlockList(left, right) {
			return left.length === right.length && left.every((block, blockIndex) => {
				const previous = right[blockIndex];
				return block.length === previous.length
					&& block.every((component, componentIndex) => component === previous[componentIndex]);
			});
		}
		
		function observedTerrainBounds(environment) {
			const x = Math.floor(environment.originX);
			const y = Math.floor(environment.originY);
			const z = Math.floor(environment.originZ);
			return {
				minX: x - TERRAIN_BLOCK_RADIUS,
				maxX: x + TERRAIN_BLOCK_RADIUS,
				minY: y - TERRAIN_BELOW_PLAYER,
				maxY: y + TERRAIN_ABOVE_PLAYER,
				minZ: z - TERRAIN_BLOCK_RADIUS,
				maxZ: z + TERRAIN_BLOCK_RADIUS
			};
		}
		
		function cellIsObserved(cell, bounds) {
			return cell.x >= bounds.minX && cell.x <= bounds.maxX
				&& cell.y >= bounds.minY && cell.y <= bounds.maxY
				&& cell.z >= bounds.minZ && cell.z <= bounds.maxZ;
		}
		
		function renderTerrain(tick, offset) {
			const environment = tick.environment;
			const observedCells = new Map();
			for (const block of environment.blocks) {
				const x = terrainCellCoordinate(block[2], environment.originX);
				const y = terrainCellCoordinate(block[3], environment.originY);
				const z = terrainCellCoordinate(block[4], environment.originZ);
				const key = terrainCellKey(x, y, z);
				let cell = observedCells.get(key);
				if (!cell) {
					cell = { x, y, z, blocks: [] };
					observedCells.set(key, cell);
				}
				cell.blocks.push(sceneBlock(block, offset));
			}
		
			let dirty = false;
			for (const [key, cell] of observedCells) {
				const previous = terrainCells.get(key);
				if (!previous || !sameBlockList(cell.blocks, previous.blocks)) dirty = true;
				terrainCells.set(key, { ...cell, lastSeenTick: tick.tick });
			}
		
			const bounds = observedTerrainBounds(environment);
			for (const [key, cell] of terrainCells) {
				const observedAsAir = cellIsObserved(cell, bounds) && !observedCells.has(key);
				const tooOld = Math.abs(tick.tick - cell.lastSeenTick) > TERRAIN_RETENTION_TICKS;
				if (observedAsAir || tooOld) {
					terrainCells.delete(key);
					dirty = true;
				}
			}
		
			if (!dirty) return;
			clearGroup(terrainGroup);
			const retainedBlocks = [];
			for (const cell of terrainCells.values()) retainedBlocks.push(...cell.blocks);
			addBlocks(terrainGroup, retainedBlocks, [0, 0, 0]);
			syncCherryParticles();
		}
		
		function addHitbox(position, width, height, color, opacity, parent = playerGroup) {
			const geometry = new THREE.BoxGeometry(1, 1, 1);
			const edgeGeometry = new THREE.EdgesGeometry(geometry);
			geometry.dispose();
			const material = new THREE.LineBasicMaterial({ color, transparent: true, opacity });
			const outline = new THREE.LineSegments(edgeGeometry, material);
			outline.position.set(position[0], position[1] + height / 2, position[2]);
			outline.scale.set(width, height, width);
			parent.add(outline);
			return outline;
		}
		
		// Minecraft skin UV layout adapted from skinview3d's MIT-licensed model.
		function setSkinUVs(box, u, v, width, height, depth, atlasWidth = 64, atlasHeight = 64) {
			const face = (x1, y1, x2, y2) => [
				new THREE.Vector2(x1 / atlasWidth, 1 - y2 / atlasHeight),
				new THREE.Vector2(x2 / atlasWidth, 1 - y2 / atlasHeight),
				new THREE.Vector2(x2 / atlasWidth, 1 - y1 / atlasHeight),
				new THREE.Vector2(x1 / atlasWidth, 1 - y1 / atlasHeight)
			];
			const top = face(u + depth, v, u + width + depth, v + depth);
			const bottom = face(u + width + depth, v, u + width * 2 + depth, v + depth);
			const left = face(u, v + depth, u + depth, v + depth + height);
			const front = face(u + depth, v + depth, u + width + depth, v + depth + height);
			const right = face(u + width + depth, v + depth, u + width + depth * 2, v + depth + height);
			const back = face(u + width + depth * 2, v + depth, u + width * 2 + depth * 2, v + depth + height);
			const ordered = [
				[right[3], right[2], right[0], right[1]],
				[left[3], left[2], left[0], left[1]],
				[top[3], top[2], top[0], top[1]],
				[bottom[0], bottom[1], bottom[3], bottom[2]],
				[front[3], front[2], front[0], front[1]],
				[back[3], back[2], back[0], back[1]]
			];
			box.attributes.uv.set(new Float32Array(ordered.flatMap(points =>
				points.flatMap(point => [point.x, point.y])
			)));
			box.attributes.uv.needsUpdate = true;
		}
		
		""";

	private static final String HTML_TAIL = """
		function cherryParticlePosition(particle, age) {
			const index = Math.min(299, Math.floor(age));
			const flow = THREE.MathUtils.lerp(cherryFlow[index], cherryFlow[index + 1], age - index);
			return [particle.x + particle.windX * flow,
				particle.y - .000375 * age * (age + 1), particle.z + particle.windZ * flow];
		}

		function cherryParticleLifetime(particle) {
			let previous = cherryParticlePosition(particle, 0);
			for (let age = 1; age <= 300; age++) {
				const point = cherryParticlePosition(particle, age);
				const x = terrainCellCoordinate(point[0], baseOrigin[0]);
				const z = terrainCellCoordinate(point[2], baseOrigin[2]);
				const bottom = terrainCellCoordinate(point[1], baseOrigin[1]);
				const top = terrainCellCoordinate(previous[1], baseOrigin[1]);
				for (let y = bottom; y <= top; y++) {
					const cell = terrainCells.get(terrainCellKey(x, y, z));
					if (cell?.blocks.some(([, solid, bx, by, bz, sx, sy, sz]) => solid
						&& Math.abs(point[0] - bx) <= sx / 2 && Math.abs(point[2] - bz) <= sz / 2
						&& point[1] <= by + sy / 2 && previous[1] >= by - sy / 2)) return age - 1;
				}
				previous = point;
			}
			return 300;
		}

		function syncCherryParticles() {
			const origin = originOffset(state.tickIndex);
			const leaves = [];
			for (const [key, cell] of terrainCells) {
				const block = cell.blocks.find(block => block[0] === 'CHERRY_LEAVES');
				if (!block) continue;
				const below = terrainCells.get(terrainCellKey(cell.x, cell.y - 1, cell.z));
				// Vanilla only emits beneath leaves without a full supporting face.
				if (below?.blocks.some(([, solid, , y, , sx, sy, sz]) => solid && sx >= 1 && sz >= 1
					&& y + sy / 2 >= block[3] - block[6] / 2 - .000001)) continue;
				leaves.push({ key, block, distance: Math.hypot(block[2] - origin[0], block[3] - origin[1], block[4] - origin[2]) });
			}
			// Four staggered petals per leaf, capped at 192 sprites for dense canopies.
			leaves.sort((a, b) => a.distance - b.distance || a.key.localeCompare(b.key));
			const active = new Set(leaves.slice(0, 48).map(leaf => leaf.key));
			for (const [key, particles] of cherryEmitters) {
				if (active.has(key)) continue;
				for (const particle of particles) {
					cherryParticleGroup.remove(particle.sprite);
					particle.sprite.material.dispose();
				}
				cherryEmitters.delete(key);
			}
			for (const { key, block } of leaves.slice(0, 48)) {
				if (!cherryEmitters.has(key)) {
					cherryParticleMaterials ??= Array.from({ length: 12 }, (_, index) => new THREE.SpriteMaterial({
						map: assetTexture(`particle:cherry_${index}`), transparent: true, alphaTest: .1, depthWrite: false
					}));
					// PTRs do not record cosmetic random ticks. Seed the decoration by
					// block coordinates so terrain rebuilds and exported frames stay stable.
					let seed = 2166136261;
					for (const character of key) seed = Math.imul(seed ^ character.charCodeAt(0), 16777619);
					const random = () => { seed = Math.imul(seed, 1664525) + 1013904223 | 0; return (seed >>> 0) / 4294967296; };
					const phase = random() * 300;
					const particles = Array.from({ length: 4 }, (_, slot) => {
						const sprite = new THREE.Sprite(cherryParticleMaterials[Math.floor(random() * 12)].clone());
						sprite.scale.setScalar(random() < .5 ? .1 : .15);
						const angle = random() * Math.PI / 3;
						const particle = { sprite, x: block[2] + (random() - .5) * block[5],
							y: block[3] - block[6] / 2 - .05, z: block[4] + (random() - .5) * block[7],
							windX: Math.cos(angle), windZ: Math.sin(angle), phase: phase + slot * 75,
							spin: (random() < .5 ? -1 : 1) * Math.PI / 6,
							spinAcceleration: (random() < .5 ? -1 : 1) * Math.PI / 36 };
						cherryParticleGroup.add(sprite);
						return particle;
					});
					cherryEmitters.set(key, particles);
				}
				// Stop petals at the recorded terrain, including newly observed blocks.
				for (const particle of cherryEmitters.get(key)) particle.lifetime = cherryParticleLifetime(particle);
			}
		}

		function updateCherryParticles(seconds) {
			const time = reducedMotion ? 0 : seconds * 20;
			for (const particles of cherryEmitters.values()) for (const particle of particles) {
				const age = (time + particle.phase) % 300;
				particle.sprite.visible = age < particle.lifetime;
				if (!particle.sprite.visible) continue;
				particle.sprite.position.set(...cherryParticlePosition(particle, age));
				particle.sprite.material.rotation = particle.spin * age / 20 + particle.spinAcceleration * age * (age + 1) / 800;
				particle.sprite.material.opacity = Math.min(1, age / 8, (particle.lifetime - age) / 10);
			}
		}

		function addBrewingStand(group, block, offset) {
			const [, , x, y, z, , height, , properties = ''] = block;
			const stand = new THREE.Group();
			stand.position.set(x + offset[0], y - height / 2 + offset[1], z + offset[2]);
			group.add(stand);
			const postMaterial = faceMaterial(assetTexture('brewing_stand'));
			const baseMaterial = faceMaterial(assetTexture('brewing_stand_base'));
			const holderMaterial = faceMaterial(assetTexture('brewing_stand'), 1, 0xffffff, .1);
			holderMaterial.side = THREE.DoubleSide;
			const setFaceUv = (geometry, face, [u0, v0, u1, v1]) => {
				const uv = geometry.attributes.uv;
				uv.setXY(face * 4, u0 / 16, 1 - v0 / 16);
				uv.setXY(face * 4 + 1, u1 / 16, 1 - v0 / 16);
				uv.setXY(face * 4 + 2, u0 / 16, 1 - v1 / 16);
				uv.setXY(face * 4 + 3, u1 / 16, 1 - v1 / 16);
			};
			const addBox = (from, to, sideUv, endUv, topUv, material) => {
				const geometry = new THREE.BoxGeometry(...to.map((value, axis) => (value - from[axis]) / 16));
				// Three.js box faces: east, west, up, down, south, north.
				[endUv, endUv, topUv, topUv, sideUv, sideUv].forEach((uv, face) => setFaceUv(geometry, face, uv));
				const mesh = new THREE.Mesh(geometry, material);
				mesh.position.set((from[0] + to[0]) / 32 - .5, (from[1] + to[1]) / 32, (from[2] + to[2]) / 32 - .5);
				mesh.castShadow = mesh.receiveShadow = true;
				stand.add(mesh);
			};
			// Vanilla models/block/brewing_stand.json: the visual base is three
			// separate feet, while the PTR stores a single wide collision plate.
			addBox([7, 0, 7], [9, 14, 9], [7, 2, 9, 16], [7, 2, 9, 16], [7, 7, 9, 9], postMaterial);
			for (const [x0, z0] of [[9, 5], [1, 1], [1, 9]]) {
				addBox([x0, 0, z0], [x0 + 6, 2, z0 + 6],
					[x0, 14, x0 + 6, 16], [z0, 14, z0 + 6, 16], [x0, z0, x0 + 6, z0 + 6], baseMaterial);
			}
			const namedProperties = new Set(properties.toLowerCase().split(',').map(value => value.trim()));
			for (let slot = 0; slot < 3; slot++) {
				// Old reports may contain only an opaque variant hash; in that
				// case show empty holders instead of guessing bottle contents.
				const bottle = namedProperties.has(`has_bottle_${slot}=true`);
				const geometry = new THREE.PlaneGeometry(.5, 1);
				const u0 = slot === 0 ? 8 : bottle ? 0 : 16;
				const u1 = slot === 0 ? bottle ? 0 : 16 : 8;
				setFaceUv(geometry, 0, [u0, 0, u1, 16]);
				const holder = new THREE.Mesh(geometry, holderMaterial);
				holder.position.set(slot === 0 ? .25 : -4.41 / 16, .5, 0);
				holder.castShadow = holder.receiveShadow = true;
				const pivot = new THREE.Group();
				pivot.rotation.y = slot === 0 ? 0 : slot === 1 ? -Math.PI / 4 : Math.PI / 4;
				pivot.add(holder);
				stand.add(pivot);
			}
		}

		function skinPart(parent, size, center, innerUv, outerUv, pivot = center) {
			const part = new THREE.Group();
			part.rotation.order = 'ZYX'; // Minecraft ModelPart applies Z, then Y, then X.
			part.position.set(...pivot);
			const localCenter = center.map((value, axis) => value - pivot[axis]);
			parent.add(part);
			const innerGeometry = new THREE.BoxGeometry(...size);
			setSkinUVs(innerGeometry, ...innerUv, ...size);
			const innerMaterial = new THREE.MeshStandardMaterial({
				map: textures.skin,
				roughness: .95,
				metalness: 0
			});
			const inner = new THREE.Mesh(innerGeometry, innerMaterial);
			inner.position.set(...localCenter);
			inner.castShadow = true;
			part.add(inner);
		
			const outerGeometry = new THREE.BoxGeometry(
				size[0] + .5, size[1] + .5, size[2] + .5
			);
			setSkinUVs(outerGeometry, ...outerUv, ...size);
			const outerMaterial = new THREE.MeshStandardMaterial({
				map: textures.skin,
				transparent: true,
				alphaTest: .01,
				side: THREE.DoubleSide,
				roughness: .95,
				metalness: 0
			});
			const outer = new THREE.Mesh(outerGeometry, outerMaterial);
			outer.position.set(...localCenter);
			outer.castShadow = true;
			part.add(outer);
			return part;
		}
		
		function createElytra(parent) {
			const material = new THREE.MeshStandardMaterial({
				map: assetTexture(equipmentAssets ? 'entity:equipment/wings/elytra' : 'entity:elytra'),
				transparent: true,
				alphaTest: .01,
				side: THREE.DoubleSide,
				roughness: .9,
				metalness: 0
			});
			const wing = (pivotX, centerX, mirrored) => {
				const pivot = new THREE.Group();
				pivot.rotation.order = 'ZYX';
				// ElytraModel + WingsLayer, converted to Y-up with the skin front
				// at +Z: vanilla's Y/Z coordinates and Y/Z rotations change sign.
				pivot.position.set(pivotX, 24, -2);
				const geometry = new THREE.BoxGeometry(12, 22, 4);
				setSkinUVs(geometry, 22, 0, 10, 20, 2, 64, 32);
				const mesh = new THREE.Mesh(geometry, material);
				mesh.position.set(centerX, -10, -1);
				if (mirrored) mesh.scale.x = -1;
				mesh.castShadow = true;
				pivot.add(mesh);
				parent.add(pivot);
				return pivot;
			};
			return {
				left: wing(5, -5, false),
				right: wing(-5, 5, true)
			};
		}
		
		function addSkinnedPlayer(position, width, height, yaw, fitColor) {
			const model = new THREE.Group();
			const rig = new THREE.Group();
			const bodyRoot = new THREE.Group();
			rig.position.y = 16;
			bodyRoot.position.y = -16;
			rig.add(bodyRoot);
			model.add(rig);
			const slimArm = 3;
			// Slim shoulders moved up half a pixel in 1.21.2's PlayerModel.
			const shoulderY = CLIENT_PROTOCOL >= 768 ? 22 : 21.5;
			const parts = {
				head: skinPart(bodyRoot, [8, 8, 8], [0, 28, 0], [0, 0], [32, 0], [0, 24, 0]),
				body: skinPart(bodyRoot, [8, 12, 4], [0, 18, 0], [16, 16], [16, 32], [0, 24, 0]),
				rightArm: skinPart(bodyRoot, [slimArm, 12, 4], [-5.5, shoulderY - 4, 0], [40, 16], [40, 32], [-5, shoulderY, 0]),
				leftArm: skinPart(bodyRoot, [slimArm, 12, 4], [5.5, shoulderY - 4, 0], [32, 48], [48, 48], [5, shoulderY, 0]),
				rightLeg: skinPart(bodyRoot, [4, 12, 4], [-2, 6, 0], [0, 16], [0, 32], [-2, 12, 0]),
				leftLeg: skinPart(bodyRoot, [4, 12, 4], [2, 6, 0], [16, 48], [0, 48], [2, 12, 0])
			};
			const elytra = hasElytraInTrace ? createElytra(bodyRoot) : null;
			model.scale.setScalar(1.8 / 32);
			model.position.set(...position);
			model.rotation.y = THREE.MathUtils.degToRad(-yaw);
			playerGroup.add(model);
			const hitbox = addHitbox(position, width, height, fitColor, .95);
			return { model, rig, parts, elytra, hitbox, shoulderY };
		}
		
		function createMotionArrow(color) {
			const arrow = new THREE.ArrowHelper(
				new THREE.Vector3(1, 0, 0),
				new THREE.Vector3(),
				.001,
				color,
				.001,
				.001
			);
			arrow.visible = false;
			playerGroup.add(arrow);
			return arrow;
		}
		
		function updateMotionArrow(arrow, origin, motion, progress) {
			const vector = new THREE.Vector3(...motion).multiplyScalar(progress);
			const length = vector.length();
			arrow.visible = length >= 0.0000001;
			if (!arrow.visible) return;
			arrow.position.set(origin[0], origin[1] + .08, origin[2]);
			arrow.setDirection(vector.normalize());
			arrow.setLength(
				length,
				Math.min(.14, length * .32),
				Math.min(.08, length * .2)
			);
		}
		
		function moved(origin, motion, progress) {
			return origin.map((value, axis) => value + motion[axis] * progress);
		}
		
		function setHitboxPosition(hitbox, position, height) {
			hitbox.position.set(position[0], position[1] + height / 2, position[2]);
		}
		
		function interpolateYaw(from, to, progress) {
			const delta = ((to - from + 540) % 360) - 180;
			return from + delta * progress;
		}
		
		const followTarget = new THREE.Vector3();
		const cameraShift = new THREE.Vector3();
		function followPlayer(position, height) {
			followTarget.set(position[0], position[1] + height * .5, position[2]);
			cameraShift.copy(followTarget).sub(controls.target);
			camera.position.add(cameraShift);
			controls.target.copy(followTarget);
		}
		
		function renderPossibilities(tick, origin, width, height) {
			clearGroup(possibilitiesGroup);
			// Include every diagnostic layer and retention outcome. Older reports
			// without a candidate tree still have their first-tick branch endpoints.
			const candidates = tick.multiTick?.candidates?.length
				? tick.multiTick.candidates : (tick.branches ?? []);
			const byId = new Map(candidates.map(candidate => [candidate.id, candidate]));
			const endpoints = new Map();
			const segments = new Map();
			for (const candidate of candidates) {
				const end = [candidate.x, candidate.y, candidate.z];
				const parent = candidate.parent >= 0 ? byId.get(candidate.parent) : null;
				const start = parent ? [parent.x, parent.y, parent.z] : [0, 0, 0];
				// Coincident branches share geometry, but no candidate is filtered out.
				endpoints.set(end.join(','), end);
				segments.set([...start, ...end].join(','), [start, end]);
			}
			possibilitiesGroup.userData = {
				candidateCount: candidates.length, endpointCount: endpoints.size, segmentCount: segments.size
			};
			if (!endpoints.size) return;
			const color = darkTheme ? 0x93c8ff : 0x3276b8;
			const density = Math.sqrt(endpoints.size);
			const geometry = new THREE.BoxGeometry(width, height, width);
			const material = new THREE.MeshBasicMaterial({
				color, transparent: true, opacity: .08 / density, depthWrite: false, depthTest: false
			});
			const volumes = new THREE.InstancedMesh(geometry, material, endpoints.size);
			volumes.renderOrder = 10;
			const matrix = new THREE.Matrix4();
			const edgeGeometry = new THREE.EdgesGeometry(geometry);
			const localEdges = edgeGeometry.attributes.position.array;
			const edges = [];
			const points = [];
			let index = 0;
			for (const end of endpoints.values()) {
				const x = origin[0] + end[0], y = origin[1] + end[1], z = origin[2] + end[2];
				volumes.setMatrixAt(index++, matrix.makeTranslation(x, y + height / 2, z));
				points.push(x, y + .035, z);
				for (let edge = 0; edge < localEdges.length; edge += 3) {
					edges.push(x + localEdges[edge], y + height / 2 + localEdges[edge + 1], z + localEdges[edge + 2]);
				}
			}
			edgeGeometry.dispose();
			volumes.instanceMatrix.needsUpdate = true;
			const line = (positions, opacity) => {
				const buffer = new THREE.BufferGeometry();
				buffer.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
				const lines = new THREE.LineSegments(buffer, new THREE.LineBasicMaterial({
					color, transparent: true, opacity, depthWrite: false, depthTest: false
				}));
				lines.renderOrder = 11;
				possibilitiesGroup.add(lines);
			};
			possibilitiesGroup.add(volumes);
			line(edges, .38 / density);
			const paths = [];
			for (const segment of segments.values()) {
				for (const point of segment) paths.push(origin[0] + point[0], origin[1] + point[1] + .035, origin[2] + point[2]);
			}
			line(paths, .65 / Math.sqrt(segments.size));
			const pointGeometry = new THREE.BufferGeometry();
			pointGeometry.setAttribute('position', new THREE.Float32BufferAttribute(points, 3));
			const markers = new THREE.Points(pointGeometry, new THREE.PointsMaterial({
				color, size: .045, transparent: true, opacity: .55, depthWrite: false, depthTest: false
			}));
			markers.renderOrder = 12;
			possibilitiesGroup.add(markers);
		}

		function addMultiTickPath(tick, origin, width, height) {
			const path = tick.multiTick?.path ?? [];
			if (path.length < 2) return;
			const color = darkTheme ? 0xb3a0ff : 0x6445b8;
			const points = [new THREE.Vector3(...origin)];
			for (const step of path) {
				points.push(new THREE.Vector3(
					origin[0] + step.x,
					origin[1] + step.y,
					origin[2] + step.z
				));
			}
			const geometry = new THREE.BufferGeometry().setFromPoints(points);
			const material = new THREE.LineBasicMaterial({ color, transparent: true, opacity: .9 });
			pathGroup.add(new THREE.Line(geometry, material));
			for (let index = 1; index < points.length - 1; index++) {
				const point = points[index];
				addHitbox([point.x, point.y, point.z], width, height, color, .34, pathGroup);
				const marker = new THREE.Mesh(
					new THREE.SphereGeometry(.055, 12, 8),
					new THREE.MeshBasicMaterial({ color })
				);
				marker.position.copy(point);
				marker.position.y += .08;
				pathGroup.add(marker);
			}
		}
		
		function elytraTarget(tick, motion) {
			let x = .2617994;
			let y = 0;
			let z = -.2617994;
			let verticalOffset = 0;
			if (tick.environment.fallFlying) {
				let openness = 1;
				const length = Math.hypot(...motion);
				if (motion[1] < 0 && length > 0.0000001) {
					openness = 1 - Math.pow(-motion[1] / length, 1.5);
				}
				x = openness * .34906584 + (1 - openness) * .2617994;
				z = openness * (-Math.PI / 2) + (1 - openness) * -.2617994;
			} else if (tick.environment.pose === 'CROUCHING') {
				x = .6981317;
				y = .08726646;
				z = -Math.PI / 4;
				verticalOffset = 3;
			}
			return { x, y, z, verticalOffset };
		}
		
		function animatePlayerModel(player, tick, nextTick, motion, progress, position) {
			const nextMotion = [nextTick.actualX, nextTick.actualY, nextTick.actualZ];
			const walk = walkingStates[state.tickIndex];
			const partialTick = reducedMotion ? progress : Math.max(0, Math.min(1, state.progress));
			const walkPosition = THREE.MathUtils.lerp(walk.previousPosition, walk.position, partialTick);
			const walkSpeed = tick.environment.pose === 'SLEEPING'
				? 0 : THREE.MathUtils.lerp(walk.previousSpeed, walk.speed, partialTick);
			const speedSquared = tick.actualX ** 2 + tick.actualY ** 2 + tick.actualZ ** 2;
			const flightDamping = tick.environment.fallFlying ? Math.max(1, (speedSquared / .2) ** 3) : 1;
			const walkAmount = walkSpeed / flightDamping;
			const walkPhase = walkPosition * .6662;
			const age = tick.tick - 1 + partialTick;
			const idleX = Math.sin(age * .067) * .05;
			const idleZ = Math.cos(age * .09) * .05 + .05;
			// ModelBiped/HumanoidModel: opposite arm/leg pairs, with a smaller
			// arm amplitude. Y-up and the +Z skin front reverse vanilla's Z roll.
			player.parts.rightLeg.rotation.set(Math.cos(walkPhase) * 1.4 * walkAmount, 0, 0);
			player.parts.leftLeg.rotation.set(Math.cos(walkPhase + Math.PI) * 1.4 * walkAmount, 0, 0);
			player.parts.rightArm.rotation.set(Math.cos(walkPhase + Math.PI) * walkAmount + idleX, 0, -idleZ);
			player.parts.leftArm.rotation.set(Math.cos(walkPhase) * walkAmount - idleX, 0, idleZ);
		
			const nextCrouching = nextTick.environment.pose === 'CROUCHING' ? 1 : 0;
			const crouching = THREE.MathUtils.lerp(
				tick.environment.pose === 'CROUCHING' ? 1 : 0,
				nextCrouching,
				progress
			);
			player.parts.body.rotation.x = crouching * .5;
			player.parts.body.position.y = 24 - crouching * 3.2;
			player.parts.head.position.y = 24 - crouching * 4.2;
			player.parts.head.rotation.x = THREE.MathUtils.degToRad(
				THREE.MathUtils.lerp(tick.environment.pitch, nextTick.environment.pitch, progress)
			);
			player.parts.rightArm.rotation.x += crouching * .4;
			player.parts.leftArm.rotation.x += crouching * .4;
			player.parts.rightArm.position.y = player.parts.leftArm.position.y = player.shoulderY - crouching * 3.2;
			player.parts.rightLeg.position.z = player.parts.leftLeg.position.z = -crouching * 4;
			const swimming = THREE.MathUtils.lerp(
				tick.environment.pose === 'SWIMMING' ? 1 : 0,
				nextTick.environment.pose === 'SWIMMING' ? 1 : 0, progress
			);
			if (swimming > 0) {
				// HumanoidModel's 26-unit swimming stroke and alternating kicks.
				const cycle = walkPosition % 26;
				let armX = 0;
				let sweep = 0;
				if (cycle < 14) sweep = 1.8707964 * (cycle * cycle - 65 * cycle) / (14 * 14 - 65 * 14);
				else if (cycle < 22) {
					const phase = (cycle - 14) / 8;
					armX = Math.PI / 2 * phase;
					sweep = 1.8707964 * (1 - phase);
				} else armX = Math.PI / 2 * (1 - (cycle - 22) / 4);
				const blendAngle = (from, to) => from + Math.atan2(Math.sin(to - from), Math.cos(to - from)) * swimming;
				for (const [arm, side] of [[player.parts.rightArm, 1], [player.parts.leftArm, -1]]) {
					arm.rotation.x = blendAngle(arm.rotation.x, armX);
					arm.rotation.y = blendAngle(arm.rotation.y, -Math.PI);
					arm.rotation.z = blendAngle(arm.rotation.z, -Math.PI + side * sweep);
				}
				player.parts.rightLeg.rotation.x = THREE.MathUtils.lerp(player.parts.rightLeg.rotation.x, .3 * Math.cos(walkPosition / 3), swimming);
				player.parts.leftLeg.rotation.x = THREE.MathUtils.lerp(player.parts.leftLeg.rotation.x, .3 * Math.cos(walkPosition / 3 + Math.PI), swimming);
			}
			const horizontal = sample => sample.environment.fallFlying || sample.environment.pose === 'SWIMMING';
		
			const currentFlightAngle = horizontal(tick)
				? Math.PI / 2 + THREE.MathUtils.degToRad(tick.environment.pitch)
				: 0;
			const nextFlightAngle = horizontal(nextTick)
				? Math.PI / 2 + THREE.MathUtils.degToRad(nextTick.environment.pitch)
				: 0;
			player.rig.rotation.x = THREE.MathUtils.lerp(
				currentFlightAngle,
				nextFlightAngle,
				progress
			);
			const flightBlend = THREE.MathUtils.lerp(
				horizontal(tick) ? 1 : 0,
				horizontal(nextTick) ? 1 : 0,
				progress
			);
			player.parts.head.rotation.x = THREE.MathUtils.lerp(player.parts.head.rotation.x, -Math.PI / 4, flightBlend);
			const modelCenter = 1.8 / 2;
			const flightCenterOffset = tick.environment.playerHeight / 2 - modelCenter;
			player.model.position.set(
				position[0],
				position[1] + flightCenterOffset * flightBlend,
				position[2]
			);
		
			if (!player.elytra) return;
			const visible = tick.environment.elytraEquipped || nextTick.environment.elytraEquipped;
			player.elytra.left.visible = visible;
			player.elytra.right.visible = visible;
			if (!visible) return;
			const currentWing = elytraTarget(tick, motion);
			const nextWing = elytraTarget(nextTick, nextMotion);
			const wingX = THREE.MathUtils.lerp(currentWing.x, nextWing.x, progress);
			const wingY = THREE.MathUtils.lerp(currentWing.y, nextWing.y, progress);
			const wingZ = THREE.MathUtils.lerp(currentWing.z, nextWing.z, progress);
			const wingOffset = THREE.MathUtils.lerp(
				currentWing.verticalOffset,
				nextWing.verticalOffset,
				progress
			);
			player.elytra.left.position.y = 24 - wingOffset;
			player.elytra.right.position.y = 24 - wingOffset;
			player.elytra.left.rotation.set(wingX, -wingY, -wingZ);
			player.elytra.right.rotation.set(wingX, wingY, wingZ);
		}
		
		function focusedPossibility(tick) {
			if (state.focusKind === 'candidate') {
				const candidate = tick.multiTick.candidates.find(candidate => candidate.id === state.candidateId);
				if (candidate) {
					return {
						motion: [candidate.x, candidate.y, candidate.z],
						loss: candidate.loss,
						label: `candidate tick ${candidate.depth + 1}`
					};
				}
			}
			const path = tick.multiTick.path;
			const requestedIndex = state.focusKind === 'layer'
				? state.multiLayerIndex
				: state.pathIndex;
			const pathIndex = Math.max(0, Math.min(path.length - 1, requestedIndex));
			const step = path[pathIndex];
			if (step) {
				return {
					motion: [step.x, step.y, step.z],
					loss: step.loss,
					label: `production tick ${step.depth + 1}`
				};
			}
			const fallback = tick.branches[state.branchIndex] ?? tick.branches[0];
			return fallback
				? { motion: [fallback.x, fallback.y, fallback.z], loss: fallback.loss, label: 'closest branch' }
				: { motion: [0, 0, 0], loss: tick.loss, label: 'possibility' };
		}
		
		function updateWorldInterpolation(tick) {
			if (!worldActors) return;
			const rawProgress = reducedMotion
				? (state.progress >= 1 ? 1 : 0)
				: Math.max(0, Math.min(1, state.progress));
			const progress = rawProgress * rawProgress * (3 - 2 * rawProgress);
			const origin = originOffset(state.tickIndex);
			const possibility = focusedPossibility(tick);
			const recordedMotion = [tick.actualX, tick.actualY, tick.actualZ];
			const predictedMotion = possibility.motion;
			const recordedPosition = moved(origin, recordedMotion, progress);
			const predictedPosition = moved(origin, predictedMotion, progress);
			setHitboxPosition(worldActors.startHitbox, origin, tick.environment.playerHeight);
			setHitboxPosition(worldActors.recordedHitbox, recordedPosition, tick.environment.playerHeight);
			setHitboxPosition(worldActors.player.hitbox, predictedPosition, tick.environment.playerHeight);
			const nextTick = trace[Math.min(trace.length - 1, state.tickIndex + 1)];
			animatePlayerModel(
				worldActors.player,
				tick,
				nextTick,
				predictedMotion,
				progress,
				predictedPosition
			);
			const yaw = interpolateYaw(
				tick.environment.yaw,
				nextTick.environment.yaw,
				progress
			);
			worldActors.player.model.rotation.y = THREE.MathUtils.degToRad(-yaw);
			updateMotionArrow(worldActors.recordedArrow, origin, recordedMotion, progress);
			updateMotionArrow(worldActors.predictedArrow, origin, predictedMotion, progress);
			followPlayer(recordedPosition, tick.environment.playerHeight);
		}
		
		function renderWorld(tick) {
			const origin = originOffset(state.tickIndex);
			renderTerrain(tick, origin);
			const possibility = focusedPossibility(tick);
			const width = tick.environment.playerWidth;
			const height = tick.environment.playerHeight;
			if (renderedPathTick !== state.tickIndex) {
				clearGroup(pathGroup);
				addMultiTickPath(tick, origin, width, height);
				renderPossibilities(tick, origin, width, height);
				renderedPathTick = state.tickIndex;
			}
			const fit = fitOf(possibility.loss);
			const branchLegend = document.getElementById('branch-legend');
			branchLegend.className = `legend-item ${fit.key}`;
			document.getElementById('branch-legend-label').textContent = `${possibility.label} · ${fit.label.toLowerCase()}`;
			if (!worldActors) {
				worldActors = {
					startHitbox: addHitbox(origin, width, height, darkTheme ? 0x9da7b2 : 0x657180, .42),
					recordedHitbox: addHitbox(origin, width, height, 0x35b779, .72),
					player: addSkinnedPlayer(origin, width, height, tick.environment.yaw, fit.color),
					recordedArrow: createMotionArrow(0x35b779),
					predictedArrow: createMotionArrow(fit.color)
				};
			}
			for (const hitbox of [
				worldActors.startHitbox,
				worldActors.recordedHitbox,
				worldActors.player.hitbox
			]) hitbox.scale.set(width, height, width);
			worldActors.player.model.scale.setScalar(1.8 / 32);
			worldActors.player.hitbox.material.color.setHex(fit.color);
			worldActors.predictedArrow.setColor(new THREE.Color(fit.color));
			updateWorldInterpolation(tick);
			document.getElementById('world-origin').textContent = `Jpx3 · ${tick.environment.pose.toLowerCase().replace('_', ' ')} · yaw ${tick.environment.yaw.toFixed(1)}° · start ${tick.environment.origin}`;
		}
		
		function setPlaying(playing) {
			state.playing = playing;
			play.textContent = playing ? '❚❚ Pause' : '▶ Play';
			play.setAttribute('aria-pressed', String(playing));
			if (playing) {
				playbackStartedAt = performance.now() - state.progress * TICK_DURATION_MS;
			}
		}
		
		function updatePlayback(now) {
			if (!state.playing || trace.length === 0) return;
			let tickProgress = (now - playbackStartedAt) / TICK_DURATION_MS;
			if (tickProgress >= 1) {
				const remainingTicks = trace.length - 1 - state.tickIndex;
				const advance = Math.min(Math.floor(tickProgress), remainingTicks);
				if (advance > 0) {
					state.tickIndex += advance;
					playbackStartedAt += advance * TICK_DURATION_MS;
					tickProgress -= advance;
					state.stageIndex = 0;
					resetPossibilityFocus(trace[state.tickIndex]);
					state.progress = Math.min(1, tickProgress);
					render(false);
				}
				if (state.tickIndex === trace.length - 1 && tickProgress >= 1) {
					state.progress = 1;
					setPlaying(false);
					render();
					return;
				}
			}
			state.progress = Math.max(0, Math.min(1, tickProgress));
			updateWorldInterpolation(trace[state.tickIndex]);
		}
		
		function stopForManualNavigation() {
			setPlaying(false);
			state.progress = 1;
		}
		
		function render(includeDetails = true) {
			const tick = trace[state.tickIndex];
			if (!tick) return;
			state.stageIndex = Math.min(state.stageIndex, tick.stages.length - 1);
			state.branchIndex = Math.min(state.branchIndex, tick.branches.length - 1);
			state.pathIndex = Math.min(state.pathIndex, Math.max(0, tick.multiTick.path.length - 1));
			tickInput.value = String(state.tickIndex);
			previous.disabled = state.tickIndex === 0;
			next.disabled = state.tickIndex === trace.length - 1;
			document.getElementById('tick-label').textContent = `Tick ${tick.tick} · ${state.tickIndex + 1}/${trace.length}`;
			if (includeDetails) {
				renderPicker(tick);
				renderStage(tick);
				renderBranches(tick);
				renderSelection(tick);
				renderGraph(tick);
				renderMultiTick(tick);
			}
			renderWorld(tick);
		}
		
		function defaultBranchIndex(tick) {
			const productionWinner = tick.branches.findIndex(branch => branch.selected);
			return productionWinner >= 0 ? productionWinner : 0;
		}
		
		function defaultMultiLayerIndex(tick) {
			return Math.min(tick.multiTick.selectedDepth, tick.multiTick.layers.length - 1);
		}
		
		function resetPossibilityFocus(tick) {
			state.branchIndex = defaultBranchIndex(tick);
			state.multiLayerIndex = defaultMultiLayerIndex(tick);
			state.pathIndex = Math.max(0, tick.multiTick.path.length - 1);
			const selectedCandidate = tick.multiTick.candidates.find(candidate => candidate.selected)
				?? tick.multiTick.candidates[0];
			state.candidateId = selectedCandidate?.id ?? -1;
			state.expandedCandidates = new Set(
				tick.multiTick.candidates
					.filter(candidate => candidate.production)
					.map(candidate => candidate.id)
			);
			state.focusKind = tick.multiTick.selectedPathFound ? 'path' : 'candidate';
		}
		
		function retentionLabel(candidate) {
			return {
				'expanded': 'implicit · expanded',
				'retained': 'implicit · retained',
				'merged': 'implicit · merged',
				'depth-limit': 'implicit · depth limit',
				'explicit-only': 'explicit only'
			}[candidate.retention] ?? candidate.retention;
		}
		
		function candidateGroups(tick) {
			const groups = new Map();
			for (const candidate of tick.multiTick.candidates) {
				if (!groups.has(candidate.parent)) groups.set(candidate.parent, []);
				groups.get(candidate.parent).push(candidate);
			}
			for (const candidates of groups.values()) {
				candidates.sort((left, right) => left.loss - right.loss || left.key.localeCompare(right.key));
			}
			return groups;
		}
		
		function selectCandidate(candidate) {
			stopForManualNavigation();
			state.focusKind = 'candidate';
			state.candidateId = candidate.id;
			if (candidate.retention === 'expanded') {
				state.expandedCandidates.add(candidate.id);
			}
			render();
		}
		
		function candidateButton(candidate, compact = false) {
			const fit = fitOf(candidate.loss);
			const button = document.createElement('button');
			button.type = 'button';
			button.className = `tree-item branch-tile fit-${fit.key}${candidate.selected ? ' production' : ''}`;
			const icon = document.createElement('span');
			icon.className = 'tree-icon';
			icon.textContent = candidate.selected ? '★' : candidate.production ? '●' : candidate.implicitEligible ? '≈' : '◇';
			const name = document.createElement('span');
			name.className = 'tree-primary';
			name.textContent = `${compact ? '' : `Tick ${candidate.depth + 1} · `}${candidate.config}`;
			const meta = document.createElement('span');
			meta.className = `tree-secondary ${candidate.implicitEligible ? 'multi' : fit.key}`;
			meta.textContent = candidate.implicitEligible ? 'implicit' : fit.label;
			button.append(icon, name, meta);
			button.setAttribute('aria-pressed', String(state.focusKind === 'candidate' && candidate.id === state.candidateId));
			button.setAttribute(
				'aria-label',
				`Tick ${candidate.depth + 1} candidate: ${candidate.config}, ${retentionLabel(candidate)}, loss ${formatDecimal(candidate.loss)}${candidate.selected ? ', production selection' : ''}`
			);
			button.addEventListener('click', () => selectCandidate(candidate));
			return button;
		}
		
		function renderCandidateDetail(tick, groups) {
			const candidates = tick.multiTick.candidates;
			let candidate = candidates.find(candidate => candidate.id === state.candidateId);
			if (!candidate) {
				candidate = candidates.find(candidate => candidate.selected) ?? candidates[0];
				state.candidateId = candidate?.id ?? -1;
			}
			const children = candidate ? groups.get(candidate.id) ?? [] : [];
			const childList = document.getElementById('candidate-children');
			childList.replaceChildren();
			document.getElementById('candidate-child-summary').textContent = `${children.length.toLocaleString()} direct`;
			if (!candidate) {
				const empty = document.createElement('p');
				empty.className = 'candidate-empty';
				empty.textContent = 'No diagnostic candidates were generated.';
				childList.append(empty);
				return;
			}
		
			const fit = fitOf(candidate.loss);
			const title = document.getElementById('branch-title');
			title.className = `fit-line ${fit.key}`;
			title.textContent = `${fit.label} · loss ${formatDecimal(candidate.loss, 7)}${candidate.selected ? ' · production selection' : ''}`;
			document.getElementById('branch-config').textContent = `${candidate.key} · search tick ${candidate.depth + 1} · ${retentionLabel(candidate)}`;
			document.getElementById('branch-motion').textContent = `motion ${candidate.motion} → accumulated position (${candidate.x.toFixed(6)}, ${candidate.y.toFixed(6)}, ${candidate.z.toFixed(6)})`;
			const comparison = candidate.implicitEligible ? '<' : '≥';
			document.getElementById('branch-finish').textContent = `${candidate.implicitEligible ? 'Implicit-eligible' : 'Explicit-only'}: omission ${formatDecimal(candidate.omissionDistance, 5)} ${comparison} ${formatDecimal(tick.multiTick.omissionLimit, 5)}${candidate.finishable ? '' : ' · cannot finish an explicit tick'}`;
		
			if (children.length) {
				children.forEach(child => childList.append(candidateButton(child, true)));
				return;
			}
			const empty = document.createElement('p');
			empty.className = 'candidate-empty';
			empty.textContent = {
				'merged': 'This implicit candidate reached a state equivalent to another candidate, so that equivalent node owns the continuation.',
				'depth-limit': 'This candidate is still implicit-eligible; the diagnostic stops at the client’s three-tick search horizon.',
				'explicit-only': 'This state is not omission-safe, so the search cannot continue implicitly from it.'
			}[candidate.retention] ?? 'No distinct child alternatives were generated.';
			childList.append(empty);
		}
		
		function renderBranches(tick) {
			const grid = document.getElementById('branch-grid');
			const groups = candidateGroups(tick);
			grid.replaceChildren();
			const appendChildren = (parentId, depth) => {
				for (const candidate of groups.get(parentId) ?? []) {
					const children = groups.get(candidate.id) ?? [];
					const row = document.createElement('div');
					row.className = 'candidate-row';
					row.style.setProperty('--depth', depth);
					if (children.length) {
						const toggle = document.createElement('button');
						toggle.type = 'button';
						toggle.className = 'candidate-toggle';
						const expanded = state.expandedCandidates.has(candidate.id);
						toggle.textContent = expanded ? '▾' : '▸';
						toggle.setAttribute('aria-label', `${expanded ? 'Collapse' : 'Expand'} alternatives from ${candidate.config}`);
						toggle.setAttribute('aria-expanded', String(expanded));
						toggle.addEventListener('click', () => {
							if (expanded) state.expandedCandidates.delete(candidate.id);
							else state.expandedCandidates.add(candidate.id);
							render();
						});
						row.append(toggle);
					} else {
						const placeholder = document.createElement('span');
						placeholder.className = 'candidate-toggle-placeholder';
						row.append(placeholder);
					}
					row.append(candidateButton(candidate));
					grid.append(row);
					if (children.length && state.expandedCandidates.has(candidate.id)) {
						appendChildren(candidate.id, depth + 1);
					}
				}
			};
			appendChildren(-1, 0);
		
			const roots = groups.get(-1) ?? [];
			document.getElementById('fit-summary').textContent = `${roots.length.toLocaleString()} roots · ${tick.multiTick.candidates.length.toLocaleString()} total`;
			renderCandidateDetail(tick, groups);
		}
		
		function renderSelection(tick) {
			const pathPage = document.getElementById('path-page');
			const layerPage = document.getElementById('layer-page');
			const branchPage = document.getElementById('branch-page');
			const stageDrilldown = document.getElementById('stage-drilldown');
			const candidate = tick.multiTick.candidates.find(candidate => candidate.id === state.candidateId);
			pathPage.hidden = state.focusKind !== 'path';
			layerPage.hidden = state.focusKind !== 'layer';
			branchPage.hidden = state.focusKind !== 'candidate';
			stageDrilldown.hidden = state.focusKind !== 'candidate' || candidate?.depth !== 0;
		
			const kicker = document.getElementById('selection-kicker');
			const title = document.getElementById('selection-title');
			const summary = document.getElementById('selection-summary');
			const status = document.getElementById('selection-status');
			if (state.focusKind === 'candidate' && candidate) {
				const fit = fitOf(candidate.loss);
				kicker.textContent = candidate.implicitEligible ? 'Implicit candidate' : 'Explicit candidate';
				title.textContent = `Search tick ${candidate.depth + 1} · ${candidate.config}`;
				summary.textContent = `${candidate.key} · motion ${candidate.motion} · accumulated position (${candidate.x.toFixed(5)}, ${candidate.y.toFixed(5)}, ${candidate.z.toFixed(5)})`;
				status.className = `page-status ${candidate.implicitEligible ? 'multi' : fit.key}`;
				status.textContent = candidate.selected
					? `${fit.label} · production selection`
					: retentionLabel(candidate);
				return;
			}
			if (state.focusKind === 'layer') {
				const layer = tick.multiTick.layers[state.multiLayerIndex];
				const finalLayer = state.multiLayerIndex === tick.multiTick.layers.length - 1;
				const diagnosticContinuation = layer.depth > tick.multiTick.selectedDepth;
				kicker.textContent = 'Search layer';
				title.textContent = `Search tick ${layer.depth + 1}`;
				summary.textContent = `${layer.simulations.toLocaleString()} candidates from ${layer.parents.toLocaleString()} parent${layer.parents === 1 ? '' : 's'} · ${layer.selected ? 'contains the production selection' : finalLayer ? `${layer.finishable.toLocaleString()} explicit at the horizon` : `${layer.retained.toLocaleString()} continued implicitly`}`;
				const layerFit = fitOf(layer.selectedLoss >= 0 ? layer.selectedLoss : layer.bestLoss);
				status.className = `page-status ${layer.selected ? layerFit.key : ''}`;
				status.textContent = layer.selected
					? `Production selected · ${layerFit.label}`
					: diagnosticContinuation ? 'Diagnostic continuation' : finalLayer ? 'Three-tick horizon' : 'Implicit search';
				return;
			}
			const step = tick.multiTick.path[state.pathIndex] ?? tick.multiTick.path.at(-1);
			const fit = fitOf(step?.loss ?? tick.loss);
			kicker.textContent = 'Production route';
			title.textContent = step ? `Search tick ${step.depth + 1} · ${step.config}` : 'Selected production possibility';
			summary.textContent = step
				? `${step.key} · motion ${step.motion} · accumulated position (${step.x.toFixed(5)}, ${step.y.toFixed(5)}, ${step.z.toFixed(5)})`
				: 'The selected production route could not be reconstructed.';
			status.className = `page-status ${fit.key}`;
			status.textContent = state.pathIndex < tick.multiTick.path.length - 1
				? 'Implicit continuation'
				: `${fit.label} · selected`;
		}
		
		function renderPicker(tick) {
			const picker = document.getElementById('stage-picker');
			picker.replaceChildren();
			tick.stages.forEach((stage, index) => {
				const button = document.createElement('button');
				button.type = 'button';
				button.textContent = stage.name;
				button.setAttribute('aria-pressed', String(index === state.stageIndex));
				button.addEventListener('click', () => { state.stageIndex = index; render(); });
				picker.append(button);
			});
		}
		
		function renderGraph(tick) {
			const width = Math.max(720, svg.clientWidth || 720);
			const height = 260;
			const margin = 52;
			const centerY = 140;
			svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
			svg.replaceChildren();
			const stages = tick.stages;
			const step = (width - margin * 2) / Math.max(1, stages.length - 1);
			const radius = count => 11 + Math.min(25, Math.log2(Math.max(1, count)) * 3.5);
		
			for (let index = 1; index < stages.length; index++) {
				const previousStage = stages[index - 1];
				const stage = stages[index];
				const x1 = margin + (index - 1) * step + radius(previousStage.outputs);
				const x2 = margin + index * step - radius(stage.outputs);
				const strands = Math.max(1, Math.min(7, Math.ceil(Math.log2(Math.max(1, stage.outputs)))));
				for (let strand = 0; strand < strands; strand++) {
					const spread = strands === 1 ? 0 : (strand / (strands - 1) - .5) * 58;
					const path = element('path', {
						d: `M ${x1} ${centerY} C ${(x1 + x2) / 2} ${centerY + spread}, ${(x1 + x2) / 2} ${centerY + spread}, ${x2} ${centerY}`,
						class: `flow-edge${tick.winnerInFirstLayer && stage.winner && strand === Math.floor(strands / 2) ? ' winner' : ''}`
					});
					svg.append(path);
				}
			}
		
			stages.forEach((stage, index) => {
				const x = margin + index * step;
				const r = radius(stage.outputs);
				const winner = tick.winnerInFirstLayer && Boolean(stage.winner);
				const circle = element('circle', {
					cx: x, cy: centerY, r,
					class: `flow-node${winner ? ' winner' : ''}${index === state.stageIndex ? ' selected' : ''}`,
					'aria-label': `${stage.name}: ${stage.outputs} configurations`
				});
				circle.addEventListener('click', () => { state.stageIndex = index; render(); });
				svg.append(circle);
		
				const representativeDots = Math.min(8, Math.ceil(Math.log2(Math.max(1, stage.outputs))));
				for (let dot = 0; dot < representativeDots; dot++) {
					const angle = (Math.PI * 2 * dot) / representativeDots - Math.PI / 2;
					svg.append(element('circle', {
						cx: x + Math.cos(angle) * (r + 8),
						cy: centerY + Math.sin(angle) * (r + 8),
						r: 2.1,
						class: 'flow-dot'
					}));
				}
		
				const name = element('text', { x, y: 55 + (index % 2) * 22, class: 'flow-name' });
				name.textContent = stage.name;
				svg.append(name);
				const count = element('text', { x, y: centerY + 4, class: 'flow-count' });
				count.textContent = stage.outputs.toLocaleString();
				svg.append(count);
			});
		}
		
		function renderMultiLayerDetail(multiTick, layer, isFinalLayer) {
			document.getElementById('multi-layer-title').textContent = `Tick ${layer.depth + 1}`;
			document.getElementById('multi-layer-status').textContent = layer.selected
				? 'production selected'
				: layer.depth > multiTick.selectedDepth
					? 'diagnostic continuation'
					: isFinalLayer ? 'three-tick horizon' : 'implicit continuation';
		
			const funnel = document.getElementById('multi-funnel');
			funnel.replaceChildren();
			const addFunnel = (label, value, total, tone = '') => {
				const row = document.createElement('div');
				row.className = 'multi-funnel-row';
				const name = document.createElement('span');
				name.textContent = label;
				const track = document.createElement('span');
				track.className = 'multi-funnel-track';
				const fill = document.createElement('span');
				fill.className = `multi-funnel-fill${tone ? ` ${tone}` : ''}`;
				fill.style.setProperty('--share', `${total > 0 ? Math.max(0, Math.min(100, value / total * 100)) : 0}%`);
				track.append(fill);
				const amount = document.createElement('span');
				amount.className = 'multi-funnel-value';
				amount.textContent = `${value.toLocaleString()} / ${total.toLocaleString()}`;
				row.append(name, track, amount);
				funnel.append(row);
			};
			if (isFinalLayer) {
				const accepted = layer.exactFinishable + layer.acceptedFinishable;
				addFunnel('Explicit', layer.finishable, layer.simulations);
				addFunnel('Accepted', accepted, layer.finishable, 'accepted');
				addFunnel('Exact', layer.exactFinishable, layer.finishable, 'retained');
			} else {
				addFunnel('Omission-safe', layer.rawImplicitEligible, layer.simulations);
				addFunnel('Unique', layer.implicitEligible, layer.rawImplicitEligible, 'unique');
				addFunnel('Retained', layer.retained, layer.implicitEligible, 'retained');
			}
		
			const loss = value => value < 0 ? '—' : formatDecimal(value, 4);
			const metrics = document.getElementById('multi-layer-metrics');
			metrics.replaceChildren();
			const rows = [
				['Parents / candidates each', `${layer.parents.toLocaleString()} / ${(layer.simulations / Math.max(1, layer.parents)).toFixed(1)}`],
				['Exact / accepted / mismatch', `${layer.exactFinishable.toLocaleString()} / ${layer.acceptedFinishable.toLocaleString()} / ${layer.mismatchFinishable.toLocaleString()}`],
				['Loss best / p50 / p90', `${loss(layer.bestLoss)} / ${loss(layer.medianLoss)} / ${loss(layer.p90Loss)}`],
			];
			if (!isFinalLayer) {
				rows.push(
					['Omission radius / nearest', `${formatDecimal(multiTick.omissionLimit, 4)} / ${loss(layer.closestOmissionDistance)}`],
					['Furthest retained', loss(layer.furthestRetainedDistance)]
				);
			}
			for (const [label, value] of rows) {
				const term = document.createElement('dt');
				term.textContent = label;
				const description = document.createElement('dd');
				description.textContent = value;
				metrics.append(term, description);
			}
		}
		
		function renderMultiTick(tick) {
			const multiTick = tick.multiTick;
			const layers = multiTick.layers;
			state.multiLayerIndex = Math.min(state.multiLayerIndex, layers.length - 1);
			const finalStep = multiTick.path.at(-1);
			const finalFit = fitOf(finalStep?.loss ?? tick.loss);
			document.getElementById('multi-summary').textContent = `${layers.length} tick${layers.length === 1 ? '' : 's'} explored`;
			document.getElementById('route-summary').textContent = `${finalFit.label} · ${multiTick.productionSimulations.toLocaleString()} sims`;
		
			const width = Math.max(720, multiSvg.clientWidth || 720);
			const height = 190;
			const margin = 105;
			const centerY = 82;
			const stepWidth = layers.length > 1
				? (width - margin * 2) / (layers.length - 1)
				: 0;
			const xAt = index => layers.length === 1 ? width / 2 : margin + index * stepWidth;
			const radius = layer => 31 + Math.min(16, Math.log2(Math.max(1, layer.simulations)) * 2.2);
			multiSvg.setAttribute('viewBox', `0 0 ${width} ${height}`);
			multiSvg.replaceChildren();
		
			for (let index = 1; index < layers.length; index++) {
				const previousLayer = layers[index - 1];
				const layer = layers[index];
				const x1 = xAt(index - 1) + radius(previousLayer);
				const x2 = xAt(index) - radius(layer);
				const strands = Math.max(1, Math.min(7, Math.ceil(Math.log2(previousLayer.retained + 1))));
				for (let strand = 0; strand < strands; strand++) {
					const spread = strands === 1 ? 0 : (strand / (strands - 1) - .5) * 58;
					multiSvg.append(element('path', {
						d: `M ${x1} ${centerY} C ${(x1 + x2) / 2} ${centerY + spread}, ${(x1 + x2) / 2} ${centerY + spread}, ${x2} ${centerY}`,
						class: `multi-edge${multiTick.selectedPathFound && index < multiTick.path.length && strand === Math.floor(strands / 2) ? ' selected' : ''}`
					}));
				}
				const edgeLabel = element('text', {
					x: (x1 + x2) / 2,
					y: 61,
					class: 'multi-caption'
				});
				const expansion = layer.simulations / Math.max(1, layer.parents);
				edgeLabel.textContent = `×${expansion.toFixed(1)}`;
				multiSvg.append(edgeLabel);
			}
		
			layers.forEach((layer, index) => {
				const x = xAt(index);
				const r = radius(layer);
				const circle = element('circle', {
					cx: x,
					cy: centerY,
					r,
					class: `multi-node${layer.selected ? ' selected' : ''}${index === state.multiLayerIndex ? ' active' : ''}`,
					'aria-label': `Tick ${layer.depth + 1}: ${layer.simulations} candidates${layer.selected ? ', selected' : ''}`
				});
				circle.addEventListener('click', () => {
					stopForManualNavigation();
					state.focusKind = 'layer';
					state.multiLayerIndex = index;
					render();
				});
				multiSvg.append(circle);
				const title = element('text', { x, y: 27, class: 'multi-title' });
				title.textContent = `Tick ${layer.depth + 1}`;
				multiSvg.append(title);
				const count = element('text', { x, y: centerY + 5, class: 'multi-count' });
				count.textContent = layer.simulations.toLocaleString();
				multiSvg.append(count);
				const countLabel = element('text', { x, y: centerY + 20, class: 'multi-caption' });
				countLabel.textContent = 'replayed candidates';
				multiSvg.append(countLabel);
				const outcome = element('text', { x, y: centerY + r + 24, class: 'multi-caption' });
				outcome.textContent = index < layers.length - 1
					? `${layer.retained.toLocaleString()} retained`
					: `${layer.finishable.toLocaleString()} explicit`;
				multiSvg.append(outcome);
			});
		
			const layerPicker = document.getElementById('multi-layer-picker');
			layerPicker.replaceChildren();
			layers.forEach((layer, index) => {
				const button = document.createElement('button');
				button.type = 'button';
				button.className = 'tree-item';
				const icon = document.createElement('span');
				icon.className = 'tree-icon';
				icon.textContent = layer.selected ? '★' : index < layers.length - 1 ? '▸' : '◆';
				const name = document.createElement('span');
				name.className = 'tree-primary';
				name.textContent = `Search tick ${layer.depth + 1}`;
				const meta = document.createElement('span');
				meta.className = 'tree-secondary';
				meta.textContent = `${layer.simulations.toLocaleString()} candidates`;
				button.append(icon, name, meta);
				button.setAttribute('aria-pressed', String(state.focusKind === 'layer' && index === state.multiLayerIndex));
				button.addEventListener('click', () => {
					stopForManualNavigation();
					state.focusKind = 'layer';
					state.multiLayerIndex = index;
					render();
				});
				layerPicker.append(button);
			});
			renderMultiLayerDetail(
				multiTick,
				layers[state.multiLayerIndex],
				state.multiLayerIndex === layers.length - 1
			);
		
			const pathList = document.getElementById('multi-path');
			const pathTree = document.getElementById('multi-path-tree');
			pathList.replaceChildren();
			pathTree.replaceChildren();
			if (!multiTick.selectedPathFound) {
				const item = document.createElement('li');
				item.textContent = 'Selected path was not retained by the diagnostic search.';
				pathList.append(item);
				const unavailable = document.createElement('span');
				unavailable.className = 'tree-secondary';
				unavailable.textContent = 'Unavailable';
				pathTree.append(unavailable);
				return;
			}
			multiTick.path.forEach((step, index) => {
				const treeButton = document.createElement('button');
				treeButton.type = 'button';
				treeButton.className = 'tree-item';
				const treeIcon = document.createElement('span');
				treeIcon.className = 'tree-icon';
				treeIcon.textContent = index === multiTick.path.length - 1 ? '★' : '↳';
				const treeName = document.createElement('span');
				treeName.className = 'tree-primary';
				treeName.textContent = `Tick ${step.depth + 1} · ${step.config}`;
				const treeMeta = document.createElement('span');
				treeMeta.className = 'tree-secondary';
				treeMeta.textContent = index === multiTick.path.length - 1
					? fitOf(step.loss).label
					: 'implicit';
				treeButton.append(treeIcon, treeName, treeMeta);
				treeButton.setAttribute('aria-pressed', String(state.focusKind === 'path' && index === state.pathIndex));
				treeButton.addEventListener('click', () => {
					stopForManualNavigation();
					state.focusKind = 'path';
					state.pathIndex = index;
					render();
				});
				pathTree.append(treeButton);
		
				const item = document.createElement('li');
				const label = document.createElement('strong');
				label.textContent = `Tick ${step.depth + 1}`;
				const main = document.createElement('div');
				main.className = 'multi-path-main';
				const config = document.createElement('code');
				config.textContent = `${step.key} · ${step.config}`;
				const stateLine = document.createElement('span');
				stateLine.className = 'multi-path-state';
				stateLine.textContent = `motion ${step.motion} → position (${step.x.toFixed(5)}, ${step.y.toFixed(5)}, ${step.z.toFixed(5)})`;
				main.append(config, stateLine);
				const outcome = document.createElement('div');
				outcome.className = 'multi-path-outcome';
				const loss = document.createElement('span');
				loss.className = 'path-loss';
				loss.textContent = index === multiTick.path.length - 1
					? `${fitOf(step.loss).label} · loss ${formatDecimal(step.loss, 4)}`
					: `Implicit · ${formatDecimal(step.loss, 4)} remaining`;
				const omission = document.createElement('span');
				omission.className = 'multi-path-state';
				omission.textContent = step.implicitEligible
					? `omission ${formatDecimal(step.omissionDistance, 4)} ≤ ${formatDecimal(multiTick.omissionLimit, 4)}`
					: `omission ${formatDecimal(step.omissionDistance, 4)} > ${formatDecimal(multiTick.omissionLimit, 4)}`;
				outcome.append(loss);
				if (index < multiTick.path.length - 1) outcome.append(omission);
				item.append(label, main, outcome);
				pathList.append(item);
			});
		}
		
		function renderStage(tick) {
			const stage = tick.stages[state.stageIndex];
			document.getElementById('stage-title').textContent = stage.name;
			const progression = [`${stage.inputs.toLocaleString()} input${stage.inputs === 1 ? '' : 's'}`];
			if (stage.attempted !== stage.inputs || stage.duplicates > 0) {
				progression.push(`${stage.attempted.toLocaleString()} emitted`);
			}
			progression.push(`${stage.outputs.toLocaleString()} unique`);
			const notes = [];
			if (stage.duplicates > 0) notes.push(`${stage.duplicates.toLocaleString()} merged`);
			if (stage.finishable !== stage.outputs) notes.push(`${stage.finishable.toLocaleString()} explicit-capable`);
			document.getElementById('stage-summary').textContent = `${progression.join(' → ')}${notes.length ? ` · ${notes.join(' · ')}` : ''}`;
		
			const fanOutEntries = Object.entries(stage.fanOut)
				.sort((a, b) => Number(a[0]) - Number(b[0]));
			const fanOut = fanOutEntries
				.map(([children, parents]) => `${parents} parent${parents === 1 ? '' : 's'} → ${children} child${children === '1' ? '' : 'ren'}`)
				.join(' · ');
			document.getElementById('fanout').textContent = fanOutEntries.length === 1 && fanOutEntries[0][0] === '1'
				? ''
				: `Fan-out · ${fanOut}`;
		
			const winner = document.getElementById('winner');
			if (stage.winner) {
				winner.className = 'winner-box';
				winner.textContent = `Selected · ${stage.winner}`;
			} else {
				winner.className = 'winner-box missing';
				winner.textContent = tick.winnerInFirstLayer
					? 'No distinct selected configuration'
					: `Winner is in search layer ${tick.depth + 1}`;
			}
		
			const samples = document.getElementById('samples');
			samples.replaceChildren();
			stage.samples.filter(sample => sample !== stage.winner).forEach(sample => {
				const item = document.createElement('li');
				item.textContent = sample;
				samples.append(item);
			});
		}
		
		tickInput.addEventListener('input', event => {
			stopForManualNavigation();
			state.tickIndex = Number(event.target.value);
			state.stageIndex = 0;
			resetPossibilityFocus(trace[state.tickIndex]);
			render();
		});
		previous.addEventListener('click', () => {
			stopForManualNavigation();
			state.tickIndex = Math.max(0, state.tickIndex - 1);
			state.stageIndex = 0;
			resetPossibilityFocus(trace[state.tickIndex]);
			render();
		});
		next.addEventListener('click', () => {
			stopForManualNavigation();
			state.tickIndex = Math.min(trace.length - 1, state.tickIndex + 1);
			state.stageIndex = 0;
			resetPossibilityFocus(trace[state.tickIndex]);
			render();
		});
		play.addEventListener('click', () => {
			if (state.playing) {
				setPlaying(false);
				render();
				return;
			}
			if (state.tickIndex === trace.length - 1 && state.progress >= 1) {
				state.tickIndex = 0;
				state.stageIndex = 0;
				resetPossibilityFocus(trace[0]);
				state.progress = 0;
				render();
			} else if (state.progress >= 1) {
				state.progress = 0;
				updateWorldInterpolation(trace[state.tickIndex]);
			}
			setPlaying(true);
		});
		window.addEventListener('resize', () => {
			renderGraph(trace[state.tickIndex]);
			renderMultiTick(trace[state.tickIndex]);
		});
		resetPossibilityFocus(trace[state.tickIndex]);
		render();
		</script>
		</body>
		</html>
		""";

	private static final String HTML = String.join("", HTML_HEAD, HTML_TAIL);
}
