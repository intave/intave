package de.jpx3.intave.check.combat.clickpatterns;

import de.jpx3.intave.user.meta.CheckCustomMetadata;

import java.util.LinkedList;
import java.util.List;

public class TickAlignedMeta extends CheckCustomMetadata {
  final int historyLength = 40;
  final List<TickAlignedHistoryBlueprint.TickAction> tickActions = new LinkedList<>();
  final List<Integer> tickIntensity = new LinkedList<>();
  final List<Boolean> inBlockBreak = new LinkedList<>();
  boolean breakingBlock;
  int clicks, attacks, places;
  int tickCount;

  {
    for (int i = 0; i < historyLength; i++) {
      tickActions.add(TickAlignedHistoryBlueprint.TickAction.NOTHING);
      tickIntensity.add(0);
      inBlockBreak.add(false);
//      streakLength.add(0);
    }
  }
}
