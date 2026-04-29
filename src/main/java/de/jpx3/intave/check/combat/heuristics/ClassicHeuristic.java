package de.jpx3.intave.check.combat.heuristics;

import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

public class ClassicHeuristic<M extends CheckCustomMetadata> extends MetaCheckPart<Heuristics, M> {
  protected final Heuristics parentCheck;
  protected final String nerfId;
  private final HeuristicsClassicType type;
  private final int violationLevelIncrease;

  protected ClassicHeuristic(Heuristics parentCheck, HeuristicsClassicType type, Class<? extends M> metaClass) {
    super(parentCheck, metaClass);
    this.parentCheck = parentCheck;
    this.type = type;
    this.violationLevelIncrease = parentCheck.classicViolationLevelMap().get(type);
    this.nerfId = type.verboseName();
  }

  protected void flag(Player player, String details) {
    Violation violation = Violation.builderFor(Heuristics.class)
      .forPlayer(player).withMessage("failed " + type.verboseName())
      .withDetails(details)
      .withVL(violationLevelIncrease)
      .withCustomThreshold("classic.thresholds")
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  @Override
  public boolean enabled() {
    return violationLevelIncrease > 0;
  }
}
