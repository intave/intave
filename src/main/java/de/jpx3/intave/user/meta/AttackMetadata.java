package de.jpx3.intave.user.meta;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.check.combat.heuristics.MiningStrategy;
import de.jpx3.intave.check.combat.heuristics.mine.MiningStrategyContainer;
import de.jpx3.intave.module.tracker.entity.EntityTracker;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import de.jpx3.intave.module.tracker.entity.WrappedEntity.EntityPositionContext;
import de.jpx3.intave.player.fake.FakePlayer;
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

  private long lastTimeAttackedByEntity = 0;

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
      perfectYaw = resolveYawRotation(currentPosition, positionX, positionZ);
      perfectPitch = resolvePitchRotation(currentPosition, positionX, positionY, positionZ);
      previousPerfectYaw = resolveYawRotation(lastPosition, lastPositionX, lastPositionZ);
      previousPerfectPitch = resolvePitchRotation(lastPosition, lastPositionX, lastPositionY, lastPositionZ);
    }
  }

  private static float resolveYawRotation(
    WrappedEntity.EntityPositionContext entityPositions,
    double posX, double posZ
  ) {
    final double diffX = entityPositions.posX - posX;
    final double diffZ = entityPositions.posZ - posZ;
    return (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
  }

  private static float resolvePitchRotation(
    WrappedEntity.EntityPositionContext entityPositions,
    double posX, double posY, double posZ
  ) {
    double diffY = entityPositions.posY + 1.62f - (posY + 1.62f);
    double diffX = entityPositions.posX - posX;
    double diffZ = entityPositions.posZ - posZ;
    double d3 = Math.sqrt(diffX * diffX + diffZ * diffZ);
    return (float) (-Math.atan2(diffY, d3) * 180.0 / Math.PI);
  }

  public FakePlayer fakePlayer() {
    return fakePlayer;
  }

  public boolean recentlyAttacked(long time) {
    return System.currentTimeMillis() - lastAttack <= time;
  }

  public boolean recentlySwitchedEntity(long time) {
    return System.currentTimeMillis() - lastEntitySwitch <= time;
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
    WrappedEntity attackedEntity = EntityTracker.entityByIdentifier(UserRepository.userOf(player), lastAttackedEntityID);
    if (attackedEntity != null && attackedEntity != lastAttackedEntity) {
      this.lastEntitySwitch = System.currentTimeMillis();
    }

    this.lastAttackedEntity = attackedEntity;
    this.lastAttack = System.currentTimeMillis();
  }

  public void noteExternalAttack() {
    lastTimeAttackedByEntity = System.currentTimeMillis();
  }

  public boolean wasRecentlyAttackedByEntity() {
    return System.currentTimeMillis() - lastTimeAttackedByEntity < 2000;
  }

  public void nullifyLastAttackedEntity() {
    this.lastAttackedEntityID = 0;
    this.lastAttackedEntity = null;
  }

  public void setFakePlayer(FakePlayer fakePlayer) {
    this.fakePlayer = fakePlayer;
  }
}