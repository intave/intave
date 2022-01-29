package de.jpx3.intave.check.combat.clickpatterns;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import java.util.*;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;
import static java.lang.Math.abs;

@Relocate
public final class Skewness extends MetaCheckPart<ClickPatterns, Skewness.SkewnessMeta> {
  private final static int BUFFER_TIMEOUT = 4000;
  private final static int BUFFER_LENGTH = 50;
  private final static double LOWER_SKEWNESS_LIMIT = -0.01;

  public Skewness(ClickPatterns parentCheck) {
    super(parentCheck, SkewnessMeta.class);
  }

  @PacketSubscription(
    packetsIn = ARM_ANIMATION
  )
  public void receiveSwing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SkewnessMeta meta = metaOf(user);
    long lastSwing = meta.lastSwing;
    long swingDifference = System.currentTimeMillis() - lastSwing;
    meta.lastSwing = System.currentTimeMillis();
    Queue<Long> attacks = meta.attacks;
    if (swingDifference > BUFFER_TIMEOUT || user.meta().attack().inBreakProcess) {
      attacks.clear();
      return;
    }
    if (attacks.isEmpty()) {
      meta.started = System.currentTimeMillis();
    }
    attacks.add(swingDifference);
    if (attacks.size() >= BUFFER_LENGTH) {
      long length = System.currentTimeMillis() - meta.started;
      double skewness = skewnessOf(attacks);
      if (skewness < LOWER_SKEWNESS_LIMIT && length < 6000) {
        int vlAdd = abs(skewness) < 0.1 ? 2 : 1;
        meta.vl += vlAdd;
        if (meta.vl > 4) {
          parentCheck().makeDetection(
            player,
            "invariant",
            "s:" + formatDouble(skewness, 3) + " t:" + formatDouble(length / 1000d, 2),
            /*meta.vl > 8 ? 1 : */0
          );
        }
      } else if (meta.vl > 0) {
        meta.vl -= 0.2;
        meta.vl *= 0.98;
      }
      attacks.clear();
    }
  }

  private double skewnessOf(Collection<? extends Number> sd) {
    double total = 0;
    int amount = 0;
    List<Double> asDoubles = new ArrayList<>();
    for (Number number : sd) {
      double numberAsDouble = number.doubleValue();
      total += numberAsDouble;
      amount++;
      asDoubles.add(numberAsDouble);
    }
    if (amount == 0) {
      return 0;
    }
    asDoubles.sort(Double::compareTo);
    double mean = total / amount;
    double median = asDoubles.get((amount % 2 != 0 ? amount : amount - 1) / 2);
    double standardDeviation = standardDeviationOf(asDoubles);
    return 3 * (mean - median) / standardDeviation;
  }

  private double standardDeviationOf(Collection<? extends Number> sd) {
    double sum = 0, newSum = 0;
    for (Number v : sd) {
      sum = sum + v.doubleValue();
    }
    double mean = sum / sd.size();
    for (Number v : sd) {
      newSum = newSum + (v.doubleValue() - mean) * (v.doubleValue() - mean);
    }
    return Math.sqrt(newSum / sd.size());
  }

  public static class SkewnessMeta extends CheckCustomMetadata {
    private final Queue<Long> attacks = new ArrayDeque<>();
    private double vl = 0;
    private long lastSwing = 0;
    private long started = System.currentTimeMillis();
  }
}
