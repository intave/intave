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

public final class ConsistencyAnalysis extends MetaCheckPart<ClickPatterns, ConsistencyAnalysis.ConsistencyMeta> {
    private static final int BUFFER_TIMEOUT = 2500;
    private static final int BUFFER_LENGTH = 100;

    public ConsistencyAnalysis(ClickPatterns parentCheck) {
        super(parentCheck, ConsistencyMeta.class);
    }

    @PacketSubscription(
            packetsIn = ARM_ANIMATION
    )
    public void receiveSwing(PacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        ConsistencyMeta meta = metaOf(user);

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
            double std = ClickMathUtils.getStandardDeviation(meta.intervals);
            double cps = ClickMathUtils.getCPS(meta.intervals);

            if (std <= 0.45 && cps > 8.0) {
                meta.buffer += 1.0;
                if (meta.buffer > 3.0) {
                    parentCheck().makeDetection(
                            player,
                            "low variance",
                            "std:" + formatDouble(std, 3) + " cps:" + formatDouble(cps, 1) + " buf:" + formatDouble(meta.buffer, 1),
                            meta.buffer > 4 ? 5 : 0
                    );
                }
            } else {
                meta.buffer = Math.max(0, meta.buffer - 0.5);
            }

            double difference = Math.abs(std - meta.lastStandardDeviation);
            if (difference <= 0.01) {
                meta.diffBuffer += 1.0;
                if (meta.diffBuffer > 3.0) {
                    parentCheck().makeDetection(
                            player,
                            "consistent variance",
                            "diff:" + formatDouble(difference, 3) + " std:" + formatDouble(std, 3),
                            meta.diffBuffer > 4 ? 5 : 0
                    );
                }
            } else {
                meta.diffBuffer = Math.max(0, meta.diffBuffer - 0.5);
            }

            meta.lastStandardDeviation = std;
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

    public static class ConsistencyMeta extends CheckCustomMetadata {
        private final Deque<Double> intervals = new ArrayDeque<>();
        private long lastSwing = 0;
        private double buffer = 0;
        private double diffBuffer = 0;
        private double lastStandardDeviation = 0;
    }
}
