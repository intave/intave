package de.jpx3.intave.check.combat.clickpatterns;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
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

public final class Entropy extends MetaCheckPart<ClickPatterns, Entropy.EntropyMeta> {
    private static final int BUFFER_TIMEOUT = 4000;
    private static final int BUFFER_LENGTH = 100;

    public Entropy(ClickPatterns parentCheck) {
        super(parentCheck, EntropyMeta.class);
    }

    @PacketSubscription(
            packetsIn = ARM_ANIMATION
    )
    public void receiveSwing(ProtocolPacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        EntropyMeta meta = metaOf(user);

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

        if (attacks.size() >= BUFFER_LENGTH) {
            long length = System.currentTimeMillis() - meta.started;

            double entropy = entropy(attacks);
            // Necessary for the statistically low variance check
            meta.entropySamples.add((long) entropy);

            if (entropy <= 1 && entropy >= 0.35 && length < 4000) {
                int vlAdd = 2;
                meta.vl += vlAdd;
                if (meta.vl > 1) {
                    parentCheck().makeDetection(
                            player,
                            "low entropy",
                            "e:" + formatDouble(entropy, 3) + " t:" + formatDouble(length / 1000d, 2),
                            meta.vl > 0 ? 5 : 0
                    );
                }
            } else if (meta.vl > 0) {
                meta.vl -= 0.2;
                meta.vl *= 0.98;
            }

            attacks.clear();
        }

        // After we got 4 deviation samples, we are going to check the deviation of these samples, if it's too low, the player is performing a long-term consistency
        if (meta.entropySamples.size() >= 4) {
            double std = standardDeviation(meta.entropySamples);

            long length = System.currentTimeMillis() - meta.started;

            if (std < 0.3 && length < 4000) {
                int vlAdd = 2;
                meta.vl += vlAdd;
                if (meta.vl > 3) {
                    parentCheck().makeDetection(
                            player,
                            "balanced entropy",
                            "sd:" + formatDouble(std, 3) + " t:" + formatDouble(length / 1000d, 2),
                            meta.vl > 0 ? 5 : 0
                    );
                }
            } else if (meta.vl > 0) {
                meta.vl -= 0.2;
                meta.vl *= 0.98;
            }

            meta.entropySamples.clear();
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

    public static <T extends Number> double entropy(Collection<T> values) {
        if (values.isEmpty()) {
            return 0;
        }

        Map<T, Integer> valueCounts = new HashMap<>();
        int totalCount = 0;

        for (T value : values) {
            if (value != null) {
                valueCounts.put(value, valueCounts.getOrDefault(value, 0) + 1);
                totalCount++;
            }
        }

        double entropy = 0;
        for (int count : valueCounts.values()) {
            double probability = (double) count / totalCount;
            entropy -= probability * log2(probability);
        }

        return entropy;
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    public static class EntropyMeta extends CheckCustomMetadata {
        private final Queue<Long> attacks = new ArrayDeque<>();
        private final Queue<Long> entropySamples = new ArrayDeque<>();
        private double vl = 0;
        private long lastSwing = 0;
        private long started = System.currentTimeMillis();
    }
}
