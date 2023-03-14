package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.nayoro.NayoroRelay;
import de.jpx3.intave.module.nayoro.PlayerContainer;
import de.jpx3.intave.module.nayoro.event.AttackEvent;
import de.jpx3.intave.module.nayoro.event.ClickEvent;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

@Reserved
public final class LongTermClickAccuracyRelayHeuristic extends MetaCheckPart<Heuristics, LongTermClickAccuracyRelayHeuristic.ClickAccuracyMeta> {
  public LongTermClickAccuracyRelayHeuristic(Heuristics parentCheck) {
    super(parentCheck, LongTermClickAccuracyRelayHeuristic.ClickAccuracyMeta.class);
  }

  @NayoroRelay
  public void on(PlayerContainer player, ClickEvent click) {
    ClickAccuracyMeta meta = player.meta(ClickAccuracyMeta.class);
//    Environment environment = player.environment();
//    if (meta.lastAttackedEntityId > 0 && !environment.entityMoved(meta.lastAttackedEntityId, 0.05)) {
//      return;
//    }
//    if (System.currentTimeMillis() - meta.lastAttack > 500 || System.currentTimeMillis() - meta.lastSwitch < 1000) {
//      return;
//    }
//    player.debug("click swing");
    meta.swings++;
  }

  @NayoroRelay
  public void on(PlayerContainer player, AttackEvent attack) {
    ClickAccuracyMeta meta = player.meta(ClickAccuracyMeta.class);
    meta.lastAttack = System.currentTimeMillis();
    if (meta.lastAttackedEntityId != attack.target()) {
      meta.lastSwitch = System.currentTimeMillis();
      meta.lastAttackedEntityId = attack.target();
    }
//    Environment environment = player.environment();
//    if (!environment.entityMoved(attack.target(), 0.05)) {
//      return;
//    }
//    if (System.currentTimeMillis() - meta.lastAttack > 500 || System.currentTimeMillis() - meta.lastSwitch < 1000) {
//      return;
//    }
    meta.attacks++;
//    meta.swings--;
    int fails = (int) (meta.swings - meta.attacks);
    double failRate = (fails / meta.swings) * 100.0;
    if (meta.attacks >= 100) {
      if (failRate >= 0 && failRate < 3) {
        player.noteAnomaly("210", Confidence.NONE, "player maintains high attack accuracy (failRate: " + MathHelper.formatDouble(failRate, 2) + "%)");
      }
      meta.attacks = 0;
      meta.swings = 0;
    }
  }

  public static class ClickAccuracyMeta extends CheckCustomMetadata {
    public double attacks;
    public double swings;
    public long lastAttack;
    public long lastSwitch;
    public int lastAttackedEntityId;
  }
}
