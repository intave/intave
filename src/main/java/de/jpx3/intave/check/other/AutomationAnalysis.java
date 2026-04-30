package de.jpx3.intave.check.other;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.other.automationanalysis.PitchLockedPathingCheck;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.FLYING;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class AutomationAnalysis extends Check {
  private static final double MAX_VL_DEDUCTION_PER_MINUTE = 16.0d;
  private final CheckViolationLevelDecrementer decrementer;

  public AutomationAnalysis(IntavePlugin plugin) {
    super("AutomationAnalysis", "automationanalysis");
    decrementer = new CheckViolationLevelDecrementer(this, MAX_VL_DEDUCTION_PER_MINUTE / 60d);
    appendCheckPart(new PitchLockedPathingCheck(this));
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    decrementer.decrement(userOf(player), (MAX_VL_DEDUCTION_PER_MINUTE / 20) / 60d);
  }
}
