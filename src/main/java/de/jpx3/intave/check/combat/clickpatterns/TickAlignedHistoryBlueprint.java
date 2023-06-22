package de.jpx3.intave.check.combat.clickpatterns;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.Blueprint;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.DROP_ITEM;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public abstract class TickAlignedHistoryBlueprint<E extends TickAlignedMeta> extends Blueprint<ClickPatterns, TickAlignedMeta, E> {
  private int historyLength = 80;

  public TickAlignedHistoryBlueprint(ClickPatterns parentCheck, Class<? extends E> metaClass) {
    super(parentCheck, metaClass);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY, ARM_ANIMATION, BLOCK_DIG
    }
  )
  public final void clientClickUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);

    TickAlignedMeta meta = metaOf(user);

    PacketContainer packet = event.getPacket();
    PacketType type = packet.getType();
    if (type == PacketType.Play.Client.USE_ENTITY) {
      EntityUseReader reader = PacketReaders.readerOf(packet);
      EnumWrappers.EntityUseAction entityUseAction = reader.useAction();
      if (entityUseAction == EnumWrappers.EntityUseAction.ATTACK) {
        meta.attacks++;
      }
      reader.release();
    } else if (type == PacketType.Play.Client.ARM_ANIMATION) {
      meta.clicks++;
    } else if (type == PacketType.Play.Client.BLOCK_DIG) {
      EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);
//      if (digType == )
      if (digType == DROP_ITEM && user.meta().inventory().heldItemType() == Material.AIR) {

      } else {
        meta.breakingBlock = user.meta().attack().inBreakProcess;
        meta.places++;
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public final void clientTickUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);

    TickAlignedMeta meta = metaOf(user);
    TickAction action = TickAction.NOTHING;
    int intensity = 0;

    if (meta.clicks > 0) {
      action = TickAction.CLICK;
      intensity = meta.clicks;
    }
    if (meta.attacks > 0) {
      action = TickAction.ATTACK;
      intensity = meta.attacks;
    } else if (meta.places > 0) {
      action = TickAction.PLACE;
      intensity = meta.places;
    }
    append(user, action, intensity);
    meta.attacks = 0;
    meta.clicks = 0;
    meta.places = 0;
    meta.tickCount++;

    if (meta.tickCount % meta.historyLength == 0) {
      analyzeClicks(user, metaOf(user));
    }
  }

  public abstract void analyzeClicks(User user, E meta);

  public final void flag(User user, String message) {
    Violation violation = Violation.builderFor(ClickPatterns.class)
      .forPlayer(user.player()).withDefaultThreshold()
      .withMessage(message).withDetails("pattern: "+buildHistoryString(user))
      .withVL(0).build();
    Modules.violationProcessor().processViolation(violation);
  }

  private String buildHistoryString(User user) {
    TickAlignedMeta meta = metaOf(user);
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < meta.historyLength; i++) {
      TickAction action = meta.tickActions.get(i);
      int intensity = meta.tickIntensity.get(i);
      builder.append(action.repChar());//.append(intensity);
      if (intensity > 1) {
        builder.append("!").append(intensity);
      }
    }
    return builder.toString();
  }

  public final void append(User user, TickAction action, int intensity) {
    TickAlignedMeta baseMeta = metaOf(user);

    if (action == TickAction.NOTHING) {
      baseMeta.breakingBlock = false;
    }
    Boolean inBlockBreak = baseMeta.inBlockBreak.remove(0);
    baseMeta.inBlockBreak.add(baseMeta.breakingBlock);
    TickAction removed = baseMeta.tickActions.remove(0);

//    if (removed == TickAction.NOTHING || inBlockBreak) {
//    } else {
//    }
//    baseMeta.streakLength.remove(0);
//    if (action == TickAction.NOTHING) {
//      baseMeta.streakLength.add(currentClickStreak > 4 ? currentClickStreak : 0);
//      currentClickStreak = 0;
//    } else {
//      baseMeta.streakLength.add(0);
//      currentClickStreak++;
//    }
    baseMeta.tickActions.add(action);
    baseMeta.tickIntensity.remove(0);
    baseMeta.tickIntensity.add(intensity);
  }

  public enum TickAction {
    NOTHING('_'),
    CLICK('C'),
    ATTACK('A'),
    PLACE('P'),
    ;
    private final char representation;

    TickAction(char representation) {
      this.representation = representation;
    }

    public char repChar() {
      return representation;
    }
  }
}

