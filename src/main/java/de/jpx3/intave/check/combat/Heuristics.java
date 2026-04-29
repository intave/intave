package de.jpx3.intave.check.combat;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckConfiguration;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.*;
import de.jpx3.intave.check.combat.heuristics.detect.clickpatterns.OldAirClickLimitHeuristic;
import de.jpx3.intave.check.combat.heuristics.clickpatterns.SwingDeviationHeuristics;
import de.jpx3.intave.check.combat.heuristics.clickpatterns.SwingLimitHeuristics;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.AttackRequiredHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.BaritoneRotationCheck;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.PreAttackHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.SameRotationHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy.AccuracyHitboxCornerHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy.AccuracyLongTermHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy.AccuracyLongTermRelayHeuristic;
import de.jpx3.intave.check.combat.heuristics.experimental.RotationPrevisionDetermination;
import de.jpx3.intave.check.combat.heuristics.experimental.RotationPrevisionFluctuation;
import de.jpx3.intave.check.combat.heuristics.inventory.PacketInventoryHeuristic;
import de.jpx3.intave.check.combat.heuristics.other.*;
import de.jpx3.intave.check.combat.heuristics.testing.TestingHeuristic;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.violation.Violation;
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
    appendCheckPart(new OldAirClickLimitHeuristic(this));
//        appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.other.AttackReduceIgnoreHeuristic");
    appendCheckPart(new RotationStandardDeviationHeuristic(this));
    appendCheckPart(new RotationStandardDeviationRelayHeuristic(this));
    appendCheckPart(new RotationSnapHeuristic(this));
    appendCheckPart(new AccuracyLongTermHeuristic(this));
    appendCheckPart(new AccuracyLongTermRelayHeuristic(this));
    appendCheckPart(new ReshapedJumpHeuristic(this));
    appendCheckPart(new RotationAccuracyYawHeuristic(this));
    appendCheckPart(new RotationAccuracyPitchHeuristic(this));
    appendCheckPart(new AccuracyHitboxCornerHeuristic(this));
    appendCheckPart(new RotationSensitivityHeuristic(this));
    appendCheckPart(new RotationModuloResetHeuristic(this));
    appendCheckPart(new PreAttackHeuristic(this));

    appendCheckPart(new SameRotationHeuristic(this));
    appendCheckPart(new AttackRequiredHeuristic(this));
    appendCheckPart(new PacketOrderHeuristic(this));
    appendCheckPart(new BaritoneRotationCheck(this));
    appendCheckPart(new ToolSwitchHeuristic(this));

    // for testing
    appendCheckPart(new RotationPrevisionFluctuation(this));
    appendCheckPart(new TestingHeuristic(this));

    // Lucky experimental heuristics
    appendCheckPart(new RotationPrevisionDetermination(this));
    appendCheckPart(new SwingLimitHeuristics(this));
    appendCheckPart(new SwingDeviationHeuristics(this));
//    appendCheckPart(new RotationAngleHeuristic(this));

    appendCheckPart(new PacketOrderSwingHeuristic(this));
    appendCheckPart(new PacketPlayerActionToggleHeuristic(this));
    appendCheckPart(new PacketInventoryHeuristic(this));
    appendCheckPart(new BlockingHeuristic(this));
    appendCheckPart(new AttackInInvalidStateHeuristic(this));
    appendCheckPart(new NoSwingHeuristic(this));
    appendCheckPart(new DoubleEntityActionHeuristic(this));
    appendCheckPart(new SprintOnAttackHeuristic(this));
    appendCheckPart(new JumpVelocityHeuristic(this));
    appendCheckPart(new CivbreakHeuristic(this));
    appendCheckPart(new InvalidFlyingPacketHeuristic(this));
  }

  private void loadClassicConfiguration() {
    CheckConfiguration.CheckSettings settings = configuration().settings();
    for (HeuristicsClassicType classType : HeuristicsClassicType.values()) {
      String fullConfigurationName = "classic." + classType.configurationName();
      int violationLevelIncrease = settings.intInBoundsBy(fullConfigurationName, 0, Integer.MAX_VALUE);
      classicViolationLevelMap.put(classType, violationLevelIncrease);
    }
  }

  public int classicFlag(Player player, HeuristicsClassicType type, String details) {
    return violationLevelIncrease;
  }

  public void cloudFlag(Player player, String details) {
    // soon:TM:
  }

  public Map<HeuristicsClassicType, Integer> classicViolationLevelMap() {
    return classicViolationLevelMap;
  }
}