package de.jpx3.intave.detect.checks.world;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.detect.checks.world.placementanalysis.PlacementInvalidFacingPattern;
import de.jpx3.intave.event.service.violation.Violation;
import org.bukkit.entity.Player;

public final class PlacementAnalysis extends IntaveCheck {
  private final IntavePlugin plugin;

  public PlacementAnalysis(IntavePlugin plugin) {
    super("PlacementAnalysis", "placementanalysis");
    this.plugin = plugin;
    this.setupSubChecks();
  }

  public void processViolation(Player player) {
    Violation violation = Violation.fromType(PlacementAnalysis.class)
      .withPlayer(player).withMessage("suspicious block-placement")
      .withDefaultThreshold().withVL(1)
      .build();
    plugin.violationProcessor().processViolation(violation);
  }

  public void setupSubChecks() {
    appendCheckPart(new PlacementInvalidFacingPattern(this));
  }
}