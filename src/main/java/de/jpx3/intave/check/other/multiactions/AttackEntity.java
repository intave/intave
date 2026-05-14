package de.jpx3.intave.check.other.multiactions;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.MultiActions;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MetadataBundle;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class AttackEntity extends CheckPart<MultiActions> {
    public AttackEntity(MultiActions parentCheck) {
        super(parentCheck);
    }

    @PacketSubscription(
            packetsIn = {
                    USE_ENTITY, ATTACK_ENTITY
            }
    )
    public void receiveAttack(PacketEvent event) {
        Player player = event.getPlayer();
        User user = UserRepository.userOf(player);
        MetadataBundle meta = user.meta();
        PacketContainer packet = event.getPacket();
        EntityUseReader reader = PacketReaders.readerOf(packet);
        EnumWrappers.EntityUseAction useAction = reader.useAction();

        if (useAction == EnumWrappers.EntityUseAction.INTERACT
                || useAction == EnumWrappers.EntityUseAction.INTERACT_AT) return;

        if (meta.inventory().handActive()) {
            String message = "attack while item using";
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