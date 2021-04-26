package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.event.service.entity.ClientSideEntityService;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaAbilityData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class PlayerAbilityEvaluator implements PacketEventSubscriber {
  private final IntavePlugin plugin;

  public PlayerAbilityEvaluator(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "CAMERA")
    }
  )
  public void receiveCamera(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    Integer entityID = packet.getIntegers().read(0);
    plugin.eventService().transactionFeedbackService().requestPong(player, entityID, this::synchronizeCameraUpdate);
  }

  private void synchronizeCameraUpdate(Player player, int entityID) {
    User user = UserRepository.userOf(player);
    UserMetaAbilityData abilityData = user.meta().abilityData();
    Entity entity = ClientSideEntityService.serverEntityByIdentifier(player, entityID);
    abilityData.hasViewEntity = entity != player;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ABILITIES")
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

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ABILITIES")
    }
  )
  public void sentAbilities(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    Float walkingSpeed = packet.getFloat().readSafely(1);
    if (walkingSpeed != null) {
      plugin.eventService().transactionFeedbackService().requestPong(player, walkingSpeed, this::retrieveWalkingSpeed);
    }

    Float flySpeed = packet.getFloat().readSafely(0);
    if (flySpeed != null) {
      plugin.eventService().transactionFeedbackService().requestPong(player, flySpeed, this::retrieveFlyingSpeed);
    }

    Boolean allowedFlight = packet.getBooleans().read(2);
    if (allowedFlight != null) {
      plugin.eventService().transactionFeedbackService().requestPong(player, allowedFlight, this::retrieveAllowedFlight);
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

  private final static boolean BIT_FIELD = ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.BEE_UPDATE);

  private boolean requestedFlying(PacketContainer packet) {
    return packet.getBooleans().read(BIT_FIELD ? 0 : 1);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "GAME_STATE_CHANGE")
    }
  )
  public void updateGameMode(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaAbilityData abilityData = user.meta().abilityData();

    PacketContainer packet = event.getPacket();
    Integer gameState = packet.getIntegers().read(0);
    Float value = packet.getFloat().read(0);

    // GameType state must be 3
    if (gameState != 3) {
      return;
    }

    int gameTypeIdentifier = WrappedMathHelper.floor_float(value + 0.5F);
    GameMode gameMode = gameModeOf(gameTypeIdentifier);

    abilityData.setPendingGameMode(gameMode);
    plugin.eventService().transactionFeedbackService().requestPong(
      player, gameMode,
      ((player1, target) -> UserRepository.userOf(player1).meta().abilityData().setGameMode(target))
    );
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
    SPECTATOR(3)
    ;

    private final int id;

    GameMode(int id) {
      this.id = id;
    }

    public int id() {
      return id;
    }
  }
}