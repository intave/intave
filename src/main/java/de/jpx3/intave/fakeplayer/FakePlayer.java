package de.jpx3.intave.fakeplayer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.fakeplayer.movement.LocationUtils;
import de.jpx3.intave.fakeplayer.movement.types.Movement;
import de.jpx3.intave.fakeplayer.randomaction.ActionType;
import de.jpx3.intave.fakeplayer.randomaction.RandomAction;
import de.jpx3.intave.fakeplayer.randomaction.actions.EquipmentArmorAction;
import de.jpx3.intave.fakeplayer.randomaction.actions.EquipmentHeldItemAction;
import de.jpx3.intave.fakeplayer.randomaction.actions.HurtAnimationAction;
import de.jpx3.intave.fakeplayer.randomaction.actions.SwingAnimationAction;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FakePlayer implements TickTaskScheduler {
  public final static float SPAWN_HEALTH_STATE = 20.0f;
  private final static IntavePlugin plugin = IntavePlugin.singletonInstance();
  private final static ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

  private final WrappedDataWatcher wrappedDataWatcher = new WrappedDataWatcher();
  private final List<RandomAction> actions;
  private final Movement movement;
  private final Player parentPlayer;
  private final User user;
  private final WrappedGameProfile wrappedGameProfile;
  private final String tabListPrefix, prefix;
  private final int fakePlayerID, timeout;
  public double killAuraVL = 0;
  private int taskId;
  private int previousLatency = 0, ticks = 0;

  private final EnumWrappers.NativeGameMode gameMode;
  private final boolean invisible;
  private final boolean visibleInTablist;
  private final boolean equipArmor;
  private final boolean equipHeldItem;
  private final FakePlayerAttackSubscriber attackSubscriber;
  private long lastHurtAction;
  public long lastPingPacketSent;

  FakePlayer(
    Movement movement,
    Player parentPlayer,
    WrappedGameProfile wrappedGameProfile,
    String tabListPrefix,
    String prefix,
    int entityId,
    int timeout,
    boolean invisible,
    boolean visibleInTablist,
    boolean equipArmor,
    boolean equipHeldItem,
    FakePlayerAttackSubscriber attackSubscriber
  ) {
    this.user = UserRepository.userOf(parentPlayer);
    this.timeout = timeout;
    this.movement = movement;
    this.wrappedGameProfile = wrappedGameProfile;
    this.parentPlayer = parentPlayer;
    this.fakePlayerID = entityId;
    this.tabListPrefix = tabListPrefix;
    this.prefix = prefix;
    this.equipArmor = equipArmor;
    this.equipHeldItem = equipHeldItem;
    this.actions = loadActions();
    this.invisible = invisible;
    this.visibleInTablist = visibleInTablist;
    this.attackSubscriber = attackSubscriber;
    this.gameMode = EnumWrappers.NativeGameMode.SURVIVAL;
    user.meta().attackData().setFakePlayer(this);
  }

  public static FakePlayerBuilder builder() {
    return new FakePlayerBuilder();
  }

  private List<RandomAction> loadActions() {
    List<RandomAction> actions = Lists.newArrayList(
      new SwingAnimationAction(parentPlayer, this),
      new HurtAnimationAction(parentPlayer, this)
    );
    if (equipHeldItem) {
      actions.add(new EquipmentHeldItemAction(parentPlayer, this));
    }
    if (equipArmor) {
      actions.add(new EquipmentArmorAction(parentPlayer, this));
    }
    return actions;
  }

  @Override
  public void startTickScheduler() {
    IntavePlugin plugin = IntavePlugin.singletonInstance();
    this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::onTick, 0, 1);
  }

  public void spawn(Location location) {
    Preconditions.checkNotNull(location);
    this.movement.location = location;
    this.movement.botDistance = (movement.minBotDistance() + movement.maxBotDistance()) / 2;
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
    packet.getModifier().writeSafely(0, fakePlayerID);
    packet.getModifier().writeSafely(1, wrappedGameProfile.getUUID());
    packet.getModifier().writeSafely(2, WrappedMathHelper.floor(location.getX() * 32.0));
    packet.getModifier().writeSafely(3, WrappedMathHelper.floor(location.getY() * 32.0));
    packet.getModifier().writeSafely(4, WrappedMathHelper.floor(location.getZ() * 32.0));
    packet.getModifier().writeSafely(5, FakeEntityPositionHelper.getFixRotation(location.getYaw()));
    packet.getModifier().writeSafely(6, FakeEntityPositionHelper.getFixRotation(location.getPitch()));
    packet.getModifier().writeSafely(7, 0);
    // Entity
    wrappedDataWatcher.setObject(0, (byte) 0);
    wrappedDataWatcher.setObject(1, (short) 300);
    wrappedDataWatcher.setObject(3, (byte) 0);
    wrappedDataWatcher.setObject(2, "");
    wrappedDataWatcher.setObject(4, (byte) 0);
    // EntityLivingBase
    wrappedDataWatcher.setObject(7, 0);
    wrappedDataWatcher.setObject(8, (byte) 0);
    wrappedDataWatcher.setObject(9, (byte) 0);
    wrappedDataWatcher.setObject(6, 1.0F); // health
    // EntityPlayer
    wrappedDataWatcher.setObject(16, (byte) 0);
    wrappedDataWatcher.setObject(17, 0.0F);
    wrappedDataWatcher.setObject(18, 0);
    wrappedDataWatcher.setObject(10, (byte) 0);
    packet.getDataWatcherModifier().writeSafely(0, wrappedDataWatcher);
    String tabListName = tabListPrefix + wrappedGameProfile.getName();
    TabListHelper.addToTabList(this.parentPlayer, this.wrappedGameProfile, tabListName);
    try {
      protocolManager.sendServerPacket(this.parentPlayer, packet);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }

    if (!visibleInTablist) {
      TabListHelper.removeFromTabList(this.parentPlayer, this.wrappedGameProfile);
    }
    if (invisible) {
      FakePlayerMetaDataHelper.updateVisibility(parentPlayer, this, true);
    }

    //
    // Apply equipment
    //
    if (equipArmor) {
      RandomAction.findAndProcessAction(this.actions, ActionType.EQUIPMENT);
    }
    if (equipHeldItem) {
      RandomAction.findAndProcessAction(this.actions, ActionType.HELD_ITEM_CHANGE);
    }

    FakePlayerMetaDataHelper.updateHealthFor(parentPlayer, this, SPAWN_HEALTH_STATE);
    applyDisplayName();
    startTickScheduler();
    sendLatency(0);
  }

  public void registerParentPlayerVelocity(double motionX, double motionY, double motionZ) {
    this.movement.velocityChanged = true;
    this.movement.velocityX = motionX * 4;
    this.movement.velocityY = motionY;
    this.movement.velocityZ = motionZ * 4;
  }

  private void applyDisplayName() {
    PacketContainer scoreboardCreatePacket = protocolManager.createPacket(PacketType.Play.Server.SCOREBOARD_TEAM);
    String teamName = PlayerNameHelper.randomString();
    scoreboardCreatePacket.getStrings().writeSafely(0, teamName);
    scoreboardCreatePacket.getStrings().writeSafely(2, prefix);
    try {
      protocolManager.sendServerPacket(this.parentPlayer, scoreboardCreatePacket);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    FakePlayerScoreboardAccessor.sendScoreboard(parentPlayer, teamName, wrappedGameProfile, invisible);
  }

  private void sendLatency(int latency) {
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
    WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromText(prefix);
    PlayerInfoData playerInfoData = new PlayerInfoData(
      wrappedGameProfile,
      latency,
      EnumWrappers.NativeGameMode.SURVIVAL,
      wrappedChatComponent
    );
    List<PlayerInfoData> playerInformationList = packet.getPlayerInfoDataLists().readSafely(0);
    playerInformationList.add(playerInfoData);
    packet.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.UPDATE_LATENCY);
    packet.getPlayerInfoDataLists().writeSafely(0, playerInformationList);
    packet.getBooleans().writeSafely(0, true);
    try {
      protocolManager.sendServerPacket(this.parentPlayer, packet);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private final static int LATENCY_BOUND = 25;

  public int nextLatency() {
    if (previousLatency == 0) {
      int latency = ThreadLocalRandom.current().nextInt(20, 250);
      this.previousLatency = latency;
      return latency;
    }
    int boundingLatency = ThreadLocalRandom.current().nextInt(
      previousLatency - LATENCY_BOUND,
      previousLatency + LATENCY_BOUND
    );
    int nextLatency = Math.max(LATENCY_BOUND, boundingLatency);
    previousLatency = nextLatency;
    return nextLatency;
  }

  public void despawn() {
    stopTickScheduler();
    if (this.visibleInTablist) {
      TabListHelper.removeFromTabList(this.parentPlayer, this.wrappedGameProfile);
    }
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
    packet.getIntegerArrays().writeSafely(0, new int[]{this.fakePlayerID});
    try {
      protocolManager.sendServerPacket(this.parentPlayer, packet);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    user.meta().attackData().setFakePlayer(null);
  }

  private void onTick() {
    ticks++;
    processRandomAction();
    processMovement();
    decreaseViolationLevel();
    double distanceMoved = movement.distanceMoved();
    double distanceToPlayer = movement.distanceToPlayer(parentPlayer);
    setSprinting(distanceMoved > 0.0 && !this.movement.sneaking);
    if (distanceMoved < 0.5 && distanceToPlayer < 9 && ticks != 0) {
      if (ThreadLocalRandom.current().nextInt(1, 10) % 5 == 0 && ticks % 250 == 0) {
        setSneaking(true);
      }
    } else if (distanceMoved > 1.0) {
      setSneaking(false);
    }
    if (this.ticks % 5 == 0 && this.movement.onGround) {
      sendWalkingSoundEffect(this.movement.location);
    }
  }

  private final static int SOUND_CONVERT_FACTOR = 8;

  private void sendWalkingSoundEffect(Location location) {
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);
    packet.getStrings().write(0, resolveSoundName());
    packet.getIntegers().write(0, (int) (location.getX() * SOUND_CONVERT_FACTOR));
    packet.getIntegers().write(1, (int) (location.getY() * SOUND_CONVERT_FACTOR));
    packet.getIntegers().write(2, (int) (location.getZ() * SOUND_CONVERT_FACTOR));
    packet.getFloat().write(0, 0.15f);
    packet.getIntegers().write(3, 63);
    try {
      protocolManager.sendServerPacket(parentPlayer, packet);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private String resolveSoundName() {
    Location location = movement.location;
    Block block = BukkitBlockAccess.blockAccess(location.clone().add(0.0, -1.0, 0.0));
    switch (block.getType()) {
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

  private boolean shouldDespawn() {
    return false;
//    return System.currentTimeMillis() - user.lastEntityAttack > timeout;
  }

  private void decreaseViolationLevel() {
    if (killAuraVL > 0) {
      killAuraVL -= 0.1;
    }
  }

  private void processRandomAction() {
    for (RandomAction action : this.actions) {
      action.mayProcess();
    }
  }

  private void processMovement() {
    this.movement.applyMovementAndRotation(this.parentPlayer.getLocation());
    Location location = this.movement.location;
    Location prevLocation = this.movement.prevLocation;
    if (prevLocation != null) {
      boolean shouldTeleport = LocationUtils.needTeleport(location, prevLocation);
      boolean onGround = this.movement.onGround;
      if (shouldTeleport) {
        sendTeleport(location, onGround);
      } else {
        sendRelativeMovement(location, prevLocation, onGround);
      }
    }
  }

  private void sendRelativeMovement(
    Location to,
    Location from,
    boolean onGround
  ) {
    boolean move = LocationUtils.distanceBetweenLocations(to, from) != 0;
    boolean look = !LocationUtils.equalRotations(to, from);
    if (move && look) {
      PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
      packet.getIntegers().writeSafely(0, this.fakePlayerID);
      packet.getBytes().writeSafely(0, FakeEntityPositionHelper.relativeMoveDiff(to.getX(), from.getX()));
      packet.getBytes().writeSafely(1, FakeEntityPositionHelper.relativeMoveDiff(to.getY(), from.getY()));
      packet.getBytes().writeSafely(2, FakeEntityPositionHelper.relativeMoveDiff(to.getZ(), from.getZ()));
      packet.getBytes().writeSafely(3, FakeEntityPositionHelper.getFixRotation(to.getYaw()));
      packet.getBytes().writeSafely(4, FakeEntityPositionHelper.getFixRotation(to.getPitch()));
      packet.getBooleans().writeSafely(0, onGround);
      try {
        protocolManager.sendServerPacket(this.parentPlayer, packet);
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    } else if (move) {
      PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.REL_ENTITY_MOVE);
      packet.getIntegers().writeSafely(0, this.fakePlayerID);
      packet.getBytes().writeSafely(0, FakeEntityPositionHelper.relativeMoveDiff(to.getX(), from.getX()));
      packet.getBytes().writeSafely(1, FakeEntityPositionHelper.relativeMoveDiff(to.getY(), from.getY()));
      packet.getBytes().writeSafely(2, FakeEntityPositionHelper.relativeMoveDiff(to.getZ(), from.getZ()));
      packet.getBooleans().writeSafely(0, onGround);
      try {
        protocolManager.sendServerPacket(this.parentPlayer, packet);
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    } else if (look) {
      PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_LOOK);
      packet.getIntegers().writeSafely(0, this.fakePlayerID);
      packet.getBytes().writeSafely(0, FakeEntityPositionHelper.getFixRotation(to.getYaw()));
      packet.getBytes().writeSafely(1, FakeEntityPositionHelper.getFixRotation(to.getPitch()));
      packet.getBooleans().writeSafely(0, onGround);
      try {
        protocolManager.sendServerPacket(this.parentPlayer, packet);
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    }
    if (look) {
      headRotation(to.getYaw());
    }

    plugin.eventService()
      .transactionFeedbackService()
      .requestPong(parentPlayer, to, (player, target) -> {
        User user = UserRepository.userOf(player);
        UserMetaAttackData attackData = user.meta().attackData();
        attackData.fakePlayerLastReportedX = target.getX();
        attackData.fakePlayerLastReportedY = target.getY();
        attackData.fakePlayerLastReportedZ = target.getZ();
      });
  }

  public void sendTeleport(Location to, boolean onGround) {
    Preconditions.checkNotNull(to);
    float rotationYaw = to.getYaw();
    float rotationPitch = to.getPitch();
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
    packet.getIntegers().writeSafely(0, this.fakePlayerID);
    packet.getIntegers().writeSafely(1, FakeEntityPositionHelper.getFixCoordinate(to.getX()));
    packet.getIntegers().writeSafely(2, FakeEntityPositionHelper.getFixCoordinate(to.getY()));
    packet.getIntegers().writeSafely(3, FakeEntityPositionHelper.getFixCoordinate(to.getZ()));
    packet.getBytes().writeSafely(0, FakeEntityPositionHelper.getFixRotation(rotationYaw));
    packet.getBytes().writeSafely(1, FakeEntityPositionHelper.getFixRotation(rotationPitch));
    packet.getBooleans().writeSafely(0, onGround);
    try {
      protocolManager.sendServerPacket(this.parentPlayer, packet);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    movement.registerTeleport(to);
    plugin.eventService()
      .transactionFeedbackService()
      .requestPong(parentPlayer, to, (player, target) -> {
        User user = UserRepository.userOf(player);
        UserMetaAttackData attackData = user.meta().attackData();
        attackData.fakePlayerLastReportedX = target.getX();
        attackData.fakePlayerLastReportedY = target.getY();
        attackData.fakePlayerLastReportedZ = target.getZ();
      });
  }

  public void headRotation(float yaw) {
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
    packet.getIntegers().writeSafely(0, this.fakePlayerID);
    packet.getBytes().writeSafely(0, FakeEntityPositionHelper.getFixRotation(yaw));
    try {
      protocolManager.sendServerPacket(this.parentPlayer, packet);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  public void setSprinting(boolean sprinting) {
    FakePlayerMetaDataHelper.setSprinting(parentPlayer, this, sprinting);
    this.movement.sprinting = sprinting;
  }

  public void setSneaking(boolean sneaking) {
    FakePlayerMetaDataHelper.setSneaking(parentPlayer, this, sneaking);
    this.movement.sneaking = sneaking;
  }

  public void onAttack() {
    this.movement.combatEvent();
    if (AccessHelper.now() - this.lastHurtAction > 500) {
      RandomAction.findAndProcessAction(actions, ActionType.HURT_ANIMATION);
      this.lastHurtAction = AccessHelper.now();
    }
  }

  public void moveOnTopOfPlayer() {
    this.movement.moveOnTopOfPlayerTime = AccessHelper.now();
  }

  public int fakePlayerEntityId() {
    return this.fakePlayerID;
  }

  public List<RandomAction> actions() {
    return this.actions;
  }

  public WrappedDataWatcher wrappedDataWatcher() {
    return wrappedDataWatcher;
  }

  public Movement movement() {
    return this.movement;
  }

  public FakePlayerAttackSubscriber attackSubscriber() {
    return attackSubscriber;
  }

  public WrappedGameProfile wrappedGameProfile() {
    return wrappedGameProfile;
  }

  public EnumWrappers.NativeGameMode gameMode() {
    return gameMode;
  }

  @Override
  public int taskId() {
    return this.taskId;
  }
}