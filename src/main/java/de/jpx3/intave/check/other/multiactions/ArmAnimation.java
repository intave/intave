package de.jpx3.intave.check.other.multiactions;

import com.comphenix.protocol.events.PacketEvent;
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

public final class ArmAnimation extends CheckPart<MultiActions> {

    public ArmAnimation(MultiActions parentCheck) {
        super(parentCheck);
    }

    @PacketSubscription(
            packetsIn = {
                    ARM_ANIMATION
            }
    )
    public void receiveArmAnimation(PacketEvent event) {
        Player player = event.getPlayer();
        User user = UserRepository.userOf(player);
        MetadataBundle meta = user.meta();

        if (meta.inventory().handActive()) {
            // this is possible to false on 1.7
            if (user.protocolVersion() < 47) {
                return;
            }

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