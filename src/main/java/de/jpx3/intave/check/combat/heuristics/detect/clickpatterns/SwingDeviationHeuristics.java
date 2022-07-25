package de.jpx3.intave.check.combat.heuristics.detect.clickpatterns;

import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;

import java.util.List;

import static de.jpx3.intave.check.combat.heuristics.detect.clickpatterns.SwingDeviationHeuristics.SwingDeviationBlueprintMeta;

@Reserved
public final class SwingDeviationHeuristics extends SwingBlueprint<SwingDeviationBlueprintMeta> {
  public SwingDeviationHeuristics(Heuristics parentCheck) {
    super(parentCheck, SwingDeviationBlueprintMeta.class, 100, true, false);
  }

  @Override
  public void check(User user, List<Integer> delays) {
    SwingDeviationBlueprintMeta meta = metaOf(userOf(user.player()));
    double deviation = standardDeviation(delays);
    if (deviation < 0.45) {
      if (++meta.vl >= 3) {
        String description = String.format("clicking with a low deviation %.2f vl: %.2f", deviation, meta.vl);
        Anomaly anomaly = Anomaly.anomalyOf("310", Confidence.NONE, Anomaly.Type.AUTOCLICKER, description);
        parentCheck().saveAnomaly(user.player(), anomaly);
        //dmc30
        user.applyAttackNerfer(AttackNerfStrategy.GARBAGE_HITS, "30");
        user.applyAttackNerfer(AttackNerfStrategy.DMG_MEDIUM, "30");
        user.applyAttackNerfer(AttackNerfStrategy.BLOCKING, "30");
      }
    } else {
      meta.vl = Math.max(0, meta.vl - 0.5);
    }
  }

  public static class SwingDeviationBlueprintMeta extends SwingBlueprintMeta {
    double vl;
    // Nothing yet!
  }
}