package de.jpx3.intave.check.combat.clickpatterns;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;

@Relocate
public final class Fluctuation extends MetaCheckPart<ClickPatterns, Fluctuation.FluctuationMeta> {

    /*
    Fluctuations are variations or movements that aren't steady or constant.
    This check will save spike and drop timestamps and check if the variance between them is too low
    Some autoclickers like Vape or Karma have extra randomization, sometimes with a low variance in randomization
     */

    private static final int BUFFER_TIMEOUT = 4000;
    private static final int BUFFER_LENGTH = 10;

    public Fluctuation(ClickPatterns parentCheck) {
        super(parentCheck, FluctuationMeta.class);
    }

    @PacketSubscription(
            packetsIn = ARM_ANIMATION
    )
    public void receiveSwing(PacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        FluctuationMeta meta = metaOf(user);

        // Calculating when the last swing was
        long lastSwing = meta.lastSwing;
        long swingDifference = System.currentTimeMillis() - lastSwing;
        meta.lastSwing = System.currentTimeMillis();

        Queue<Long> attacks = meta.attacks;

        // A high swing difference would kill the timestamp variance, so we will just clear it here
        if (swingDifference > 8000) {
            meta.spikeTimestamps.clear();
            meta.dropTimestamps.clear();
        }

        // When the check is disabled, there is no need to check
        if (checkDeactivated(user, swingDifference)) {
            attacks.clear();
            return;
        }

        if (attacks.isEmpty()) {
            meta.started = System.currentTimeMillis();
        }
        attacks.add(swingDifference);

        // Determination if the there is a spike or a drop
        if (attacks.size() >= BUFFER_LENGTH) {
            double cps = cpsOf(attacks);

            double difference = cps - meta.lastCPS;
            if (difference > 0.4) {
                meta.spikes++;
                meta.spikeTimestamps.add(System.currentTimeMillis());
            }

            if (difference < -0.4) {
                meta.drops++;
                meta.dropTimestamps.add(System.currentTimeMillis());
            }

            meta.lastCPS = cps;
            attacks.clear();
        }

        // If the spike array reached the required sample size, we are going to check if the variance of the timestamps is balanced. Don't put this over 3
        if (meta.spikeTimestamps.size() >= 3) {
            double std = standardDeviationOf(meta.spikeTimestamps);
            meta.spikeTimestamps.clear();
            if (std < 4000) {
                if (++meta.vl > 5) {
                    parentCheck().makeDetection(
                            player,
                            "balanced fluctuation",
                            "std:" + formatDouble(std, 3),
                            meta.vl > 8 ? 1 : 0
                    );
                }
            } else if (meta.vl > 0) {
                meta.vl -= 0.2;
                meta.vl *= 0.98;
            }
        }

        // If the drop array reached the required sample size, we are going to check if the variance of the timestamps is balanced. Don't put this over 3
        if (meta.dropTimestamps.size() >= 3) {
            double std = standardDeviationOf(meta.dropTimestamps);
            meta.dropTimestamps.clear();
            if (std < 4000) {
                if (++meta.vl > 5) {
                    parentCheck().makeDetection(
                            player,
                            "balanced fluctuation",
                            "std:" + formatDouble(std, 3),
                            meta.vl > 8 ? 1 : 0
                    );
                }
            } else if (meta.vl > 0) {
                meta.vl -= 0.2;
                meta.vl *= 0.98;
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

    public static class FluctuationMeta extends CheckCustomMetadata {
        private final Queue<Long> attacks = new ArrayDeque<>();
        private double vl = 0;
        private double lastCPS = 0;
        private long lastSwing = 0;

        private int spikes;
        private final Queue<Long> spikeTimestamps = new ArrayDeque<>();

        private int drops;
        private final Queue<Long> dropTimestamps = new ArrayDeque<>();

        private long started = System.currentTimeMillis();
    }
}
