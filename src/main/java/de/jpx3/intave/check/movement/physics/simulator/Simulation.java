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

package de.jpx3.intave.check.movement.physics.simulator;

import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.block.tick.BlockTickEntities;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.MovementCorrection;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.user.ThreadUserLocal;
import de.jpx3.intave.user.User;

import java.util.Objects;

import static de.jpx3.intave.math.MathHelper.distanceOf;

public final class Simulation {
  private static final Simulation INVALID_SIMULATION = new Simulation(MovementConfiguration.blank(), SimulationEnvironment.invalid(), SimulationResult.invalid());
  private static final ThreadUserLocal<Simulation> SIMULATION_OBJ_CACHE = ThreadUserLocal.withInitial(Simulation::new);

  private MovementConfiguration configuration;
  private SimulationResult simulationResult;
  private SimulationEnvironment environment;
  private String blueDetails = "";
  private String purpleDetails = "";
  private int simulationCount;
  private int searchDepth;

  // Stable branch fingerprint used to online-optimize search ordering.
  private long branchFrequencyKey;

	private final boolean mustBeCopied;
  private boolean canFinishExplicitTick;
  private boolean fromExhaustiveSearch;

	private Simulation() {
    this.mustBeCopied = true;
  }

  private Simulation(
    MovementConfiguration configuration,
    SimulationEnvironment environment,
    SimulationResult simulationResult
  ) {
    this.configuration = configuration;
    this.environment = environment;
    this.simulationResult = simulationResult;
    this.mustBeCopied = false;
  }

  public void flush(
    MovementConfiguration configuration,
    SimulationEnvironment environment,
    SimulationResult simulationResult
  ) {
    this.configuration = configuration;
    this.environment = environment;
    this.simulationResult = simulationResult;
    this.blueDetails = "";
    this.purpleDetails = "";
		this.simulationCount = 0;
		this.searchDepth = 0;
		this.branchFrequencyKey = 0L;
  }

  public void expire() {
    this.configuration = MovementConfiguration.blank();
    this.environment = SimulationEnvironment.invalid();
    this.simulationResult = SimulationResult.invalid();
    this.canFinishExplicitTick = false;
    this.blueDetails = "";
    this.purpleDetails = "";
    this.simulationCount = 0;
    this.searchDepth = 0;
  }

  public void setEnvironment(SimulationEnvironment myEnv) {
    this.environment = myEnv;
  }

  public void setCanFinishExplicitTick(boolean canFinishExplicitTick) {
    this.canFinishExplicitTick = canFinishExplicitTick;
  }

  public boolean canFinishExplicitTick() {
    return canFinishExplicitTick;
  }

  public SimulationEnvironment environment() {
    return environment;
  }

  public boolean wasSprinting() {
    return configuration.isSprinting();
  }

  public double offsetDifference() {
    if (this == INVALID_SIMULATION) {
      return 100_000.d;
    }
    return distanceOf(offsetMotion(), environment.sentOffsetMotion());
  }

  public double positionDifference(Position sentPosition) {
    if (this == INVALID_SIMULATION) {
      return 100_000.d;
    }
    return distanceOf(predictedPosition(), sentPosition);
  }

  public Position predictedPosition() {
    return environment.lastPosition().add(offsetMotion());
  }

  public Position postTickPosition() {
    return environment.verifiedLastPosition().add(offsetMotion());
  }

  public MovementCorrection setbackPosition(User user, int ticksAhead) {
    if (ticksAhead < 0) {
      throw new IllegalArgumentException("ticksAhead must not be negative");
    }
    MovementConfiguration nextConfiguration = configuration;
    SimulationEnvironment next = environment.mutableView();
    Position position = postTickPosition();
    next.updateMovement(position, null);
    next.setLastPosition(environment.verifiedLastPosition());
    next.assumeOccurred(this);
    Motion motion = next.simulator().simulateAfterTick(
      user, next, configuration, position, actualMotion().copy()
    );
    motion = BlockTickEntities.tick(user, next, position, motion);
    next.clearPostTickMotionCandidates();
    next.setBaseMotion(motion);
    next.setLastOnGround(next.onGround());
    next.setVerifiedLastPosition(next.position(), "Future tick simulation");
    for (int tick = 0; tick < ticksAhead; tick++) {
      next.tickComplete(false, false, false);
      next.simulator().simulateBetween(user, next, nextConfiguration);
    }
    return new MovementCorrection(
      new PositionMoveRotation(Position.mutableCopy(next.position()),
      next.mutableBaseMotionCopy(), next.rotation()), next.onGround()
    );
  }

  public Motion offsetMotion() {
    return simulationResult.offsetMotion();
  }

  public Motion actualMotion() {
    return simulationResult.actualMotion();
  }

  public void appendBlue(String details) {
    this.blueDetails += details;
  }

  public void appendPurple(String details) {
    this.purpleDetails += details;
  }

  public void setWasFromExhaustiveSearch() {
    this.fromExhaustiveSearch = true;
  }

  public boolean isFromExhaustiveSearch() {
    return fromExhaustiveSearch;
  }

  public String blueDetails() {
    return blueDetails;
  }

  public String purpleDetails() {
    return purpleDetails;
  }

  public void setSimulationCount(int simulationCount) {
    this.simulationCount = simulationCount;
  }

  public void addSimulationCount(int simulationCount) {
    this.simulationCount += simulationCount;
  }

  public int simulationCount() {
    return simulationCount;
  }

  public void setSearchDepth(int searchDepth) {
    this.searchDepth = searchDepth;
  }

  public int searchDepth() {
    return searchDepth;
  }

  public void setBranchFrequencyKey(long branchFrequencyKey) {
    this.branchFrequencyKey = branchFrequencyKey;
  }

  public long branchFrequencyKey() {
    return branchFrequencyKey;
  }

  public boolean resultsInFlyingPacket(
    SimulationEnvironment environment, double limit
  ) {
    Position lastReportedPosition = environment.lastPosition();
    Position newPosition = postTickPosition();
    double distance = lastReportedPosition.distance(newPosition);
    return distance < limit;
  }

  public SimulationResult result() {
    return simulationResult;
  }

  public MovementConfiguration configuration() {
    return configuration;
  }

  public Simulation reusableCopy() {
    Simulation copy = new Simulation(configuration, environment, simulationResult);
    copy.blueDetails = blueDetails;
    copy.simulationCount = simulationCount;
    copy.searchDepth = searchDepth;
    copy.canFinishExplicitTick = canFinishExplicitTick;
//    copy.purpleDetails = purpleDetails;
    copy.fromExhaustiveSearch = fromExhaustiveSearch;
    copy.branchFrequencyKey = branchFrequencyKey;
    return copy;
  }

  public Simulation select(Simulation other) {
    if (this == INVALID_SIMULATION) {
      return other.reusableCopy();
    }
    if (other == INVALID_SIMULATION) {
      return this.reusableCopy();
    }
    if (this.canFinishExplicitTick && !other.canFinishExplicitTick) {
      return this.reusableCopy();
    } else if (!this.canFinishExplicitTick && other.canFinishExplicitTick) {
      return other.reusableCopy();
    }
    return selectByDistance(other, offsetDifference(), other.offsetDifference());
  }

  public Simulation select(Simulation other, Position sentPosition) {
    if (this == INVALID_SIMULATION) {
      return other.reusableCopy();
    }
    if (other == INVALID_SIMULATION) {
      return this.reusableCopy();
    }
    if (this.canFinishExplicitTick && !other.canFinishExplicitTick) {
      return this.reusableCopy();
    } else if (!this.canFinishExplicitTick && other.canFinishExplicitTick) {
      return other.reusableCopy();
    }
    return selectByDistance(
      other, positionDifference(sentPosition), other.positionDifference(sentPosition)
    );
  }

  private Simulation selectByDistance(
    Simulation other, double thisDistance, double otherDistance
  ) {
    double finiteThisDistance = finiteDistance(thisDistance);
    double finiteOtherDistance = finiteDistance(otherDistance);
    boolean thisIsUnconfirmedSlotSwitch = unconfirmedLegacyItemUseSlotSwitch();
    boolean otherIsUnconfirmedSlotSwitch = other.unconfirmedLegacyItemUseSlotSwitch();

    Simulation selectedSimulation;
    if (thisIsUnconfirmedSlotSwitch && !otherIsUnconfirmedSlotSwitch) {
      selectedSimulation = finiteThisDistance < finiteOtherDistance ? this : other;
    } else if (!thisIsUnconfirmedSlotSwitch && otherIsUnconfirmedSlotSwitch) {
      selectedSimulation = finiteOtherDistance < finiteThisDistance ? other : this;
    } else {
      selectedSimulation = finiteThisDistance < finiteOtherDistance ? this : other;
    }
    if (selectedSimulation.mustBeCopied) {
      selectedSimulation = selectedSimulation.reusableCopy();
    }
    return selectedSimulation;
  }

  private boolean unconfirmedLegacyItemUseSlotSwitch() {
    return !configuration.isHandActive()
      && environment.legacyItemUseSlotSwitchPending();
  }

  private static double finiteDistance(double distance) {
    return Double.isFinite(distance) ? distance : Double.POSITIVE_INFINITY;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Simulation)) {
      return false;
    }
    Simulation other = (Simulation) obj;
    return configuration.equals(other.configuration) &&
      simulationResult.equals(other.simulationResult) &&
      canFinishExplicitTick == other.canFinishExplicitTick;
  }

  @Override
  public int hashCode() {
    return Objects.hash(configuration, simulationResult, canFinishExplicitTick);
  }

  public boolean offsetMotionDiffersFromActualMotion() {
    return simulationResult.offsetMotionDiffersFromActualMotion();
  }

  public boolean offsetMotionDiffersFromActualMotionInXZ() {
    return simulationResult.offsetMotionDiffersFromActualMotionInXZ();
  }

  public static Simulation of(User user, MovementConfiguration configuration, SimulationEnvironment environment, SimulationResult simulationResult) {
    Simulation simulation = SIMULATION_OBJ_CACHE.get(user);
    simulation.flush(configuration, environment, simulationResult);
    return simulation;
  }

  public static Simulation invalid() {
    return INVALID_SIMULATION;
  }
}
