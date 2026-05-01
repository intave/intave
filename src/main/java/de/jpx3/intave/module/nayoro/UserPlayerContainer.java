package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

public final class UserPlayerContainer implements PlayerContainer {
  private final User user;
  private final Environment environment;

  public UserPlayerContainer(User user, Environment environment) {
    this.user = user;
    this.environment = environment;
  }

  @Override
  public void debug(String message) {
    if (IntaveControl.DEBUG_HEURISTICS) {
      user.player().sendMessage("[debug] " + message);
      System.out.println("[debug] " + message);
    }
  }

  @Override
  public void nerf(AttackNerfStrategy strategy, String originCode) {
    user.nerf(strategy, originCode);
  }

  @Override
  public void applyIfUserPresent(Consumer<? super User> action) {
    action.accept(user);
  }

  @Override
  public Environment environment() {
    return environment;
  }

  @Override
  public int id() {
    return user.player().getEntityId();
  }

  @Override
  public int version() {
    return user.protocolVersion();
  }

  @Override
  public boolean outdatedClient() {
    return user.meta().protocol().outdatedClient();
  }

  @Override
  public boolean inGameMode(GameMode gameMode) {
    AbilityTracker.GameMode nativeGameMode = AbilityTracker.GameMode.fromBukkit(gameMode);
    return user.meta().abilities().inGameModeIncludePending(nativeGameMode);
  }

  @Override
  public boolean recentlyAttacked(long millis) {
    return user.meta().attack().recentlyAttacked(millis);
  }

  @Override
  public boolean recentlySwitchedEntity(long millis) {
    return user.meta().attack().recentlySwitchedEntity(millis);
  }

  @Override
  public int lastAttackedEntity() {
    Entity entity = user.meta().attack().lastAttackedEntity();
    return entity == null ? -1 : entity.entityId();
  }

  @Override
  public float perfectYaw() {
    return user.meta().attack().perfectYaw();
  }

  @Override
  public float perfectPitch() {
    return user.meta().attack().perfectPitch();
  }

  @Override
  public <M extends CheckCustomMetadata> M meta(Class<M> metaClass) {
    //noinspection unchecked
    return (M) user.checkMetadata(metaClass);
  }

  @Override
  public Rotation rotation() {
    return user.meta().movement().rotation();
  }

  @Override
  public float yaw() {
    return user.meta().movement().rotationYaw();
  }

  @Override
  public float pitch() {
    return user.meta().movement().rotationPitch();
  }

  @Override
  public Rotation lastRotation() {
    return user.meta().movement().lastRotation();
  }

  @Override
  public float lastYaw() {
    return user.meta().movement().lastRotationYaw();
  }

  @Override
  public float lastPitch() {
    return user.meta().movement().lastRotationPitch();
  }

  @Override
  public Position position() {
    return user.meta().movement().position();
  }

  @Override
  public double x() {
    return user.meta().movement().positionX();
  }

  @Override
  public double y() {
    return user.meta().movement().positionY();
  }

  @Override
  public double z() {
    return user.meta().movement().positionZ();
  }

  @Override
  public boolean cursorUponEntity(int id, float expansion) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    Entity entity = meta.connection().entityBy(id);

    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    double blockReachDistance = reachDistance(inGameMode(GameMode.CREATIVE));
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean alternativePositionY = clientData.protocolVersion() == VER_1_8;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    // mouse delay fix
    Raytrace distanceOfResult = Raytracing.blockConstraintEntityRaytrace(
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      rotationYaw, movementData.rotationPitch,
      expansion
    );
    if (distanceOfResult.reach() > blockReachDistance) {
      return false;
    }
    if (!hasAlwaysMouseDelayFix) {
      // normal
      distanceOfResult = Raytracing.blockConstraintEntityRaytrace(
        player,
        entity, true,
        movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
        lastRotationYaw, movementData.rotationPitch,
        expansion
      );
    }
    return distanceOfResult.reach() <= blockReachDistance;
  }

  @Override
  public boolean notTeleportedIn(int ticks) {
    return false;
  }

  private float reachDistance(boolean creative) {
    return (creative ? 5.0F : 3.0F) - 0.005f;
  }
}
