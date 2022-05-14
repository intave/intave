package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.shade.Position;
import de.jpx3.intave.shade.Rotation;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.GameMode;

import java.util.HashMap;
import java.util.Map;

public final class PlaybackPlayerContainer extends SinkPlayerContainer {
  private Environment environment;
  private final Map<Class<?>, CheckCustomMetadata> metadata = new HashMap<>();
  private int id;
  private int version;
  private boolean outdated;
  private double posX;
  private double posY;
  private double posZ;
  private float yaw;
  private float pitch;
  private float lastYaw;
  private float lastPitch;

  private long lastAttack;

  @Override
  public int id() {
    return id;
  }

  @Override
  public int version() {
    return version;
  }

  @Override
  public boolean outdatedClient() {
    return outdated;
  }

  @Override
  public <T extends CheckCustomMetadata> T meta(Class<T> metaClass) {
    // noinspection unchecked
    return (T) metadata.computeIfAbsent(metaClass, k -> {
      try {
        return metaClass.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public Rotation rotation() {
    return new Rotation(yaw, pitch);
  }

  @Override
  public float yaw() {
    return yaw;
  }

  @Override
  public float pitch() {
    return pitch;
  }

  @Override
  public Rotation lastRotation() {
    return new Rotation(lastYaw, lastPitch);
  }

  @Override
  public float lastYaw() {
    return lastYaw;
  }

  @Override
  public float lastPitch() {
    return lastPitch;
  }

  @Override
  public Position position() {
    return new Position(posX, posY, posZ);
  }

  @Override
  public boolean cursorUponEntity(int id, float expansion) {
    return environment.inSight(id);
  }

  @Override
  public boolean notTeleportedIn(int ticks) {
    return false;
  }

  @Override
  public boolean inGameMode(GameMode gameMode) {
    return false;
  }

  @Override
  public boolean recentlyAttacked(long millis) {
    return environment.hasPassed(lastAttack + millis);
  }

  @Override
  public boolean recentlySwitchedEntity(long millis) {
    return false;
  }

  @Override
  public Environment environment() {
    return environment;
  }

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }


  @Override
  public void debug(String message) {
    System.out.println("[nayoro/debug] " + message);
  }

  @Override
  public void visit(AttackEvent event) {
    lastAttack = System.currentTimeMillis();
    visitAny(event);
  }

  @Override
  public void visit(PlayerInitEvent event) {
    id = event.id();
    version = event.version();
    outdated = event.outdated();
  }

  @Override
  public void visit(PlayerMoveEvent event) {
    lastYaw = yaw;
    lastPitch = pitch;
    if (event.applyX()) {
      this.posX = event.x();
    }
    if (event.applyY()) {
      this.posY = event.y();
    }
    if (event.applyZ()) {
      this.posZ = event.z();
    }
    if (event.applyYaw()) {
      this.yaw = event.yaw();
    }
    if (event.applyPitch()) {
      this.pitch = event.pitch();
    }
  }
}
