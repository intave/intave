package de.jpx3.intave.check.combat;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckConfiguration;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.AttackRequiredHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.PreAttackHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy.AccuracyHitboxCornerHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy.AccuracyLongTermHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.*;
import de.jpx3.intave.check.combat.heuristics.inventory.PacketInventoryHeuristic;
import de.jpx3.intave.check.combat.heuristics.other.*;
import de.jpx3.intave.check.combat.heuristics.testing.TestingHeuristic;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class Heuristics extends Check {
  private final Map<HeuristicsClassicType, Integer> classicViolationLevelMap = new HashMap<>();

  public Heuristics(IntavePlugin plugin) {
    super("Heuristics", "heuristics");
    this.setupClassicHeuristics();
    this.loadClassicConfiguration();
  }

  private void setupClassicHeuristics() {
    // for testing
    appendCheckPart(new TestingHeuristic(this));

    appendCheckPart(new RotationStandardDeviationHeuristic(this));
    appendCheckPart(new RotationSnapHeuristic(this));
    appendCheckPart(new AccuracyLongTermHeuristic(this));
    appendCheckPart(new RotationAccuracyYawHeuristic(this));
    appendCheckPart(new RotationAccuracyPitchHeuristic(this));
    appendCheckPart(new AccuracyHitboxCornerHeuristic(this));
    appendCheckPart(new RotationSensitivityHeuristic(this));
    appendCheckPart(new RotationModuloResetHeuristic(this));
    appendCheckPart(new PreAttackHeuristic(this));

    appendCheckPart(new AttackRequiredHeuristic(this));
    appendCheckPart(new ToolSwitchHeuristic(this));

    appendCheckPart(new PacketOrderSwingHeuristic(this));
    appendCheckPart(new PacketPlayerActionToggleHeuristic(this));
    appendCheckPart(new PacketInventoryHeuristic(this));
    appendCheckPart(new BlockingHeuristic(this));
    appendCheckPart(new NoSwingHeuristic(this));
    appendCheckPart(new CivbreakHeuristic(this));
  }

  private void loadClassicConfiguration() {
    CheckConfiguration.CheckSettings settings = configuration().settings();
    for (HeuristicsClassicType classType : HeuristicsClassicType.values()) {
      String fullConfigurationName = "classic." + classType.configurationName();
      int violationLevelIncrease = settings.intInBoundsBy(fullConfigurationName, 0, Integer.MAX_VALUE);
      classicViolationLevelMap.put(classType, violationLevelIncrease);
    }
  }

  public void cloudFlag(Player player, String details) {
    // soon:TM:
  }

  public Map<HeuristicsClassicType, Integer> classicViolationLevelMap() {
    return classicViolationLevelMap;
  }
}