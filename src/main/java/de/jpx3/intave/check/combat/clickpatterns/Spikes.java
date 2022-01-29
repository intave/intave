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
public final class Spikes extends MetaCheckPart<ClickPatterns, Spikes.SpikesMeta> {
  private final static int BUFFER_TIMEOUT = 4000;
  private final static int BUFFER_LENGTH = 10;

  public Spikes(ClickPatterns parentCheck) {
    super(parentCheck, SpikesMeta.class);
  }

  @PacketSubscription(
    packetsIn = ARM_ANIMATION
  )
  public void receiveSwing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SpikesMeta meta = metaOf(user);
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
      double cps = cpsOf(attacks);
      if (meta.lastCPS > 0) {
        double difference = Math.abs(cps - meta.lastCPS);
        double meanChange = (meta.lastCPS - cps) / 2;
        if (meanChange > 9.25 && difference > 2.8) {
          if (++meta.vl > 0) {
            parentCheck().makeDetection(
              player,
              "spike",
              "k:" + formatDouble(meanChange, 3) + " f:" + formatDouble(difference, 3),
              meta.vl > 8 ? 1 : 0
            );
          }
        } else if (meta.vl > 0) {
          meta.vl -= 0.2;
          meta.vl *= 0.98;
        }
      }
      meta.lastCPS = cps;
      attacks.clear();
    }
  }

  private double cpsOf(Collection<? extends Number> input) {
    return 20d / sumOf(input) * 50d;
  }

  private double sumOf(Collection<? extends Number> input) {
    double val = 0;
    for (Number number : input) {
      val += number.doubleValue();
    }
    return val;
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

  public static class SpikesMeta extends CheckCustomMetadata {
    private final Queue<Long> attacks = new ArrayDeque<>();
    private double vl = 0;
    private double lastCPS = 0;
    private long lastSwing = 0;
    private long started = System.currentTimeMillis();
  }
}
