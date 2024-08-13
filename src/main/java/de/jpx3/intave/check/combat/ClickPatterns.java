package de.jpx3.intave.check.combat;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.combat.clickpatterns.*;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_13;

@Relocate
public final class ClickPatterns extends Check {
  private static final double MAX_VL_DEDUCTION_PER_MINUTE = 16;
  private final CheckViolationLevelDecrementer decrementer;

  public ClickPatterns() {
    super("ClickPatterns", "clickpatterns");
    setupCheckParts();
    decrementer = new CheckViolationLevelDecrementer(this, MAX_VL_DEDUCTION_PER_MINUTE /* vl */ / /* per */ 60d /* minute*/);
  }

  private void setupCheckParts() {
    appendCheckParts(
      new Deviation(this),
      new Entropy(this),
      new Fluctuation(this),
      new Repetitive(this),
      new Kurtosis(this)
    );
    appendCheckParts(
      new EqualDelay(this),
      new Bursts(this)
    );
  }

  @PacketSubscription(
    packetsIn = ARM_ANIMATION
  )
  public void receiveSwing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    decrementer.decrement(user, (MAX_VL_DEDUCTION_PER_MINUTE / 32) / 60d);
  }

  public void makeDetection(Player player, String details, String specifics, double vl) {
    User user = userOf(player);
    // Disable auto-clicker checks for players on 1.13 or higher due to integrated auto-clicker causing false flags
    if (user.protocolVersion() >= VER_1_13) {
      return;
    }
    if (IntaveControl.CLICKPATTERNS_OUTPUT || IntaveControl.GOMME_MODE) {
      details += " " + specifics.trim();
    }
    Violation violation = Violation.builderFor(ClickPatterns.class)
      .forPlayer(player).withMessage("clicks suspiciously").withDetails(details)
      .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES).withVL(vl).withDefaultThreshold().build();
    double vlAfter = Modules.violationProcessor().processViolation(violation).violationLevelAfter();
    if (vlAfter > 50) {
      user.nerf(RECEIVE_MORE_KNOCKBACK, "75");
    }
  }
}

