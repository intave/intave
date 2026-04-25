package de.jpx3.intave.check.combat.heuristics.detect.unused;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;

import static com.comphenix.protocol.PacketType.Play.Server.POSITION;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.DELAY_16s;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.LIMIT_2;
import static de.jpx3.intave.math.MathHelper.diff;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class TestHeuristic extends MetaCheckPart<Heuristics, TestHeuristic.TestMeta> {
  public TestHeuristic(Heuristics parentCheck) {
    super(parentCheck, TestMeta.class);

    int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(IntavePlugin.singletonInstance(), () -> {
      Bukkit.getOnlinePlayers().forEach(this::sendTeleportPacket);
    }, 3, 3);
    TaskTracker.begun(taskId);
  }

  public void sendTeleportPacket(Player player) {
    ProtocolManager protocol = ProtocolLibrary.getProtocolManager();
    PacketContainer packet = protocol.createPacket(POSITION);
//    User user = userOf(player);
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      packet.getIntegers().write(0, 0xAAAA4444);
    }
    Set<?> allTeleportFlags = Relative.setOfAllFlags();
    packet.getSpecificModifier(Set.class).write(0, allTeleportFlags);
    // add teleport flags
    userOf(player).ignoreNextOutboundPacket();
    protocol.sendServerPacket(player, packet);
    userOf(player).receiveNextOutboundPacketAgain();
  }

  @PacketSubscription(
    packetsIn = {
      POSITION_LOOK
    },
    priority = ListenerPriority.LOWEST
  )
  public void receivePacket(ProtocolPacketEvent event) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();
    User user = userOf(player);
    StructureModifier<Boolean> booleans = packet.getBooleans();
    Boolean onGround = booleans.read(0);
    Boolean hasLook = booleans.read(1);
    Boolean hasMovement = booleans.read(2);
    if (!onGround && hasLook && hasMovement) {
      StructureModifier<Double> doubles = packet.getDoubles();
      double positionX = doubles.read(0);
      double positionY = doubles.read(1);
      double positionZ = doubles.read(2);
      MovementMetadata movement = user.meta().movement();
      double lastPositionX = movement.positionX();
      double lastPositionY = movement.positionY();
      double lastPositionZ = movement.positionZ();
      if (positionX == lastPositionX &&
        positionY == lastPositionY &&
        positionZ == lastPositionZ
      ) {
        StructureModifier<Float> floats = packet.getFloat();
        float rotationYaw = floats.read(0);
        float rotationPitch = floats.read(1);
        double distance = diff(rotationYaw, movement.rotationYaw()) + diff(rotationPitch, movement.rotationPitch());
//        player.sendMessage(ChatColor.GREEN + "Teleport distance " + distance);
        event.setCancelled(true);
      }
    }
  }

  public void violation(Player player) {
    Anomaly anomaly = Anomaly.anomalyOf("999", Confidence.PROBABLE, Anomaly.Type.KILLAURA, "", LIMIT_2 | DELAY_16s);
    parentCheck().saveAnomaly(player, anomaly);
  }

  public class TestMeta extends CheckCustomMetadata {
    int value = 0;
  }
}