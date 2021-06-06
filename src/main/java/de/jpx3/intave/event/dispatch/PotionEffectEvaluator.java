package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketEventSubscriber;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaPotionData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;

import static de.jpx3.intave.event.packet.PacketId.Server.ENTITY_EFFECT;
import static de.jpx3.intave.event.packet.PacketId.Server.REMOVE_ENTITY_EFFECT;

public final class PotionEffectEvaluator implements PacketEventSubscriber {
  public static final int POTION_EFFECT_SPEED = 1;
  public static final int POTION_EFFECT_SLOWNESS = 2;
  public static final int POTION_EFFECT_JUMP_BOOST = 8;
  private final IntavePlugin plugin;

  public PotionEffectEvaluator(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      REMOVE_ENTITY_EFFECT
    }
  )
  public void sentRemoveEffect(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    Entity entity = packet.getEntityModifier(event).read(0);
    if (!Objects.equals(entity, player)) {
      return;
    }
    PotionEffectType potionEffectType = effectIdOf(packet);
    plugin.eventService().feedback().singleSynchronize(player, potionEffectType, this::receiveEffectRemoval);
  }

  private final static boolean EFFECT_ACCESS = MinecraftVersions.VER1_9_0.atOrAbove();

  private PotionEffectType effectIdOf(PacketContainer packet) {
    if (EFFECT_ACCESS) {
      return packet.getEffectTypes().read(0);
    } else {
      return PotionEffectType.getById(packet.getIntegers().read(1));
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_EFFECT
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
    plugin.eventService().feedback().singleSynchronize(player, effectOutput, this::receiveEffect);
  }

  private void receiveEffectRemoval(Player player, PotionEffectType potionEffectType) {
    User user = UserRepository.userOf(player);
    UserMetaPotionData potionData = user.meta().potionData();
    if (potionEffectType.equals(PotionEffectType.SPEED)) {
      potionData.potionEffectSpeedAmplifier(0);
      potionData.potionEffectSpeedDuration = 0;
    } else if (potionEffectType.equals(PotionEffectType.SLOW)) {
      potionData.potionEffectSlownessAmplifier(0);
      potionData.potionEffectSlownessDuration = 0;
    } else if (potionEffectType.equals(PotionEffectType.JUMP)) {
      potionData.potionEffectJumpAmplifier(0);
      potionData.potionEffectJumpDuration = 0;
    }
  }

  private void receiveEffect(Player player, PotionEffectOutput effectOutput) {
    User user = UserRepository.userOf(player);
    UserMetaPotionData potionData = user.meta().potionData();

    int effectAmplifier = effectOutput.potionEffectAmplifier;
    int effectDuration = effectOutput.potionEffectDuration;

    switch (effectOutput.potionEffectType) {
      case POTION_EFFECT_SPEED: {
        potionData.potionEffectSpeedAmplifier(effectAmplifier + 1);
        potionData.potionEffectSpeedDuration = effectDuration - 1;
        break;
      }
      case POTION_EFFECT_SLOWNESS: {
        potionData.potionEffectSlownessAmplifier(effectAmplifier + 1);
        potionData.potionEffectSlownessDuration = effectDuration - 1;
        break;
      }
      case POTION_EFFECT_JUMP_BOOST: {
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