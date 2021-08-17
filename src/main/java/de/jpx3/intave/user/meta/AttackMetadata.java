package de.jpx3.intave.user.meta;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.detect.checks.combat.heuristics.mining.MiningStrategyContainer;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.module.tracker.entity.ClientEntityTracker;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import de.jpx3.intave.module.tracker.entity.WrappedEntity.EntityPositionContext;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.client.RotationHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

@Relocate
public final class AttackMetadata {
  private final Player player;
  private double lastReach;
  private int lastAttackedEntityID = -1;

  private long lastAttack = 0;
  private long lastEntitySwitch = 0;

  private WrappedEntity lastAttackedEntity;
  private float perfectYaw, perfectPitch;
  private float previousPerfectYaw, previousPerfectPitch;

  @Nullable
  public MiningStrategyContainer activeMiningStrategy;
  @Nullable
  public MiningStrategy lastMiningStrategy;

  @Nullable
  private FakePlayer fakePlayer;
  public double fakePlayerLastReportedX;
  public double fakePlayerLastReportedY;
  public double fakePlayerLastReportedZ;

  public AttackMetadata(Player player) {
    this.player = player;
  }

  public void updatePerfectRotation() {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    double positionX = movementData.positionX;
    double lastPositionX = movementData.lastPositionX;
    double positionY = movementData.positionY;
    double lastPositionY = movementData.lastPositionY;
    double positionZ = movementData.positionZ;
    double lastPositionZ = movementData.lastPositionZ;

    // Set prefect yaw & pitch
    if (lastAttackedEntity != null) {
      EntityPositionContext currentPosition = lastAttackedEntity.position;
      EntityPositionContext lastPosition = lastAttackedEntity.lastPosition;
      perfectYaw = RotationHelper.resolveYawRotation(currentPosition, positionX, positionZ);
      perfectPitch = RotationHelper.resolvePitchRotation(currentPosition, positionX, positionY, positionZ);
      previousPerfectYaw = RotationHelper.resolveYawRotation(lastPosition, lastPositionX, lastPositionZ);
      previousPerfectPitch = RotationHelper.resolvePitchRotation(lastPosition, lastPositionX, lastPositionY, lastPositionZ);
    }
  }

  public FakePlayer fakePlayer() {
    return fakePlayer;
  }

  public boolean recentlyAttacked(long time) {
    return AccessHelper.now() - lastAttack <= time;
  }

  public boolean recentlySwitchedEntity(long time) {
    return AccessHelper.now() - lastEntitySwitch <= time;
  }

  public double lastReach() {
    return lastReach;
  }

  public int lastAttackedEntityID() {
    return lastAttackedEntityID;
  }

  public float perfectYaw() {
    return perfectYaw;
  }

  public float perfectPitch() {
    return perfectPitch;
  }

  public float previousPerfectYaw() {
    return previousPerfectYaw;
  }

  public float previousPerfectPitch() {
    return previousPerfectPitch;
  }

  @Nullable
  public WrappedEntity lastAttackedEntity() {
    return lastAttackedEntity;
  }

  public void setLastReach(double lastReach) {
    this.lastReach = lastReach;
  }

  public void setLastAttackedEntityID(int lastAttackedEntityID) {
    this.lastAttackedEntityID = lastAttackedEntityID;

    WrappedEntity lastAttackedEntity = this.lastAttackedEntity;
    WrappedEntity attackedEntity = ClientEntityTracker.entityByIdentifier(UserRepository.userOf(player), lastAttackedEntityID);
    if (attackedEntity != null && attackedEntity != lastAttackedEntity) {
      this.lastEntitySwitch = AccessHelper.now();
    }

    this.lastAttackedEntity = attackedEntity;
    this.lastAttack = AccessHelper.now();
  }

  public void nullifyLastAttackedEntity() {
    this.lastAttackedEntityID = 0;
    this.lastAttackedEntity = null;
  }

  public void setFakePlayer(FakePlayer fakePlayer) {
    this.fakePlayer = fakePlayer;
  }
}