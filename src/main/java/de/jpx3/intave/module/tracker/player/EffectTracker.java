package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityEffectReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.EffectMetadata;
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

  private PotionEffectType effectIdOf(PacketContainer packet) {
    if (EFFECT_ACCESS) {
      return packet.getEffectTypes().read(0);
    } else {
      return PotionEffectType.getById(packet.getIntegers().read(1));
    }
  }

  @PacketSubscription(
    priority = HIGH,
    packetsOut = ENTITY_EFFECT
  )
  public void sentEffect(
    User user, Player player,
    EntityEffectReader reader
  ) {
    int entityId = reader.entityId();
    if (entityId != player.getEntityId()) {
      return;
    }
    PotionEffectOutput effectOutput = new PotionEffectOutput(
      reader.effectType(),
      reader.effectAmplifier(),
      reader.effectDuration()
    );
    user.tickFeedback(() -> receiveEffect(player, effectOutput));
  }

  @PacketSubscription(
    priority = HIGH,
    packetsOut = {
      REMOVE_ENTITY_EFFECT
    }
  )
  public void sentRemoveEffect(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    if (entityId != player.getEntityId()) {
      return;
    }
    PotionEffectType potionEffectType = effectIdOf(packet);
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