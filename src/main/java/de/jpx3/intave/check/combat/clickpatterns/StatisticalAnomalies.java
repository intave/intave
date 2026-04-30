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

public final class StatisticalAnomalies extends MetaCheckPart<ClickPatterns, StatisticalAnomalies.AnomaliesMeta> {
    private static final int BUFFER_TIMEOUT = 2500;
    private static final int BUFFER_LENGTH = 50;

    public StatisticalAnomalies(ClickPatterns parentCheck) {
        super(parentCheck, AnomaliesMeta.class);
    }

    @PacketSubscription(
            packetsIn = ARM_ANIMATION
    )
    public void receiveSwing(PacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        AnomaliesMeta meta = metaOf(user);

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

        // this will make that the check works when the server is laggy
        double currentTPS = ServerHealth.recentTickAverage()[0];
        double msPerTick = 1000.0 / Math.max(1.0, currentTPS);
        double ticks = swingDifferenceMs / msPerTick;
        meta.intervals.add(ticks);

        if (meta.intervals.size() >= BUFFER_LENGTH) {
            double skewness = ClickMathUtils.getSkewness(meta.intervals);
            double cps = ClickMathUtils.getCPS(meta.intervals);

            // this "magic numbers" are based on a test that I've done against common
            // (hcf/skywars/general 1.8) autoclickers like NvidiaControlPanel or Slinky
            if (Math.abs(skewness) < 0.1 && cps > 10.0) {
                meta.skewnessBuffer += 1.0;
                if (meta.skewnessBuffer > 4.0) {
                    parentCheck().makeDetection(
                            player,
                            "anomalous skewness",
                            "sk:" + formatDouble(skewness, 3) + " cps:" + formatDouble(cps, 1) + " buf:" + formatDouble(meta.skewnessBuffer, 1),
                            meta.skewnessBuffer > 5 ? 5 : 0
                    );
                }
            } else {
                meta.skewnessBuffer = Math.max(0, meta.skewnessBuffer - 0.25);
            }

            double variance = ClickMathUtils.getVariance(meta.intervals);
            double std = Math.sqrt(variance);
            double entropy = ClickMathUtils.getEntropy(meta.intervals);
            double giniCoefficient = ClickMathUtils.giniCoefficient(meta.intervals);
            double serialCorrelation = ClickMathUtils.calculateSerialCorrelation(meta.intervals);

            double entropyOffset = Math.abs(entropy - meta.lastEntropy);
            double coefficientOffset = Math.abs(giniCoefficient - meta.lastCoefficient);
            double correlationOffset = Math.abs(serialCorrelation - meta.lastCorrelation);

            boolean rFlag = (giniCoefficient < 0.028 && entropy < 0.77 && serialCorrelation < 0.5)
                    || (coefficientOffset < 0.02 && entropyOffset < 0.02 && correlationOffset < 0.02)
                    || (entropyOffset < 0.037 && entropy < 1.5 && serialCorrelation < 0.5)
                    || (coefficientOffset < 0.006 && entropy < 1.1 && serialCorrelation < 0.3)
                    || (correlationOffset < 0.01 && entropy < 1.1 && serialCorrelation < 0.25);

            if (rFlag && cps >= 9.25) {
                meta.giniBuffer += 1.0;
                if (meta.giniBuffer > 6.5) {
                    parentCheck().makeDetection(
                            player,
                            "synthetic distribution",
                            "gini:" + formatDouble(giniCoefficient, 3) + " ent:" + formatDouble(entropy, 2) + " cor:" + formatDouble(serialCorrelation, 2),
                            meta.giniBuffer > 8.0 ? 5 : 0
                    );
                }
            } else {
                meta.giniBuffer = Math.max(0, meta.giniBuffer - 0.075);
            }

            boolean validVariance = (Math.abs(variance - meta.lastVariance) < 1.0 || Math.abs(variance - meta.lastLastVariance) < 1.0) && variance > 11.0;
            boolean validEntropy = entropy > 0.8 && entropy < 1.5;
            boolean invalidVarEntropy = entropy < 0.45 && variance < 5.0;
            boolean invalidEntropyOffset = entropyOffset < 0.02 || Math.abs(entropy - meta.lastLastEntropy) < 0.02;

            if (invalidVarEntropy || (validVariance && validEntropy && invalidEntropyOffset)) {
                meta.varianceBuffer += 1.0;
                if (meta.varianceBuffer > 3.0) {
                    parentCheck().makeDetection(
                            player,
                            "static variance",
                            "var:" + formatDouble(variance, 1) + " ent:" + formatDouble(entropy, 2),
                            meta.varianceBuffer > 4.5 ? 5 : 0
                    );
                }
            } else {
                meta.varianceBuffer = Math.max(0, meta.varianceBuffer - 0.4);
            }

            double skewDelta = Math.abs(skewness - meta.lastSkew);
            double stdDelta = Math.abs(std - meta.lastStd);
            double mean = ClickMathUtils.getMean(meta.intervals);
            double mode = ClickMathUtils.getMode(meta.intervals);
            
            if (mean < 2.0 && mode < 2.0) {
                if (stdDelta < 0.001 && skewDelta < 0.02) {
                    meta.modeBuffer += 1.0;
                    if (meta.modeBuffer > 5.0) {
                        parentCheck().makeDetection(
                                player,
                                "frozen distribution",
                                "mode:" + formatDouble(mode, 1) + " stdD:" + formatDouble(stdDelta, 4) + " skewD:" + formatDouble(skewDelta, 3),
                                meta.modeBuffer > 7.0 ? 5 : 0
                        );
                    }
                } else {
                    meta.modeBuffer = Math.max(0, meta.modeBuffer - 0.2);
                }
            } else {
                meta.modeBuffer = 0;
            }

            meta.lastCoefficient = giniCoefficient;
            meta.lastCorrelation = serialCorrelation;
            meta.lastLastEntropy = meta.lastEntropy;
            meta.lastEntropy = entropy;
            meta.lastLastVariance = meta.lastVariance;
            meta.lastVariance = variance;
            meta.lastSkew = skewness;
            meta.lastStd = std;

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

    public static class AnomaliesMeta extends CheckCustomMetadata {
        private final Deque<Double> intervals = new ArrayDeque<>();
        private long lastSwing = 0;
        private double skewnessBuffer = 0;
        private double giniBuffer = 0;
        private double varianceBuffer = 0;
        private double lastEntropy = 0;
        private double lastLastEntropy = 0;
        private double lastCoefficient = 0;
        private double lastCorrelation = 0;
        private double lastVariance = 0;
        private double lastLastVariance = 0;
        private double modeBuffer = 0;
        private double lastSkew = 0;
        private double lastStd = 0;
    }
}
