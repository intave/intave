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
import java.util.LinkedList;
import java.util.Queue;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;

@Relocate
public final class Repetitive extends MetaCheckPart<ClickPatterns, Repetitive.RepetitiveMeta> {

    private static final int BUFFER_TIMEOUT = 4000;
    private static final int BUFFER_LENGTH = 10;

    public Repetitive(ClickPatterns parentCheck) {
        super(parentCheck, RepetitiveMeta.class);
    }

    @PacketSubscription(
            packetsIn = ARM_ANIMATION
    )
    public void receiveSwing(PacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        RepetitiveMeta meta = metaOf(user);

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
            double cps = cpsOf(attacks);

            double difference = cps - meta.lastCPS;
            if (difference != 0.0) {
                meta.pattern.add(difference);
            }

            meta.lastCPS = cps;
            attacks.clear();
        }

        if (meta.pattern.size() >= 30) {
            if (hasRepetitivePattern(meta.pattern, 0.01)) {
                if (++meta.vl > 2) {
                    parentCheck().makeDetection(
                            player,
                            "repetitive",
                            "std:" + formatDouble(meta.pattern.getLast(), 3),
                            meta.vl > 8 ? 1 : 0
                    );
                }
            } else if (meta.vl > 0) {
                meta.vl -= 0.2;
                meta.vl *= 0.98;
            }

            meta.pattern.clear();
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

    private boolean hasRepetitivePattern(LinkedList<Double> list, double threshold) {
        int length = list.size();

        // Repetitive
        for (int patternLength = 2; patternLength <= length / 2; patternLength++) {
            boolean isRepetitive = true;

            for (int i = 0; i < length - patternLength; i++) {
                if (!list.get(i).equals(list.get(i + patternLength))) {
                    isRepetitive = false;
                    break;
                }
            }

            if (isRepetitive) {
                return true;
            }
        }

        // This will detect samples that are close to each other, based on the threshold
        for (int i = 0; i < length - 1; i++) {
            if (Math.abs(list.get(i) - list.get(i + 1)) <= threshold) {
                return true;
            }
        }

        return false;
    }



    private double sumOf(Collection<? extends Number> input) {
        double val = 0;
        for (Number number : input) {
            val += number.doubleValue();
        }
        return val;
    }

    public static class RepetitiveMeta extends CheckCustomMetadata {
        private final Queue<Long> attacks = new ArrayDeque<>();
        private double vl = 0;
        private double lastCPS = 0;
        private long lastSwing = 0;

        private final LinkedList<Double> pattern = new LinkedList<>();

        private long started = System.currentTimeMillis();
    }
}
