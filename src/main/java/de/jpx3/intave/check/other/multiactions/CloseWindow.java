package de.jpx3.intave.check.other.multiactions;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.check.movement.physics.Simulators;
import de.jpx3.intave.check.other.MultiActions;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class CloseWindow extends CheckPart<MultiActions> {
    public CloseWindow(MultiActions parentCheck) {
        super(parentCheck);
    }

    @PacketSubscription(
            packetsIn = {
                    CLOSE_WINDOW
            }
    )
    public void receiveWindowClose(PacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);
        MetadataBundle meta = user.meta();

        MovementMetadata movement = meta.movement();
        int keyForward = movement.keyForward;
        int keyStrafe = movement.keyStrafe;

        if (movement.simulator() == Simulators.ELYTRA) {
            return;
        }

        if (movement.inWeb
                || movement.receivedFlyingPacketIn(2)
                || Collision.rasterizedTypeSearch(user, movement.boundingBox(), BlockTypeAccess.NETHER_PORTAL)
        ) {
            return;
        }

        double distanceMoved = Hypot.fast(movement.motionX(), movement.motionZ());
        double distanceRequirement = movement.isSneaking() ? 0.04 : 0.1;
        
        if ((keyForward != 0 || keyStrafe != 0) && distanceMoved > distanceRequirement 
                || movement.isSprinting() && movement.pose() != Pose.SWIMMING) {
            String message = "inventory close whilst walking";
            Violation violation = Violation.builderFor(MultiActions.class)
                    .forPlayer(player).withMessage(message)
                    .withVL(1)
                    .build();
            Modules.violationProcessor().processViolation(violation);
        }
    }
}