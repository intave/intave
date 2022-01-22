package de.jpx3.intave.check.combat;

import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.combat.clickpatterns.LowDeviationClickPattern;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.violation.Violation;
import org.bukkit.entity.Player;

public final class ClickPatterns extends Check {
  public ClickPatterns() {
    super("ClickPatterns", "clickpatterns");
  }

  private void setupCheckParts() {
    appendCheckParts(
      new LowDeviationClickPattern(this)
    );
  }

  public void makeDetection(Player player, String details, double vl) {
    Violation violation = Violation.builderFor(ClickPatterns.class)
      .forPlayer(player).withMessage("clicks suspiciously").withDetails(details)
      .withVL(vl).withDefaultThreshold().build();
    Modules.violationProcessor().processViolation(violation);
  }
}

