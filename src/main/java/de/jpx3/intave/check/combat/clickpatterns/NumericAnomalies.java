package de.jpx3.intave.check.combat.clickpatterns;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.metric.ServerHealth;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;

public final class NumericAnomalies extends MetaCheckPart<ClickPatterns, NumericAnomalies.NumericMeta> {
    private static final int BUFFER_TIMEOUT = 2500;
    private static final int BUFFER_LENGTH = 30;

    public NumericAnomalies(ClickPatterns parentCheck) {
        super(parentCheck, NumericMeta.class);
    }

    @PacketSubscription(
            packetsIn = ARM_ANIMATION
    )
    public void receiveSwing(PacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        NumericMeta meta = metaOf(user);

        long now = System.currentTimeMillis();
        if (meta.lastSwing == 0) {
            meta.lastSwing = now;
            return;
        }

        long swingDifferenceMs = now - meta.lastSwing;
        meta.lastSwing = now;

        if (checkDeactivated(user, swingDifferenceMs)) {
            meta.intervals.clear();
            return;
        }

        double currentTPS = ServerHealth.recentTickAverage()[0];
        double msPerTick = 1000.0 / Math.max(1.0, currentTPS);
        double ticks = swingDifferenceMs / msPerTick;
        meta.intervals.add(ticks);

        if (meta.intervals.size() >= BUFFER_LENGTH) {
            int range = ClickMathUtils.getRange(meta.intervals);
            double mean = ClickMathUtils.getMean(meta.intervals);
            double cps = ClickMathUtils.getCPS(meta.intervals);
            double perfectRatio = ClickMathUtils.getRatioOfValue(meta.intervals, 1.0, 0.1);

            if (currentTPS > 18.5) {
                if (mean < 3.0 && range <= 1 && perfectRatio > 0.8 && cps > 8.0) {
                    meta.perfectBuffer += 1.0;
                    if (meta.perfectBuffer > 15.0) {
                        parentCheck().makeDetection(
                        player,
                        "numeric perfection",
                        "range:" + range + " perfectRatio:" + formatDouble(perfectRatio, 2) + " cps:" + formatDouble(cps, 1),
                        meta.perfectBuffer > 20 ? 5 : 0
                        );
                    }
                } else {
                    meta.perfectBuffer = Math.max(0, meta.perfectBuffer - 0.5);
                }
            }

            if (range == 1 && cps > 5.0 && cps < 22.0) {
                double drift = ClickMathUtils.calculateWindowDrift(meta.intervals, 10);
                if (drift < 8.0) {
                    meta.rangeBuffer += 4.0;
                    if (meta.rangeBuffer > 25.0) {
                        parentCheck().makeDetection(
                                player,
                                "constant delta",
                                "range:" + range + " drift:" + formatDouble(drift, 1) + " cps:" + formatDouble(cps, 1),
                                meta.rangeBuffer > 30 ? 5 : 0
                        );
                    }
                } else {
                    meta.rangeBuffer = Math.max(0, meta.rangeBuffer - 3.0);
                }
            } else if (range == 2) {
                meta.rangeBuffer = Math.max(0, meta.rangeBuffer - 6.0);
            } else if (range >= 3) {
                meta.rangeBuffer = Math.max(0, meta.rangeBuffer - 12.0);
            } else if (range == 0) {
                meta.rangeBuffer += 2.0;
            }

            meta.intervals.clear();
        }
    }

    private boolean checkDeactivated(User user, long swingDifference) {
        AttackMetadata attack = user.meta().attack();
        ItemStack heldItem = user.meta().inventory().heldItem();
        return swingDifference > BUFFER_TIMEOUT ||
                attack.inBreakProcess ||
                System.currentTimeMillis() - attack.lastBreak < 3000 ||
                (heldItem != null && heldItem.getType() == Material.FISHING_ROD);
    }

    public static class NumericMeta extends CheckCustomMetadata {
        private final Deque<Double> intervals = new ArrayDeque<>();
        private long lastSwing = 0;
        private double perfectBuffer = 0;
        private double rangeBuffer = 0;
    }
}
