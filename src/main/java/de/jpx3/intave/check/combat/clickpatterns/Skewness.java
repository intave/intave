package de.jpx3.intave.check.combat.clickpatterns;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;
import static java.lang.Math.abs;

@Relocate
public final class Skewness extends MetaCheckPart<ClickPatterns, Skewness.SkewnessMeta> {
  private static final int BUFFER_TIMEOUT = 4000;
  private static final int BUFFER_LENGTH = 50;
  private static final double LOWER_SKEWNESS_LIMIT = -0.01;

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

    // Calculating when the last swing was
    long lastSwing = meta.lastSwing;
    long swingDifference = System.currentTimeMillis() - lastSwing;
    meta.lastSwing = System.currentTimeMillis();

    Queue<Long> attacks = meta.attacks;

    // When the check is disabled, there is no need to check
    if (checkDeactivated(user, swingDifference)) {
      attacks.clear();
      return;
    }

    if (attacks.isEmpty()) {
      meta.started = System.currentTimeMillis();
    }
    attacks.add(swingDifference);

    // If the attacks queue reached the buffer length, Intave will calculate the skewness and check if the skewness (german: schräglage) is under the skewness limit
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

  private boolean checkDeactivated(
    User user,
    long swingDifference
  ) {
    AttackMetadata attack = user.meta().attack();
    ItemStack heldItem = user.meta().inventory().heldItem();
    return swingDifference > BUFFER_TIMEOUT ||
      attack.inBreakProcess ||
      System.currentTimeMillis() - attack.lastBreak < 3000 ||
      (heldItem != null && heldItem.getType() == Material.FISHING_ROD);
  }

  private double skewnessOf(Collection<? extends Number> sd) {
    int amount = sd.size();
    if (amount == 0) {
      return 0;
    }
    double total = 0;
    List<Double> numbersAsDoubles = new ArrayList<>();
    for (Number number : sd) {
      double numberAsDouble = number.doubleValue();
      total += numberAsDouble;
      numbersAsDoubles.add(numberAsDouble);
    }
    numbersAsDoubles.sort(Double::compareTo);
    double mean = total / amount;
    double median = numbersAsDoubles.get((amount % 2 != 0 ? amount : amount - 1) / 2);
    return 3 * (mean - median) / standardDeviationOf(numbersAsDoubles);
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
