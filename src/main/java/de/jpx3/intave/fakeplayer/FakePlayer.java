package de.jpx3.intave.fakeplayer;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.fakeplayer.action.*;
import de.jpx3.intave.fakeplayer.movement.FloatingMovement;
import de.jpx3.intave.fakeplayer.movement.Movement;
import de.jpx3.intave.fakeplayer.movement.PositionRotationLookup;
import de.jpx3.intave.fakeplayer.movement.WalkingMovement;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static de.jpx3.intave.fakeplayer.FakePlayerAttribute.*;
import static de.jpx3.intave.fakeplayer.MetadataAccess.updateHealthFor;
import static de.jpx3.intave.fakeplayer.ProfileLookup.acquireGameProfile;

public final class FakePlayer extends FakePlayerBody {
  public final static float SPAWN_HEALTH_STATE = 20.0f;
  private final static IntavePlugin plugin = IntavePlugin.singletonInstance();
  private final static int LATENCY_JITTER_INTERVAL = 25;
  private final static double MAX_RELATIVE_MOVE_DIST = 3.5;
  private final Map<Class<? extends Action>, Action> actions;
  private final Movement movement;
  private final Player observer;
  private final User user;
  private final WrappedGameProfile wrappedGameProfile;
  private final EnumWrappers.NativeGameMode gameMode;
  private final int attributes;
  private final Consumer<FakePlayer> attackSubscriber;
  public double killAuraVL = 0;
  public long lastPingPacketSent;
  private int taskId;
  private int previousLatency = 0, ticks = 0;
  private long lastHurtAction;

  FakePlayer(
    Movement movement,
    Player observer,
    WrappedGameProfile profile,
    String tabListPrefix,
    String prefix,
    int entityId,
    int attributes,
    Consumer<FakePlayer> attackSubscriber
  ) {
    super(observer, entityId, attributes, profile, tabListPrefix, prefix);
    this.user = UserRepository.userOf(observer);
    this.movement = movement;
    this.wrappedGameProfile = profile;
    this.observer = observer;
    this.attributes = attributes;
    this.actions = loadActions();
    this.attackSubscriber = attackSubscriber;
    this.gameMode = EnumWrappers.NativeGameMode.SURVIVAL;
    user.meta().attackData().setFakePlayer(this);
  }

  public static Builder builderFor(Player observer) {
    return new Builder(observer);
  }

  private Map<Class<? extends Action>, Action> loadActions() {
    List<Action> actions = Lists.newArrayList(
      new SwingAnimationAction(observer, this),
      new HurtAnimationAction(observer, this)
    );
    if (hasAttribute(attributes, ITEM_IN_HAND)) {
      actions.add(new EquipmentHeldItemAction(observer, this));
    }
    if (hasAttribute(attributes, ARMORED)) {
      actions.add(new EquipmentArmorAction(observer, this));
    }
    Map<Class<? extends Action>, Action> actionMap = new HashMap<>();
    for (Action action : actions) {
      actionMap.put(action.getClass(), action);
    }
    return actionMap;
  }

  public void startTicking() {
    this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 0, 1);
  }

  public void stopTicking() {
    Bukkit.getScheduler().cancelTask(taskId);
  }

  public void create(Location spawn) {
    if (threadEscape(() -> create(spawn))) {
      return;
    }
    Preconditions.checkNotNull(spawn);
    this.movement.location = spawn;
    this.movement.botDistance = (movement.minBotDistance() + movement.maxBotDistance()) / 2;
    spawn(spawn);
    applyActions();
    updateHealthFor(observer(), this, SPAWN_HEALTH_STATE);
    applyDisplayName();
    latencyInitialize();
    startTicking();
  }

  private void applyActions() {
    int attributes = attributes();
    if (hasAttribute(attributes, ARMORED)) {
      immediateActionPerform(EquipmentArmorAction.class);
    }
    if (hasAttribute(attributes, ITEM_IN_HAND)) {
      immediateActionPerform(EquipmentHeldItemAction.class);
    }
  }

  public void remove() {
    if (threadEscape(this::remove)) {
      return;
    }
    stopTicking();
    despawn();
    user.meta().attackData().setFakePlayer(null);
  }

  public void respawn() {
    respawn(PositionRotationLookup.lookup(observer.getLocation(), ThreadLocalRandom.current().nextDouble(10)));
  }

  public void registerParentPlayerVelocity(double motionX, double motionY, double motionZ) {
    this.movement.velocityChanged = true;
    this.movement.velocityX = motionX * 4;
    this.movement.velocityY = motionY;
    this.movement.velocityZ = motionZ * 4;
  }

  public int nextLatency() {
    if (previousLatency == 0) {
      int latency = ThreadLocalRandom.current().nextInt(20, 250);
      this.previousLatency = latency;
      return latency;
    }
    int boundingLatency = ThreadLocalRandom.current().nextInt(
      previousLatency - LATENCY_JITTER_INTERVAL,
      previousLatency + LATENCY_JITTER_INTERVAL
    );
    int nextLatency = Math.max(LATENCY_JITTER_INTERVAL, boundingLatency);
    previousLatency = nextLatency;
    return nextLatency;
  }

  public void tick() {
    ticks++;
    try {
      tryPerformAllActions();
      processMovement();
      decreaseViolationLevel();
      double distanceMoved = movement.distanceMoved();
      double distanceToPlayer = movement.distanceToPlayer(observer);
      setSprinting(distanceMoved > 0.0 && !this.movement.sneaking);
      if (distanceMoved < 0.5 && distanceToPlayer < 9 && ticks != 0) {
        if (ThreadLocalRandom.current().nextInt(1, 10) % 5 == 0 && ticks % 250 == 0) {
          setSneaking(true);
        }
      } else if (distanceMoved > 1.0) {
        setSneaking(false);
      }
      if (this.ticks % 5 == 0 && distanceMoved > 0.0 && this.movement.onGround) {
        makeWalkingSound(this.movement.location);
      }
    } catch (Exception exception) {
      System.out.println(exception.getClass().getSimpleName() + " occurred in tick #" + ticks + " of # " + identifier());
      exception.printStackTrace();
    }
  }

  private void decreaseViolationLevel() {
    if (killAuraVL > 0) {
      killAuraVL -= 0.1;
    }
  }

  private void tryPerformAllActions() {
    for (Action action : this.actions.values()) {
      action.tryPerform();
    }
  }

  private void processMovement() {
    this.movement.applyMovementAndRotation(this.observer.getLocation());
    Location location = this.movement.location;
    Location prevLocation = this.movement.prevLocation;
    if (prevLocation != null) {
      boolean shouldTeleport = teleportRequired(location, prevLocation);
      boolean onGround = this.movement.onGround;
      if (shouldTeleport) {
        movementTeleport(location, onGround);
      } else {
        movementUpdate(location, prevLocation, onGround);
      }
    }
  }

  public void movementTeleport(Location to, boolean onGround) {
    super.movementTeleport(to, onGround);
    movement.registerTeleport(to);
    plugin.eventService()
      .feedback()
      .singleSynchronize(observer, to, (player, target) -> {
        User user = UserRepository.userOf(player);
        UserMetaAttackData attackData = user.meta().attackData();
        attackData.fakePlayerLastReportedX = target.getX();
        attackData.fakePlayerLastReportedY = target.getY();
        attackData.fakePlayerLastReportedZ = target.getZ();
      });
  }

  public void movementUpdate(Location to, Location prevLocation, boolean onGround) {
    super.movementUpdate(to, prevLocation, onGround);
    IntavePlugin.singletonInstance()
      .eventService().feedback()
      .singleSynchronize(observer, to, (player, target) -> {
        User user = UserRepository.userOf(player);
        UserMetaAttackData attackData = user.meta().attackData();
        attackData.fakePlayerLastReportedX = target.getX();
        attackData.fakePlayerLastReportedY = target.getY();
        attackData.fakePlayerLastReportedZ = target.getZ();
      });
  }

  public boolean teleportRequired(Location location1, Location location2) {
    if (location1.getWorld() != location2.getWorld()) {
      return true;
    }
    return safeDistance(location1, location2) > MAX_RELATIVE_MOVE_DIST;
  }

  public void setSprinting(boolean sprinting) {
    super.setSprinting(sprinting);
    this.movement.sprinting = sprinting;
  }

  public void setSneaking(boolean sneaking) {
    super.setSneaking(sneaking);
    this.movement.sneaking = sneaking;
  }

  public void onAttack() {
    this.movement.combatEvent();
    if (AccessHelper.now() - this.lastHurtAction > 500) {
      immediateActionPerform(HurtAnimationAction.class);
      this.lastHurtAction = AccessHelper.now();
    }
  }

  public void immediateActionPerform(
    Class<? extends Action> actionClass
  ) {
    Action action = actions.get(actionClass);
    if (action != null) {
      Synchronizer.synchronize(action::perform);
    }
  }

  public void moveOnTopOfPlayer() {
    this.movement.moveOnTopOfPlayerTime = AccessHelper.now();
  }

  public Movement movement() {
    return this.movement;
  }

  public Consumer<FakePlayer> attackSubscriber() {
    return attackSubscriber;
  }

  public WrappedGameProfile profile() {
    return wrappedGameProfile;
  }

  public EnumWrappers.NativeGameMode gameMode() {
    return gameMode;
  }

  private boolean threadEscape(Runnable apply) {
    if (Bukkit.isPrimaryThread()) {
      return false;
    }
    Synchronizer.synchronize(apply);
    return true;
  }

  public static class Builder {
    private final Player observer;
    private int identifier = -1;
    private WrappedGameProfile gameProfile;
    private String tabListPrefix = "";
    private String prefix = "";
    private Movement movement = null;
    private int attributes = 0;
    private Consumer<FakePlayer> attackSubscriber = fakePlayer -> {};

    Builder(Player observer) {
      this.observer = observer;
    }

    public Builder specifyIdentifier(int identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder prefixed(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public Builder tabPrefixed(String tabListPrefix) {
      this.tabListPrefix = tabListPrefix;
      return this;
    }

    public Builder floating() {
      this.movement = new FloatingMovement();
      return this;
    }

    public Builder walking() {
      this.movement = new WalkingMovement();
      return this;
    }

    public Builder acceptAttributes(int attributes) {
      this.attributes |= attributes;
      return this;
    }

    public Builder invisible() {
      this.attributes |= INVISIBLE;
      return this;
    }

    public Builder visible() {
      this.attributes &= ~INVISIBLE;
      return this;
    }

    public Builder invisibleInTabList() {
      this.attributes &= ~IN_TABLIST;
      return this;
    }

    public Builder visibleInTabList() {
      this.attributes |= IN_TABLIST;
      return this;
    }

    public Builder equipArmor() {
      this.attributes |= ARMORED;
      return this;
    }

    public Builder equipHeldItem() {
      this.attributes |= ITEM_IN_HAND;
      return this;
    }

    public Builder attackSubscribe(Consumer<FakePlayer> subscriber) {
      this.attackSubscriber = subscriber;
      return this;
    }

    public FakePlayer build() {
      if (movement == null) {
        floating();
      }
      if (identifier <= 0) {
        identifier = IdentifierReserve.acquireNew();
      }
      if (gameProfile == null) {
        gameProfile = acquireGameProfile();
      }
      return new FakePlayer(
        movement,
        observer,
        gameProfile,
        tabListPrefix,
        prefix,
        identifier,
        attributes,
        attackSubscriber
      );
    }
  }
}