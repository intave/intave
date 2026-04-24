package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.EffectMetadata;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.HIGH;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_EFFECT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.REMOVE_ENTITY_EFFECT;

public final class EffectTracker extends Module {
  public static final int POTION_EFFECT_SPEED = 1;
  public static final int POTION_EFFECT_SLOWNESS = 2;
  public static final int POTION_EFFECT_JUMP_BOOST = 8;

  private final boolean EFFECT_ACCESS = MinecraftVersions.VER1_9_0.atOrAbove();

  @PacketSubscription(
    priority = HIGH,
    packetsOut = ENTITY_EFFECT
  )
  public void sentEffect(
    User user, Player player,
    WrapperPlayServerEntityEffect packet
  ) {
    int entityId = packet.getEntityId();
    if (entityId != player.getEntityId()) {
      return;
    }
    PotionEffectOutput effectOutput = new PotionEffectOutput(
      effectTypeId(packet.getPotionType()),
      packet.getEffectAmplifier(),
      packet.getEffectDurationTicks()
    );
    user.tickFeedback(() -> receiveEffect(player, effectOutput));
  }

  @PacketSubscription(
    priority = HIGH,
    packetsOut = {
      REMOVE_ENTITY_EFFECT
    }
  )
  public void sentRemoveEffect(ProtocolPacketEvent event, WrapperPlayServerRemoveEntityEffect packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    int entityId = packet.getEntityId();
    if (entityId != player.getEntityId()) {
      return;
    }
    PotionEffectType potionEffectType = SpigotConversionUtil.toBukkitPotionEffectType(packet.getPotionType());
    user.tickFeedback(() -> receiveEffectRemoval(player, potionEffectType));
  }

  private void receiveEffectRemoval(Player player, PotionEffectType potionEffectType) {
    User user = UserRepository.userOf(player);
    EffectMetadata potionData = user.meta().potions();
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
    EffectMetadata potionData = user.meta().potions();

    int effectAmplifier = effectOutput.potionEffectAmplifier;
    int effectDuration = effectOutput.potionEffectDuration;

    boolean infiniteEffectsAllowed = user.meta().protocol().protocolVersion() >= 763;
    if (effectDuration == -1 && infiniteEffectsAllowed) {
      effectDuration = Integer.MAX_VALUE;
    }

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

  private int effectTypeId(PotionType potionType) {
    if (potionType == PotionTypes.SPEED) {
      return POTION_EFFECT_SPEED;
    }
    if (potionType == PotionTypes.SLOWNESS) {
      return POTION_EFFECT_SLOWNESS;
    }
    if (potionType == PotionTypes.JUMP_BOOST) {
      return POTION_EFFECT_JUMP_BOOST;
    }
    return -1;
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
