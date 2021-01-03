package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaPotionData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class PotionEffectEvaluator implements PacketEventSubscriber {
  private final IntavePlugin plugin;

  public PotionEffectEvaluator(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "REMOVE_ENTITY_EFFECT")
    }
  )
  public void sentRemoveEffect(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    Entity entity = packet.getEntityModifier(event).read(0);
    if (!Objects.equals(entity, player)) {
      return;
    }

    Integer potionEffectType = packet.getIntegers().readSafely(1);
    if (potionEffectType == null) {
      potionEffectType = 0;
    }
    plugin.eventService().transactionFeedbackService().requestPong(player, potionEffectType, this::receiveEffectRemoval);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ENTITY_EFFECT")
    }
  )
  public void sentEffect(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    Entity entity = packet.getEntityModifier(event).read(0);
    if (!Objects.equals(entity, player)) {
      return;
    }

    Byte potionEffectTypeIdentifier = packet.getBytes().readSafely(0);
    Byte potionEffectAmplifier = packet.getBytes().readSafely(1);
    Integer potionEffectDuration = packet.getIntegers().readSafely(1);

    if (potionEffectTypeIdentifier == null) {
      potionEffectTypeIdentifier = 0;
    }
    if (potionEffectAmplifier == null) {
      potionEffectAmplifier = 0;
    }
    if (potionEffectDuration == null) {
      potionEffectDuration = 0;
    }

    PotionEffectOutput effectOutput = new PotionEffectOutput(
      potionEffectTypeIdentifier,
      potionEffectAmplifier,
      potionEffectDuration
    );
    plugin.eventService().transactionFeedbackService().requestPong(player, effectOutput, this::receiveEffect);
  }

  private void receiveEffectRemoval(Player player, int potionEffectType) {
    User user = UserRepository.userOf(player);
    UserMetaPotionData potionData = user.meta().potionData();
    switch (potionEffectType) {
      // 1 = speed
      case 1: {
        potionData.potionEffectSpeedAmplifier(0);
        potionData.potionEffectSpeedDuration = 0;
        break;
      }
      // 2 = slowness
      case 2: {
        potionData.potionEffectSlownessAmplifier(0);
        potionData.potionEffectSlownessDuration = 0;
        break;
      }
      // 8 = jump boost
      case 8: {
        potionData.potionEffectJumpAmplifier(0);
        potionData.potionEffectJumpDuration = 0;
        break;
      }
    }
  }

  private void receiveEffect(Player player, PotionEffectOutput effectOutput) {
    User user = UserRepository.userOf(player);
    UserMetaPotionData potionData = user.meta().potionData();

    int effectAmplifier = effectOutput.potionEffectAmplifier;
    int effectDuration = effectOutput.potionEffectDuration;

    switch (effectOutput.potionEffectType) {
      // 1 = speed
      case 1: {
        potionData.potionEffectSpeedAmplifier(effectAmplifier + 1);
        potionData.potionEffectSpeedDuration = effectDuration - 1;
        break;
      }
      // 2 = slowness
      case 2: {
        potionData.potionEffectSlownessAmplifier(effectAmplifier + 1);
        potionData.potionEffectSlownessDuration = effectDuration - 1;
        break;
      }
      // 8 = jump boost
      case 8: {
        potionData.potionEffectJumpAmplifier(effectAmplifier);
        potionData.potionEffectJumpDuration = effectDuration - 1;
        break;
      }
    }
  }

  private static class PotionEffectOutput {
    private final int potionEffectType;
    private final int potionEffectAmplifier;
    private final int potionEffectDuration;

    public PotionEffectOutput(
      int potionEffectType,
      int potionEffectAmplifier,
      int potionEffectDuration
    ) {
      this.potionEffectType = potionEffectType;
      this.potionEffectAmplifier = potionEffectAmplifier;
      this.potionEffectDuration = potionEffectDuration;
    }
  }
}