package de.jpx3.intave.player.fake;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.google.common.base.Preconditions;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.shade.WrappedMathHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.player.fake.FakePlayerAttribute.*;
import static de.jpx3.intave.player.fake.MetadataAccess.metadataAccept;
import static de.jpx3.intave.player.fake.MetadataAccess.updateVisibility;
import static de.jpx3.intave.player.fake.RandomStringGenerator.randomString;
import static de.jpx3.intave.player.fake.ScoreboardAccessor.sendScoreboard;
import static de.jpx3.intave.player.fake.TablistMutator.addToTabList;
import static de.jpx3.intave.player.fake.TablistMutator.removeFromTabList;

public abstract class FakePlayerBody extends FakePlayerIdentity {
  private final static boolean MODERN_POSITION_PROCESSING = MinecraftVersions.VER1_9_0.atOrAbove();
  private final static Map<Integer, Object> METADATA = new HashMap<Integer, Object>(){{
    // Entity
    put(0, (byte) 0);
    put(1, MODERN_POSITION_PROCESSING ? 300 : (short) 300);
    put(2, "");
    put(3, (byte) 0);
    put(4, (byte) 0);
    // EntityLivingBase
    put(6, 1.0f);
    put(7, 0);
    put(8, (byte) 0);
    put(9, (byte) 0);
    // EntityPlayer
    put(16, (byte) 0);
    put(17, 0.0f);
    put(18, 0);
    put(10, (byte) 0);
  }};

  private final Player observer;
  private final String listedPrefix, prefix;
  private final int attributes;

  protected FakePlayerBody(
    Player observer,
    int entityId, int attributes,
    WrappedGameProfile profile,
    String listedPrefix, String prefix
  ) {
    super(entityId, profile);
    this.observer = observer;
    this.attributes = attributes;
    this.listedPrefix = listedPrefix;
    this.prefix = prefix;
  }

  protected void spawn(Location spawn) {
    PacketContainer spawnPacket = create(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
    WrappedGameProfile profile = profile();
    WrappedDataWatcher dataWatcher = dataWatcher();
    spawnPacket.getModifier()
      .write(0, identifier())
      .write(1, profile.getUUID())
      .write(5, compressRotation(spawn.getYaw()))
      .write(6, compressRotation(spawn.getPitch()));
    pushLocationToPacket(spawnPacket, spawn, 0);
    if (!MODERN_POSITION_PROCESSING) {
      spawnPacket.getModifier().write(7, 0);
    }
    METADATA.forEach((index, object) -> metadataAccept(dataWatcher, index, object.getClass(), object));
    spawnPacket.getDataWatcherModifier().write(0, dataWatcher);
    String tabListName = listedPrefix + profile.getName();
    addToTabList(observer, profile, tabListName);
    send(spawnPacket);
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
    PacketContainer packet = create(PacketType.Play.Server.ENTITY_DESTROY);
    packet.getIntegerArrays().write(0, new int[]{identifier()});
    send(packet);
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

    PacketContainer packet = null;
    if (move && look) {
      packet = create(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
      packet.getIntegers().write(0, identifier());
      if (MODERN_POSITION_PROCESSING) {
        packet.getIntegers()
          .write(1, (int) ((to.getX() - from.getX()) * 4096.0D))
          .write(2, (int) ((to.getY() - from.getY()) * 4096.0D))
          .write(3, (int) ((to.getZ() - from.getZ()) * 4096.0D));
        packet.getBytes()
          .write(0, compressRotation(to.getYaw()))
          .write(1, compressRotation(to.getPitch()));
      } else {
        packet.getBytes()
          .write(0, compressAxisUpdate(to.getX(), from.getX()))
          .write(1, compressAxisUpdate(to.getY(), from.getY()))
          .write(2, compressAxisUpdate(to.getZ(), from.getZ()))
          .write(3, compressRotation(to.getYaw()))
          .write(4, compressRotation(to.getPitch()));
      }
    } else if (move) {
      packet = create(PacketType.Play.Server.REL_ENTITY_MOVE);
      packet.getIntegers().write(0, identifier());
      if (MODERN_POSITION_PROCESSING) {
        packet.getIntegers()
          .write(1, (int) ((to.getX() - from.getX()) * 4096.0D))
          .write(2, (int) ((to.getY() - from.getY()) * 4096.0D))
          .write(3, (int) ((to.getZ() - from.getZ()) * 4096.0D));

      } else {
        packet.getBytes()
          .write(0, compressAxisUpdate(to.getX(), from.getX()))
          .write(1, compressAxisUpdate(to.getY(), from.getY()))
          .write(2, compressAxisUpdate(to.getZ(), from.getZ()));
      }
    } else if (look) {
      packet = create(PacketType.Play.Server.ENTITY_LOOK);
      packet.getIntegers().write(0, identifier());
      packet.getBytes()
        .write(0, compressRotation(to.getYaw()))
        .write(1, compressRotation(to.getPitch()));
    }
    if (packet != null) {
      packet.getBooleans().write(0, onGround);
      send(packet);
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
    PacketContainer packet = create(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
    packet.getIntegers().write(0, identifier());
    packet.getBytes().write(0, compressRotation(yaw));
    send(packet);
  }

  private static boolean rotationChange(Location location1, Location location2) {
    boolean equalYaw = location1.getYaw() == location2.getYaw();
    boolean equalPitch = location1.getPitch() == location2.getPitch();
    return !equalYaw || !equalPitch;
  }

  private final static double COORDINATE_COMPRESSION_FACTOR = 32.0D;

  private byte compressAxisUpdate(double coordinateTo, double coordinateFrom) {
    double fixedTo = WrappedMathHelper.floor(coordinateTo * COORDINATE_COMPRESSION_FACTOR);
    double fixedFrom = WrappedMathHelper.floor(coordinateFrom * COORDINATE_COMPRESSION_FACTOR);
    return (byte) (fixedTo - fixedFrom);
  }

  public void movementTeleport(Location to, boolean onGround) {
    Preconditions.checkNotNull(to);
    float rotationYaw = to.getYaw();
    float rotationPitch = to.getPitch();
    PacketContainer packet = create(PacketType.Play.Server.ENTITY_TELEPORT);
    packet.getIntegers().write(0, identifier());
    pushLocationToPacket(packet, to, 0);
    packet.getBytes()
      .write(0, compressRotation(rotationYaw))
      .write(1, compressRotation(rotationPitch));
    packet.getBooleans().write(0, onGround);
    send(packet);
  }

  private void pushLocationToPacket(PacketContainer packet, Location location, int offset) {
    if (MODERN_POSITION_PROCESSING) {
      packet.getDoubles()
        .write(offset + 0, location.getX())
        .write(offset + 1, location.getY())
        .write(offset + 2, location.getZ());
    } else {
      packet.getIntegers()
        .write(offset + 1, compressCoordinate(location.getX()))
        .write(offset + 2, compressCoordinate(location.getY()))
        .write(offset + 3, compressCoordinate(location.getZ()));
    }
  }

  private static final float FIX_CONVERT_FACTOR = 256.0F / 360.0F;

  private byte compressRotation(final float f) {
    return (byte) (f * FIX_CONVERT_FACTOR);
  }

  private int compressCoordinate(double coordinate) {
    return (int) Math.floor(coordinate * COORDINATE_COMPRESSION_FACTOR);
  }

  private final static int SOUND_CONVERT_FACTOR = 8;

  public void makeWalkingSound(Location location) {
    PacketContainer packet = create(PacketType.Play.Server.NAMED_SOUND_EFFECT);

    // Set SoundCategory and SoundEffect when on 1.9 or higher
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      packet.getSoundEffects().write(0, Sound.BLOCK_STONE_STEP);
      packet.getSoundCategories().write(0, EnumWrappers.SoundCategory.PLAYERS);
    } else {
      packet.getStrings().write(0, walkingSoundAt(location));
    }
    packet.getIntegers()
      .write(0, (int) (location.getX() * SOUND_CONVERT_FACTOR))
      .write(1, (int) (location.getY() * SOUND_CONVERT_FACTOR))
      .write(2, (int) (location.getZ() * SOUND_CONVERT_FACTOR));
    if (MinecraftVersions.VER1_10_0.atOrAbove()) {
      packet.getFloat().write(0, 1f).write(1, 0.15f);
    } else {
      packet.getIntegers().write(3, 63);
      packet.getFloat().write(0, 0.15f);
    }
    send(packet);
  }

  private String walkingSoundAt(Location location) {
    Block block = VolatileBlockAccess.unsafe__BlockAccess(location.clone().add(0.0, -1.0, 0.0));
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
    PacketContainer scoreboardCreatePacket = create(PacketType.Play.Server.SCOREBOARD_TEAM);
    String teamName = randomString();
    scoreboardCreatePacket.getStrings()
      .write(0, teamName)
      .write(2, prefix);
    send(scoreboardCreatePacket);
    sendScoreboard(observer, teamName, profile(), hasAttribute(attributes, INVISIBLE));
  }

  public void latencyInitialize() {
    PacketContainer packet = create(PacketType.Play.Server.PLAYER_INFO);
    WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromText(prefix);
    PlayerInfoData playerInfoData = new PlayerInfoData(
      profile(),
      0,
      EnumWrappers.NativeGameMode.SURVIVAL,
      wrappedChatComponent
    );
    List<PlayerInfoData> playerInformationList = packet.getPlayerInfoDataLists().readSafely(0);
    playerInformationList.add(playerInfoData);
    packet.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.UPDATE_LATENCY);
    packet.getPlayerInfoDataLists().writeSafely(0, playerInformationList);
    packet.getBooleans().writeSafely(0, true);
    send(packet);
  }

  private PacketContainer create(PacketType packetType) {
    return ProtocolLibrary.getProtocolManager().createPacket(packetType);
  }

  private void send(PacketContainer packet) {
    if (threadEscape(() -> send(packet))) {
      return;
    }
    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(this.observer, packet);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private boolean threadEscape(Runnable apply) {
    if (Bukkit.isPrimaryThread()) {
      return false;
    }
    Synchronizer.synchronize(apply);
    return true;
  }

  public Player observer() {
    return observer;
  }

  public int attributes() {
    return attributes;
  }
}
