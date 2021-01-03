package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaAbilityData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

public final class PlayerAbilityEvaluator implements PacketEventSubscriber {
  private final IntavePlugin plugin;

  public PlayerAbilityEvaluator(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
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
        abilityData.flying(true);
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
    abilityData.walkSpeed(walkSpeed);
  }

  private void retrieveFlyingSpeed(Player player, float flySpeed) {
    User user = UserRepository.userOf(player);
    UserMetaAbilityData abilityData = user.meta().abilityData();
    abilityData.flySpeed(flySpeed);
  }

  private void retrieveAllowedFlight(Player player, boolean allowedFlight) {
    User user = UserRepository.userOf(player);
    UserMetaAbilityData abilityData = user.meta().abilityData();
    abilityData.allowFlying(allowedFlight);
  }

  private final static boolean BIT_FIELD = ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.BEE_UPDATE);

  private boolean requestedFlying(PacketContainer packet) {
    return packet.getBooleans().read(BIT_FIELD ? 0 : 1);
  }
}