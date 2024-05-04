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
import java.util.Queue;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ARM_ANIMATION;

@Relocate
public final class Outliers extends MetaCheckPart<ClickPatterns, Outliers.OutliersMeta> {
    private static final int BUFFER_TIMEOUT = 4000;
    private static final int BUFFER_LENGTH = 50;

    public Outliers(ClickPatterns parentCheck) {
        super(parentCheck, OutliersMeta.class);
    }

    @PacketSubscription(
            packetsIn = ARM_ANIMATION
    )
    public void receiveSwing(PacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        OutliersMeta meta = metaOf(user);

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

            int outliers = (int) attacks.stream()
                    .filter(delay -> delay > 3)
                    .count();

            if (outliers < 2 && length < 4000) {
                int vlAdd = outliers < 10 ? 2 : 1;
                meta.vl += vlAdd;
                if (meta.vl > 1) {
                    parentCheck().makeDetection(
                            player,
                            "low outliers",
                            "o:" + formatDouble(outliers, 3) + " t:" + formatDouble(length / 1000d, 2),
                            meta.vl > 0 ? 10 : 0
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

    public static class OutliersMeta extends CheckCustomMetadata {
        private final Queue<Long> attacks = new ArrayDeque<>();

        private double vl = 0;
        private long lastSwing = 0;
        private long started = System.currentTimeMillis();
    }
}
