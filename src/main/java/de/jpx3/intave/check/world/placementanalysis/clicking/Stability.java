package de.jpx3.intave.check.world.placementanalysis.clicking;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class Stability extends MetaCheckPart<PlacementAnalysis, Stability.StabilityMeta> {
    private static final int BUFFER_TIMEOUT = 4000;
    private static final int BUFFER_LENGTH = 50;

    public Stability(PlacementAnalysis parentCheck) {
        super(parentCheck, StabilityMeta.class);
    }

    @PacketSubscription(
            packetsIn = BLOCK_PLACE
    )
    public void receiveSwing(ProtocolPacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        StabilityMeta meta = metaOf(user);

        // Calculating when the last swing was
        long lastSwing = meta.lastSwing;
        long swingDifference = System.currentTimeMillis() - lastSwing;
        meta.lastSwing = System.currentTimeMillis();

        Queue<Long> placements = meta.places;

        // When the check is disabled, there is no need to check
        if (checkDeactivated(user, swingDifference)) {
            placements.clear();
            return;
        }

        if (placements.isEmpty()) {
            meta.started = System.currentTimeMillis();
        }
        placements.add(swingDifference);

        if (placements.size() >= BUFFER_LENGTH) {
            long length = System.currentTimeMillis() - meta.started;

            double standardDeviation = standardDeviation(placements);
            // Necessary for the statistically low variance check
            meta.deviations.add((long) standardDeviation);
            placements.clear();
        }

        // After we got 5 deviation samples, we are going to check the deviation of these samples, if it's too low, the player is performing a long-term consistency
        if (meta.deviations.size() >= 5) {
            double std = standardDeviation(meta.deviations);

            long length = System.currentTimeMillis() - meta.started;

            if (std < 10 && length < 4000) {
                int vlAdd = 1;
                meta.vl += vlAdd;
                if (meta.vl > 2) {
                    Violation violation = Violation.builderFor(PlacementAnalysis.class)
                            .forPlayer(player).withDefaultThreshold()
                            .withMessage(COMMON_FLAG_MESSAGE)
                            .withDetails("clicking stability")
                            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
                            .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
                            .withVL(2.5).build();
                    Modules.violationProcessor().processViolation(violation);

                    if (meta.vl > 6) {
                        //dmc45
                        user.nerfPermanently(AttackNerfStrategy.GARBAGE_HITS, "45");
                        user.nerf(AttackNerfStrategy.APPLY_LESS_KNOCKBACK, "45");
                        user.nerf(AttackNerfStrategy.CRITICALS, "45");
                        meta.vl -= 0.2;
                        meta.vl *= 0.98;
                    }
                }
            } else if (meta.vl > 0) {
                meta.vl -= 0.2;
                meta.vl *= 0.98;
            }
            meta.deviations.clear();
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

    public static class StabilityMeta extends CheckCustomMetadata {
        private final Queue<Long> places = new ArrayDeque<>();
        private final Queue<Long> deviations = new ArrayDeque<>();
        private double vl = 0;
        private long lastSwing = 0;
        private long started = System.currentTimeMillis();
    }
}
