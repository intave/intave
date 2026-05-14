package de.jpx3.intave.check.other.multiactions;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.MultiActions;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MetadataBundle;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class ArmAnimation extends CheckPart<MultiActions> {
    private long lastDropItem;

    public ArmAnimation(MultiActions parentCheck) {
        super(parentCheck);
    }

    @PacketSubscription(
            packetsIn = PacketId.Client.BLOCK_DIG
    )
    public void onBlockDigPacket() {
        lastDropItem = System.currentTimeMillis();
    }

    @PacketSubscription(
            packetsIn = {
                    ARM_ANIMATION
            }
    )
    public void receiveBlockDig(PacketEvent event) {
        Player player = event.getPlayer();
        User user = UserRepository.userOf(player);
        MetadataBundle meta = user.meta();

        if (meta.inventory().handActive() && System.currentTimeMillis() - lastDropItem > 100 && MinecraftVersions.VER1_8_0.atOrAbove()) {
            String message = "swing hand while item using";
            String details = "ticks " + meta.inventory().handActiveTicks;
            Violation violation = Violation.builderFor(MultiActions.class)
                    .forPlayer(player).withMessage(message).withDetails(details)
                    .withVL(1)
                    .build();
            Modules.violationProcessor().processViolation(violation);
            event.setCancelled(true);
        }
    }
}