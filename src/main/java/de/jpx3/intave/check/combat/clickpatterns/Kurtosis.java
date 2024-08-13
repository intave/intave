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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;
import static java.lang.Math.pow;

@Relocate
public final class Kurtosis extends MetaCheckPart<ClickPatterns, Kurtosis.KurtosisMeta> {
  private static final int BUFFER_TIMEOUT = 4000;
  private static final int BUFFER_LENGTH = 25;

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

    // Calculating when the last swing was
    long lastSwing = meta.lastSwing;
    long swingDifference = System.currentTimeMillis() - lastSwing;
    meta.lastSwing = System.currentTimeMillis();

    Deque<Long> attacks = meta.attacks;

    // When the check is disabled, there is no need to check
    if (checkDeactivated(user, swingDifference)) {
      attacks.clear();
      return;
    }
    attacks.offerFirst(swingDifference);

    // If the attacks queue reached the buffer length, Intave will calculate the kurtosis and check if the kurtosis (german: wölbung) is too low
    if (attacks.size() >= BUFFER_LENGTH) {
      double kurtosis = kurtosisOf(attacks) / 1000d;
      if (kurtosis < 6) {
        if (++meta.vl > 15) {
          parentCheck().makeDetection(player, "kurtosis", "h:" + ((int) kurtosis), meta.vl > 24 ? 5 : 2.5);
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
    public final Deque<Long> attacks = new ArrayDeque<>();
    private double vl = 0;
    private long lastSwing = 0;
  }
}
