package de.jpx3.intave.player.fake;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.google.common.base.Preconditions;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.executor.Synchronizer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import static de.jpx3.intave.player.fake.FakePlayerAttribute.*;
import static de.jpx3.intave.player.fake.MetadataAccess.updateVisibility;
import static de.jpx3.intave.player.fake.RandomStringGenerator.randomString;
import static de.jpx3.intave.player.fake.ScoreboardAccessor.sendScoreboard;
import static de.jpx3.intave.player.fake.TablistMutator.addToTabList;
import static de.jpx3.intave.player.fake.TablistMutator.removeFromTabList;

public abstract class FakePlayerBody extends FakePlayerIdentity {
  private final Player observer;
  private final String listedPrefix, prefix;
  private final int attributes;

  protected FakePlayerBody(
    Player observer,
    int entityId, int attributes,
    UserProfile profile,
    String listedPrefix, String prefix
  ) {
    super(entityId, profile);
    this.observer = observer;
    this.attributes = attributes;
    this.listedPrefix = listedPrefix;
    this.prefix = prefix;
  }

  protected void spawn(Location spawn) {
    initializeMetadata();
    UserProfile profile = profile();
    String tabListName = listedPrefix + profile.getName();
    addToTabList(observer, profile, tabListName);
    send(new WrapperPlayServerSpawnPlayer(
      identifier(),
      profile.getUUID(),
      new Vector3d(spawn.getX(), spawn.getY(), spawn.getZ()),
      spawn.getYaw(),
      spawn.getPitch(),
      metadata()
    ));
    send(new WrapperPlayServerEntityMetadata(identifier(), metadata()));
    if (!hasAttribute(attributes, IN_TABLIST)) {
      removeFromTabList(observer, profile());
    }
    if (hasAttribute(attributes, INVISIBLE)) {
      updateVisibility(observer, this, true);
    }
  }

  public void despawn() {
    if (hasAttribute(attributes, IN_TABLIST)) {
      TablistMutator.removeFromTabList(observer(), profile());
    }
    send(new WrapperPlayServerDestroyEntities(identifier()));
  }

  public void respawn(Location location) {
    if (threadEscape(() -> respawn(location))) {
      return;
    }
    despawn();
    spawn(location);
  }

  public void setSprinting(boolean sprinting) {
    MetadataAccess.setSprinting(observer, this, sprinting);
  }

  public void setSneaking(boolean sneaking) {
    MetadataAccess.setSneaking(observer, this, sneaking);
  }

  public void movementUpdate(
    Location to,
    Location from,
    boolean onGround
  ) {
    boolean move = safeDistance(to, from) != 0;
    boolean look = rotationChange(to, from);

    if (move && look) {
      send(new WrapperPlayServerEntityRelativeMoveAndRotation(
        identifier(),
        to.getX() - from.getX(),
        to.getY() - from.getY(),
        to.getZ() - from.getZ(),
        to.getYaw(),
        to.getPitch(),
        onGround
      ));
    } else if (move) {
      send(new WrapperPlayServerEntityRelativeMove(
        identifier(),
        to.getX() - from.getX(),
        to.getY() - from.getY(),
        to.getZ() - from.getZ(),
        onGround
      ));
    } else if (look) {
      send(new WrapperPlayServerEntityRotation(identifier(), to.getYaw(), to.getPitch(), onGround));
    }
    if (look) {
      rotationUpdate(to.getYaw());
    }
  }

  public double safeDistance(Location location1, Location location2) {
    if (location1.getWorld() != location2.getWorld()) {
      return 0.0;
    }
    return location1.distance(location2);
  }

  private void rotationUpdate(float yaw) {
    send(new WrapperPlayServerEntityHeadLook(identifier(), yaw));
  }

  private static boolean rotationChange(Location location1, Location location2) {
    boolean equalYaw = location1.getYaw() == location2.getYaw();
    boolean equalPitch = location1.getPitch() == location2.getPitch();
    return !equalYaw || !equalPitch;
  }

  public void movementTeleport(Location to, boolean onGround) {
    Preconditions.checkNotNull(to);
    send(new WrapperPlayServerEntityTeleport(
      identifier(),
      new Vector3d(to.getX(), to.getY(), to.getZ()),
      to.getYaw(),
      to.getPitch(),
      onGround
    ));
  }

  public void makeWalkingSound(Location location) {
    try {
      if (MinecraftVersions.VER1_9_0.atOrAbove()) {
        observer.playSound(location, Sound.BLOCK_STONE_STEP, 0.15f, 1.0f);
      } else {
        observer.playSound(location, walkingSoundAt(location), 0.15f, 1.0f);
      }
    } catch (Throwable ignored) {
      observer.playSound(location, Sound.BLOCK_STONE_STEP, 0.15f, 1.0f);
    }
  }

  private String walkingSoundAt(Location location) {
    Block block = VolatileBlockAccess.blockAccess(location.clone().add(0.0, -1.0, 0.0));
    switch (BlockTypeAccess.typeAccess(block)) {
      case GRASS: {
        return "step.grass";
      }
      case GRAVEL: {
        return "step.gravel";
      }
      case WOOD: {
        return "step.wood";
      }
      default:
        return "step.stone";
    }
  }

  public void applyDisplayName() {
    String teamName = randomString();
    sendScoreboard(observer, teamName, prefix, profile(), hasAttribute(attributes, INVISIBLE));
  }

  public void latencyInitialize() {
    TablistMutator.updateLatency(observer, profile(), 0, prefix);
  }

  private void send(PacketWrapper<?> packet) {
    if (threadEscape(() -> send(packet))) {
      return;
    }
    PacketEvents.getAPI().getPlayerManager().sendPacket(this.observer, packet);
  }

  private boolean threadEscape(Runnable apply) {
    if (Bukkit.isPrimaryThread()) {
      return false;
    }
    Synchronizer.synchronize(apply);
    return true;
  }

  private void initializeMetadata() {
    metadata(0, EntityDataTypes.BYTE, (byte) 0);
    metadata(MetadataAccess.healthIndex(), EntityDataTypes.FLOAT, FakePlayer.SPAWN_HEALTH_STATE);
  }

  public Player observer() {
    return observer;
  }

  public int attributes() {
    return attributes;
  }
}
