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
import java.util.Deque;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;
import static java.lang.Math.pow;

@Relocate
public final class Kurtosis extends MetaCheckPart<ClickPatterns, Kurtosis.KurtosisMeta> {
  private final static int BUFFER_TIMEOUT = 4000;
  private final static int BUFFER_LENGTH = 25;

  public Kurtosis(ClickPatterns parentCheck) {
    super(parentCheck, KurtosisMeta.class);
  }

  @PacketSubscription(
    packetsIn = ARM_ANIMATION
  )
  public void receiveSwing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    KurtosisMeta meta = metaOf(user);
    long lastSwing = meta.lastSwing;
    long swingDifference = System.currentTimeMillis() - lastSwing;
    meta.lastSwing = System.currentTimeMillis();
    Deque<Long> attacks = meta.attacks;
    if (swingDifference > BUFFER_TIMEOUT || user.meta().attack().inBreakProcess) {
      attacks.clear();
      return;
    }
    attacks.offerFirst(swingDifference);
    if (attacks.size() >= BUFFER_LENGTH) {
      double kurtosis = kurtosisOf(attacks) / 1000d;
      if (kurtosis < 13) {
        if (++meta.vl > 20) {
          parentCheck().makeDetection(player, "low relative variance", "h:" + ((int) kurtosis), meta.vl > 21 ? 10 : 5);
          attacks.clear();
        }
      } else if (meta.vl > 0) {
        meta.vl -= 0.1;
        meta.vl *= 0.98;
      }
      if (!attacks.isEmpty()) {
        attacks.removeLast();
      }
    }
  }

  private double kurtosisOf(Collection<? extends Number> input) {
    double sum = 0;
    int amount = 0;
    for (Number number : input) {
      sum += number.doubleValue();
      ++amount;
    }
    if (amount < 3.0) {
      return 0.0;
    }
    double d2 = amount * (amount + 1.0) / ((amount - 1.0) * (amount - 2.0) * (amount - 3.0));
    double d3 = 3.0 * pow(amount - 1.0, 2.0) / ((amount - 2.0) * (amount - 3.0));
    double average = sum / amount;
    double s2 = 0.0;
    double s4 = 0.0;
    for (Number number : input) {
      s2 += pow(average - number.doubleValue(), 2);
      s4 += pow(average - number.doubleValue(), 4);
    }
    return d2 * (s4 / pow(s2 / sum, 2)) - d3;
  }

  public static class KurtosisMeta extends CheckCustomMetadata {
    private final Deque<Long> attacks = new ArrayDeque<>();
    private double vl = 0;
    private long lastSwing = 0;
  }
}
