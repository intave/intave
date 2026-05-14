package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import org.bukkit.entity.Player;

public final class InvalidDig extends CheckPart<ProtocolScanner> {
    public InvalidDig(ProtocolScanner parentCheck) {
        super(parentCheck);
    }

    @PacketSubscription(
            packetsIn = {
                    PacketId.Client.BLOCK_DIG
            }
    )
    public void receiveBlockDig(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        Player player = event.getPlayer();
        EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().readSafely(0);
        BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
        EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
        int enumDirection = direction == null ? 0 : direction.ordinal();

        if (digType != EnumWrappers.PlayerDigType.START_DESTROY_BLOCK
                && digType != EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK
                && digType != EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK
        ) {

            final int expectedFace = !MinecraftVersions.VER1_8_0.atOrAbove() && digType == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM ? 255 : 0;

            if (enumDirection != expectedFace
                    || blockPosition.getX() != 0
                    || blockPosition.getY() != 0
                    || blockPosition.getZ() != 0
                    || packet.getIntegers().read(0) != 0
            ) {
                Violation violation = Violation.builderFor(ProtocolScanner.class)
                        .forPlayer(player).withMessage("sent invalid digging").withDetails("type " + digType.name().toLowerCase())
                        .withVL(100)
                        .build();
                Modules.violationProcessor().processViolation(violation);
            }
        }
    }
}