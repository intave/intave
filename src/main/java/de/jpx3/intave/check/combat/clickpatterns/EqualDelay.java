package de.jpx3.intave.check.combat.clickpatterns;

import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.user.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class EqualDelay extends TickAlignedHistoryBlueprint<EqualDelay.EqualDelayMeta>  {
  public EqualDelay(ClickPatterns parentCheck) {
    super(parentCheck, EqualDelayMeta.class);
  }

  @Override
  public void analyzeClicks(User user, EqualDelayMeta meta) {
    List<TickAction> tickActions = meta.tickActions;
//    List<Integer> tickIntensity = meta.tickIntensity;

    // check for equal delay between clicks
    List<Integer> delay = new ArrayList<>();
    int delaySinceLastClick = 0;

    for (TickAction tickAction : tickActions) {
      if (tickAction == TickAction.CLICK || tickAction == TickAction.ATTACK) {
        if (delaySinceLastClick > 0) {
          delay.add(delaySinceLastClick);
        }
        delaySinceLastClick = 0;
      } else {
        delaySinceLastClick++;
      }
    }

    if (delay.isEmpty()) {
      return;
    }

    int lastDelay = delay.get(0);
    int streak = 1;

    for (Integer integer : delay) {
      if (integer == lastDelay) {
        streak++;
        int streakLimit = 10;
        if (lastDelay > 1) {
          streakLimit += 2;
        }
        if (lastDelay > 2) {
          streakLimit += 2;
        }
        if (lastDelay > 3) {
          streakLimit += 2;
        }
        if (streak > streakLimit) {
//          flag(user, "exhibits repetitive delay between clicks", 0);
          break;
        }
      } else {
        streak = 1;
      }
      lastDelay = integer;
    }
//    Player player = user.player();
//    player.sendMessage("delay: " + delay);
//    player.sendMessage("coefficientOfDetermination: " + coefficientOfDetermination(delay));
//    player.sendMessage("variance: " + variance(delay));
//    player.sendMessage("mean: " + mean(delay));
//    player.sendMessage("median: " + median(delay));
  }

  private static double mean(Collection<? extends Number> values) {
    double sum = 0;
    for (Number value : values) {
      sum += value.doubleValue();
    }
    return sum / values.size();
  }

  private static double median(Collection<? extends Number> values) {
    List<Double> sortedValues = new ArrayList<>();
    for (Number value : values) {
      sortedValues.add(value.doubleValue());
    }
    sortedValues.sort(Double::compareTo);
    if (sortedValues.size() % 2 == 0) {
      return (sortedValues.get(sortedValues.size() / 2) + sortedValues.get(sortedValues.size() / 2 - 1)) / 2;
    } else {
      return sortedValues.get(sortedValues.size() / 2);
    }
  }

  private static double variance(Collection<? extends Number> values) {
    double sum = 0;
    for (Number value : values) {
      sum += value.doubleValue();
    }
    double mean = sum / values.size();
    double sumOfSquares = 0;
    for (Number value : values) {
      sumOfSquares += Math.pow(value.doubleValue() - mean, 2);
    }
    return sumOfSquares / values.size();
  }

  private static double coefficientOfDetermination(Collection<? extends Number> values) {
    double variance = variance(values);
    double mean = mean(values);
    double sumOfSquares = 0;
    for (Number value : values) {
      sumOfSquares += Math.pow(value.doubleValue() - mean, 2);
    }
    return 1 - (variance / sumOfSquares);
  }

  public static class EqualDelayMeta extends TickAlignedMeta {

  }
}
