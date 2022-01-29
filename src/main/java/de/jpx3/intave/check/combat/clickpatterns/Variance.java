package de.jpx3.intave.check.combat.clickpatterns;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;

@Relocate
public final class Variance extends MetaCheckPart<ClickPatterns, Variance.VarianceMeta> {
  private final static int BUFFER_TIMEOUT = 4000;
  private final static int BUFFER_LENGTH = 50;

  public Variance(ClickPatterns parentCheck) {
    super(parentCheck, VarianceMeta.class);
  }

  @PacketSubscription(
    packetsIn = ARM_ANIMATION
  )
  public void receiveSwing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    VarianceMeta meta = metaOf(user);
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
      double standardDeviation = standardDeviation(attacks);
      if (standardDeviation < 166 && length < 4000) {
        int vlAdd = standardDeviation < 10 ? 2 : 1;
        meta.vl += vlAdd;
        if (meta.vl > 4) {
          parentCheck().makeDetection(
            player,
            "low variance",
            "sd:" + formatDouble(standardDeviation, 3) + " t:" + formatDouble(length / 1000d, 2),
            meta.vl > 8 ? 5 : 0
          );
        }
      } else if (meta.vl > 0) {
        meta.vl -= 0.2;
        meta.vl *= 0.98;
      }
      attacks.clear();
    }
  }

  private double standardDeviation(Collection<? extends Number> sd) {
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

  public static class VarianceMeta extends CheckCustomMetadata {
    private final Queue<Long> attacks = new ArrayDeque<>();
    private double vl = 0;
    private long lastSwing = 0;
    private long started = System.currentTimeMillis();
  }
}
