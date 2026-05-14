package de.jpx3.intave.check.other.multiactions;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.MultiActions;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MetadataBundle;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class BlockDig extends CheckPart<MultiActions> {
    public BlockDig(MultiActions parentCheck) {
        super(parentCheck);
    }

    @PacketSubscription(
            packetsIn = {
                    BLOCK_DIG
            }
    )
    public void receiveBlockDig(PacketEvent event) {
        Player player = event.getPlayer();
        User user = UserRepository.userOf(player);
        MetadataBundle meta = user.meta();
        PacketContainer packet = event.getPacket();
        EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().readSafely(0);

        if (digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK
                || digType == EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK
                || digType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK
        ) {
            if (meta.inventory().handActive()) {
                String message = "dig while item using";
                String details = "type " + digType.name().toLowerCase();
                Violation violation = Violation.builderFor(MultiActions.class)
                        .forPlayer(player).withMessage(message).withDetails(details)
                        .withVL(1)
                        .build();
                Modules.violationProcessor().processViolation(violation);
                event.setCancelled(true);
            }
        }
    }
}