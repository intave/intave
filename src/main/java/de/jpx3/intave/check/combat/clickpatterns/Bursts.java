package de.jpx3.intave.check.combat.clickpatterns;

import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;

public final class Bursts extends TickAlignedHistoryBlueprint<Bursts.BurstMeta> {
  public Bursts(ClickPatterns parentCheck) {
    super(parentCheck, BurstMeta.class);
  }

  @PacketSubscription(
    packetsIn = ARM_ANIMATION
  )
  @Override
  public void analyzeClicks(User user, BurstMeta meta) {
    int consecutiveClicks = 0;
    boolean cancel = meta.breakingBlock;

    List<Integer> streaks = new ArrayList<>();
    List<Boolean> doubleClick = new ArrayList<>();
    boolean doubleClickOccurred = false;

    List<TickAction> tickActions = meta.tickActions;

    for (int i = 0; i < tickActions.size(); i++) {
      TickAction tickAction = tickActions.get(i);
      if (tickAction == TickAction.CLICK || tickAction == TickAction.ATTACK) {
        consecutiveClicks++;
      } else {
        if (consecutiveClicks > 5) {
          streaks.add(consecutiveClicks);
          doubleClick.add(doubleClickOccurred);
          doubleClickOccurred = false;
        }
        consecutiveClicks = 0;
      }

      if (meta.tickIntensity.get(i) > 1) {
        doubleClickOccurred = true;
      }

      if (tickAction == TickAction.PLACE) {
        cancel = true;
      }
    }

    int vl = 0;
    boolean anyDoubleClick = false;

    for (int i = 0; i < streaks.size(); i++) {
      Integer streak = streaks.get(i);
      Boolean doubleClicked = doubleClick.get(i);
      vl += streak + (doubleClicked ? -1 : 2);
      if (doubleClicked) {
        anyDoubleClick = true;
      }
    }

    // We will flag the player if the tick action is not place and reached the violation limit
    if (!cancel && vl >= 12) {
      flag(user, "exhibits click bursts" + (!anyDoubleClick ? " without double clicks" : ""), 5);
    }
  }

  public static class BurstMeta extends TickAlignedMeta {

  }
}
