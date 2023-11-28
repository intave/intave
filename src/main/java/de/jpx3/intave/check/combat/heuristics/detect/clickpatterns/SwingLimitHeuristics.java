package de.jpx3.intave.check.combat.heuristics.detect.clickpatterns;

import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.user.User;

import java.util.List;

import static de.jpx3.intave.check.combat.heuristics.detect.clickpatterns.SwingLimitHeuristics.SwingLimitBlueprintMeta;

@Reserved
public final class SwingLimitHeuristics extends SwingBlueprint<SwingLimitBlueprintMeta> {
  public SwingLimitHeuristics(Heuristics parentCheck) {
    super(parentCheck, SwingLimitBlueprintMeta.class, 60, false, false);
  }

  @Override
  public void check(User user, List<Integer> delays) {
    SwingLimitBlueprintMeta meta = metaOf(userOf(user.player()));
    double cps = clickPerSecond(delays);
    if (cps > 15 && meta.doubleClicks == 0) {
      if (++meta.vl >= 1.5) {
        String description = String.format("clicking too fast without double clicks %.2f vl: %.2f", cps, meta.vl);
        Anomaly anomaly = Anomaly.anomalyOf("300", Confidence.NONE, Anomaly.Type.AUTOCLICKER, description);
        parentCheck().saveAnomaly(user.player(), anomaly);
        //dmc29
//        if (meta.vl >= 5) {
//          user.nerf(AttackNerfStrategy.DMG_LIGHT, "29");
//        }
////        user.applyAttackNerfer(AttackNerfStrategy.DMG_MEDIUM, "29");
//        user.nerf(AttackNerfStrategy.GARBAGE_HITS, "29");
//        user.nerf(AttackNerfStrategy.BLOCKING, "29");
      }
    } else {
      meta.vl = Math.max(0, meta.vl - 0.25);
    }
  }

  public static class SwingLimitBlueprintMeta extends SwingBlueprintMeta {
    double vl;
    // Nothing yet!
  }
}