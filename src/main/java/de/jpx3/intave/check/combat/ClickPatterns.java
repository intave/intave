package de.jpx3.intave.check.combat;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.combat.clickpatterns.Kurtosis;
import de.jpx3.intave.check.combat.clickpatterns.Skewness;
import de.jpx3.intave.check.combat.clickpatterns.Spikes;
import de.jpx3.intave.check.combat.clickpatterns.Variance;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.violation.Violation;
import org.bukkit.entity.Player;

public final class ClickPatterns extends Check {
  public ClickPatterns() {
    super("ClickPatterns", "clickpatterns");
    setupCheckParts();
  }

  private void setupCheckParts() {
    appendCheckParts(
      new Variance(this),
      new Skewness(this),
      new Spikes(this),
      new Kurtosis(this)
    );
  }

  public void makeDetection(Player player, String details, String specifics, double vl) {
    if (IntaveControl.CLICKPATTERNS_OUTPUT) {
      details += " " + specifics.trim();
    }
    Violation violation = Violation.builderFor(ClickPatterns.class)
      .forPlayer(player).withMessage("clicks suspiciously").withDetails(details)
      .withVL(vl).withDefaultThreshold().build();
    Modules.violationProcessor().processViolation(violation);
  }
}

