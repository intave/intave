package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.event.entity.ClientSideEntityService;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketEventSubscriber;
import de.jpx3.intave.event.packet.PacketId;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaAbilityData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

import static de.jpx3.intave.event.packet.PacketId.Server.CAMERA;
import static de.jpx3.intave.event.packet.PacketId.Server.GAME_STATE_CHANGE;

public final class PlayerAbilityEvaluator implements PacketEventSubscriber {
  private final IntavePlugin plugin;

  public PlayerAbilityEvaluator(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    packetsOut = {
      CAMERA
    }
  )
  public void receiveCamera(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    Integer entityID = packet.getIntegers().read(0);
    plugin.eventService().feedback().singleSynchronize(player, entityID, this::synchronizeCameraUpdate);
  }

  private void synchronizeCameraUpdate(Player player, int entityID) {
    User user = UserRepository.userOf(player);
    UserMetaAbilityData abilityData = user.meta().abilityData();
    Entity entity = ClientSideEntityService.serverEntityByIdentifier(player, entityID);
    abilityData.hasViewEntity = entity != player;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PacketId.Client.ABILITIES
    }
  )
  public void receiveAbilities(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();

    UserMetaAbilityData abilityData = user.meta().abilityData();
    UserMetaMovementData movementData = user.meta().movementData();

    boolean flying = requestedFlying(packet);
    if (abilityData.allowFlying()) {
      if (flying) {
        abilityData.setFlying(true);
      } else {
        movementData.disabledFlying = true;
      }
    }
  }

  private final static boolean BIT_FIELD = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_16_0);

  private boolean requestedFlying(PacketContainer packet) {
    return packet.getBooleans().read(BIT_FIELD ? 0 : 1);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      PacketId.Server.ABILITIES
    }
  )
  public void sentAbilities(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    Float walkingSpeed = packet.getFloat().readSafely(1);
    if (walkingSpeed != null) {
      plugin.eventService().feedback().singleSynchronize(player, walkingSpeed, this::retrieveWalkingSpeed);
    }

    Float flySpeed = packet.getFloat().readSafely(0);
    if (flySpeed != null) {
      plugin.eventService().feedback().singleSynchronize(player, flySpeed, this::retrieveFlyingSpeed);
    }

    Boolean allowedFlight = packet.getBooleans().read(2);
    if (allowedFlight != null) {
      plugin.eventService().feedback().singleSynchronize(player, allowedFlight, this::retrieveAllowedFlight);
    }
  }

  private void retrieveWalkingSpeed(Player player, float walkSpeed) {
    User user = UserRepository.userOf(player);
    UserMetaAbilityData abilityData = user.meta().abilityData();
    abilityData.setWalkSpeed(walkSpeed);
  }

  private void retrieveFlyingSpeed(Player player, float flySpeed) {
    User user = UserRepository.userOf(player);
    UserMetaAbilityData abilityData = user.meta().abilityData();
    abilityData.setFlySpeed(flySpeed);
  }

  private void retrieveAllowedFlight(Player player, boolean allowedFlight) {
    User user = UserRepository.userOf(player);
    UserMetaAbilityData abilityData = user.meta().abilityData();
    abilityData.setAllowFlying(allowedFlight);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsOut = {
      GAME_STATE_CHANGE
    }
  )
  public void updateGameMode(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    UserMetaAbilityData abilityData = user.meta().abilityData();
    if (!gameModeUpdateState(packet)) {
      return;
    }
    Float value = packet.getFloat().read(0);
    int gameTypeIdentifier = WrappedMathHelper.floor_float(value + 0.5F);
    GameMode gameMode = gameModeOf(gameTypeIdentifier);
    abilityData.setPendingGameMode(gameMode);
    plugin.eventService().feedback().singleSynchronize(
      player, gameMode,
      ((player1, target) -> UserRepository.userOf(player1).meta().abilityData().setGameMode(target))
    );
  }

  private final static boolean NEW_GAME_STATE_CHANGE_PACKET = MinecraftVersions.VER1_16_0.atOrAbove();
  private final static Class<?> GAME_STATE_CLASS = !NEW_GAME_STATE_CHANGE_PACKET ? null : ReflectiveAccess.lookupServerClass("PacketPlayOutGameStateChange$a");

  private boolean gameModeUpdateState(PacketContainer packet) {
    if (NEW_GAME_STATE_CHANGE_PACKET) {
      try {
        Object obj = packet.getModifier().withType(GAME_STATE_CLASS).read(0);
        Field field = obj.getClass().getDeclaredField("b");
        field.setAccessible(true);
        return (int) field.get(obj) == 3;
      } catch (Exception e) {
        e.printStackTrace();
        return false;
      }
    } else {
      return packet.getIntegers().read(0) == 3;
    }
  }

  private GameMode gameModeOf(int id) {
    for (GameMode value : GameMode.values()) {
      if (value.id == id) {
        return value;
      }
    }
    throw new IllegalStateException("Unable to resolve GameMode with id " + id);
  }

  public enum GameMode {
    NOT_SET(-1),
    SURVIVAL(0),
    CREATIVE(1),
    ADVENTURE(2),
    SPECTATOR(3);

    private final int id;

    GameMode(int id) {
      this.id = id;
    }

    public int id() {
      return id;
    }
  }
}