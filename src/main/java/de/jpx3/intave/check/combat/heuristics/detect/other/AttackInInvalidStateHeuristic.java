package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.EntityShade;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

public final class AttackInInvalidStateHeuristic extends MetaCheckPart<Heuristics, AttackInInvalidStateHeuristic.AttackInInvalidStateMeta> {
    public AttackInInvalidStateHeuristic(Heuristics heuristics) {
        super(heuristics, AttackInInvalidStateMeta.class);
    }

    @PacketSubscription(
//    priority = ListenerPriority.HIGH,
            packetsIn = {
                    USE_ENTITY
            }
    )
    public void receiveAttack(PacketEvent event) {
        Player player = event.getPlayer();
        PacketContainer packet = event.getPacket();
        User user = userOf(player);
        ProtocolMetadata clientData = user.meta().protocol();
        EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
        if (action == null) {
            action = packet.getEnumEntityUseActions().read(0).getAction();
        }
        if (action != EnumWrappers.EntityUseAction.ATTACK) {
            return;
        }
        if (clientData.protocolVersion() <= VER_1_8) {
            checkGUIScreen(player);
        }
        checkDeadEntity(player, packet);
        checkBlocking(event);
    }

    private void checkBlocking(PacketEvent event) {
        Player player = event.getPlayer();
        User user = userOf(player);

        // On 1.9+ there is an error when switching to a consumable item for 1 tick
        if (user.protocolVersion() > 47) {
            ItemStack heldItem = user.meta().inventory().heldItem();
            if (heldItem != null) {
                // Swords are not consumable on 1.9+
                boolean sword = ItemProperties.isSwordItem(heldItem);
                boolean heldItemConsumable = ItemProperties.canItemBeUsed(player, heldItem) && !sword;
                if (user.meta().inventory().pastHotBarSlotChange < 2 && heldItemConsumable) {
                    return;
                }
            }
        }

        // not checked yet
        if (user.meta().inventory().handActive()) {
            player.sendMessage("active: " + user.meta().inventory().heldItemType());
            Anomaly anomaly = Anomaly.anomalyOf("162", Confidence.NONE, Anomaly.Type.KILLAURA, "attacked whilst using an item");
            parentCheck().saveAnomaly(player, anomaly);
            //dmc28
            user.applyAttackNerfer(AttackNerfStrategy.BLOCKING, "28");
//      user.applyAttackNerfer(AttackNerfStrategy.DMG_LIGHT, "28");

            sendStopUseItemPacketToServer(user);
        }
    }

    private void sendStopUseItemPacketToServer(User user) {
        Player player = user.player();
        if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
            return;
        } else {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Client.BLOCK_DIG);
            packet.getBlockPositionModifier().write(0, new BlockPosition(0, 0, 0));
            packet.getDirections().write(0, EnumWrappers.Direction.DOWN);
            packet.getPlayerDigTypes().write(0, EnumWrappers.PlayerDigType.RELEASE_USE_ITEM);

            userOf(player).ignoreNextInboundPacket();
            PacketSender.receiveClientPacketFrom(player, packet);
//        ProtocolLibrary.getProtocolManager().recieveClientPacket(player, packet, true);

            updatePlayerHandItem(player);
        }
        Synchronizer.synchronize(player::updateInventory);
    }

    private void updatePlayerHandItem(Player player) {
        User user = UserRepository.userOf(player);
        InventoryMetadata inventoryData = user.meta().inventory();
        inventoryData.deactivateHand();
    }

    private void checkGUIScreen(Player player) {
        User user = userOf(player);
        AttackInInvalidStateMeta meta = metaOf(user);
        ProtocolMetadata clientData = user.meta().protocol();
        AbilityMetadata abilityData = user.meta().abilities();
        float health = abilityData.health;
        if (health <= 0f) {
            long now = System.currentTimeMillis();
            long lastFlag = now - meta.lastGUIAttackTimestamps;
            int ticksAgo = abilityData.ticksToLastHealthUpdate;
            Confidence confidence = lastFlag > 1000 ? Confidence.PROBABLE : Confidence.NONE;
            String description = "attacked in gui screen (version " + clientData.versionString() + ") | ";
            description += "lastHealthUpdate: " + ticksAgo + ", ";
            description += "lastFlag " + lastFlag + " ms ago, ";
            description += "confidence " + confidence.level();
            Anomaly anomaly = Anomaly.anomalyOf("161", confidence, Anomaly.Type.KILLAURA, description);
            parentCheck().saveAnomaly(player, anomaly);
            meta.lastGUIAttackTimestamps = now;
        }
    }

    private void checkDeadEntity(Player player, PacketContainer packet) {
        User user = userOf(player);
        AttackMetadata attackData = user.meta().attack();
        ProtocolMetadata clientData = user.meta().protocol();
        EntityShade entity = attackData.lastAttackedEntity();
        if (entity == null || !entity.clientSynchronized || !entity.typeData().isLivingEntity()) {
            return;
        }
        if (clientData.protocolVersion() != VER_1_8) {
            return;
        }
        EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
        if (action == null) {
            action = packet.getEnumEntityUseActions().read(0).getAction();
        }
        if (action == EnumWrappers.EntityUseAction.ATTACK && entity.dead) {
            String description = "attacked a dead entity " + entity.entityName();
            Anomaly anomaly = Anomaly.anomalyOf("161", Confidence.NONE, Anomaly.Type.KILLAURA, description);
            parentCheck().saveAnomaly(player, anomaly);
        }
    }

    public static final class AttackInInvalidStateMeta extends CheckCustomMetadata {
        public long lastGUIAttackTimestamps;
    }
}