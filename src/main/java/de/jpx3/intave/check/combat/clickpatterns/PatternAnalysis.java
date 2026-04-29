package de.jpx3.intave.check.combat.clickpatterns;

import com.comphenix.protocol.events.PacketEvent;
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
import java.util.Deque;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;

public final class PatternAnalysis extends MetaCheckPart<ClickPatterns, PatternAnalysis.PatternMeta> {
    private static final int BUFFER_TIMEOUT = 2500;
    private static final int BUFFER_LENGTH = 60;

    public PatternAnalysis(ClickPatterns parentCheck) {
        super(parentCheck, PatternMeta.class);
    }

    @PacketSubscription(
            packetsIn = ARM_ANIMATION
    )
    public void receiveSwing(PacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        PatternMeta meta = metaOf(user);

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

        double ticks = swingDifferenceMs / 50.0;
        meta.intervals.add(ticks);

        if (meta.intervals.size() >= BUFFER_LENGTH) {
            double cps = ClickMathUtils.getCPS(meta.intervals);

            if (cps >= 5.5 && cps <= 15.0) {
                double shortTermDrift = ClickMathUtils.calculateWindowDrift(meta.intervals, 15);
                double longTermDrift = ClickMathUtils.calculateWindowDrift(meta.intervals, 30);
                double driftRatio = shortTermDrift / Math.max(longTermDrift, 0.1);

                double mean = ClickMathUtils.getMean(meta.intervals);
                double std = ClickMathUtils.getStandardDeviation(meta.intervals);
                double cv = (std / Math.max(mean, 1.0)) * 100;

                int driftScore = 0;
                if (shortTermDrift < 3.5) driftScore += 3;
                if (longTermDrift < 5.8) driftScore += 3;
                if (driftRatio > 0.82 && driftRatio < 1.15) driftScore += 2;
                if (cv > 15.0 && cv < 65.0 && std > 10.0) driftScore += 1;

                if (driftScore >= 7) {
                    meta.patternBuffer += 1.0;
                    if (meta.patternBuffer > 2.5) {
                        parentCheck().makeDetection(
                                player,
                                "unnatural pattern drift",
                                "score:" + driftScore + " sDrift:" + formatDouble(shortTermDrift, 2) + " lDrift:" + formatDouble(longTermDrift, 2) + " cv:" + formatDouble(cv, 1) + " cps:" + formatDouble(cps, 1),
                                meta.patternBuffer > 3.5 ? 5 : 0
                        );
                    }
                } else {
                    meta.patternBuffer = Math.max(0, meta.patternBuffer - 0.6);
                }

                if (!meta.cachedIntervals.isEmpty() && meta.cachedIntervals.size() == meta.intervals.size()) {
                    double similarity = ClickMathUtils.calculateSimilarity(meta.intervals, meta.cachedIntervals);
                    double delta = Math.abs(similarity - meta.lastSimilarity);
                    if (delta < 0.015 && similarity < 0.8) {
                        meta.similarityBuffer += 1.0;
                        if (meta.similarityBuffer > 5.0) {
                            parentCheck().makeDetection(
                                    player,
                                    "macro playback",
                                    "sim:" + formatDouble(similarity, 2) + " delta:" + formatDouble(delta, 3),
                                    meta.similarityBuffer > 7.0 ? 5 : 0
                            );
                        }
                    } else {
                        meta.similarityBuffer = Math.max(0, meta.similarityBuffer - 0.75);
                    }
                    meta.lastSimilarity = similarity;
                }
                meta.cachedIntervals.clear();
                meta.cachedIntervals.addAll(meta.intervals);

            } else {
                meta.patternBuffer = Math.max(0, meta.patternBuffer - 0.6);
                meta.similarityBuffer = Math.max(0, meta.similarityBuffer - 0.75);
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

    public static class PatternMeta extends CheckCustomMetadata {
        private final Deque<Double> intervals = new ArrayDeque<>();
        private final Deque<Double> cachedIntervals = new ArrayDeque<>();
        private long lastSwing = 0;
        private double patternBuffer = 0;
        private double similarityBuffer = 0;
        private double lastSimilarity = 0;
    }
}
