package de.jpx3.intave.check.combat.heuristics.detect.clickpatterns;

import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import java.util.ArrayList;
import java.util.List;

@Reserved
public abstract class SwingBlueprintMeta extends CheckCustomMetadata {
  protected final List<ClickData> pendingClicks = new ArrayList<>();
  protected final List<Integer> delays = new ArrayList<>();
  protected final List<Integer> delaysDelta = new ArrayList<>();
  protected int delay, lastDelay;
  protected int doubleClicks;
  protected int lastAttack; // In client ticks
  protected boolean placedBlock;

  // This is used to store clicks on ARM_ANIMATION packet and queue their processing to client tick
  // So we can see if they sent an attack packet and not need to raytrace blocks
  public static class ClickData {
    protected final int delay;
    protected final int lastDelay;

    public ClickData(int delay, int lastDelay) {
      this.delay = delay;
      this.lastDelay = lastDelay;
    }
  }
}

