package de.jpx3.intave.connect.shadow;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.tools.sync.Synchronizer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.server.v1_8_R3.PacketDataSerializer;
import net.minecraft.server.v1_8_R3.PacketPlayInCustomPayload;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

@PatchyAutoTranslation
public final class v8PipelineHandler extends ChannelInboundHandlerAdapter {
  private final UUID id;
  private LabymodShadowIntegration parentIntegration;

  public v8PipelineHandler(UUID id, LabymodShadowIntegration parentIntegration) {
    this.id = id;
    this.parentIntegration = parentIntegration;
  }

  @Override
  @PatchyAutoTranslation
  public void channelRead(ChannelHandlerContext context, Object packet) throws Exception {
    if (packet instanceof PacketPlayInCustomPayload) {
      PacketPlayInCustomPayload cppPacket = (PacketPlayInCustomPayload) packet;
      String channel = cppPacket.a();
      if (channel.equals("SHADOW")) {
        PacketDataSerializer data = cppPacket.b();
        int id = data.readByte();

        PacketPlayInFlying emulatedPacket;

        switch (id) {
          case 0: // PacketPlayInPosition
            PacketPlayInFlying.PacketPlayInPosition packetPlayInPosition = new PacketPlayInFlying.PacketPlayInPosition();
            packetPlayInPosition.a(data);
            emulatedPacket = packetPlayInPosition;
            break;
          case 1: // PacketPlayInLook
            PacketPlayInFlying.PacketPlayInLook packetPlayInLook = new PacketPlayInFlying.PacketPlayInLook();
            packetPlayInLook.a(data);
            emulatedPacket = packetPlayInLook;
            break;
          case 2: // PacketPlayInPositionLook
            PacketPlayInFlying.PacketPlayInPositionLook packetPlayInPositionLook = new PacketPlayInFlying.PacketPlayInPositionLook();
            packetPlayInPositionLook.a(data);
            emulatedPacket = packetPlayInPositionLook;
            break;
          case 3: // PacketPlayInFlying
            PacketPlayInFlying packetPlayInFlying = new PacketPlayInFlying();
            packetPlayInFlying.a(data);
            emulatedPacket = packetPlayInFlying;
            break;
          default:
            Synchronizer.synchronize(() -> player().kickPlayer("Shadow context failure"));
            return;
        }

        long time = data.readLong();
        double moveForward = data.readDouble();
        double moveStrafe = data.readDouble();
        boolean jump = data.readBoolean();
        boolean sneak = data.readBoolean();
        double x = data.readDouble();
        double y = data.readDouble();
        double z = data.readDouble();
        float yaw = data.readFloat();
        float pitch = data.readFloat();
        boolean sprinting = data.readBoolean();
        int packetCounter = data.readInt();

        ShadowContext shadowContext = new ShadowContext(
          time,
          moveForward,
          moveStrafe,
          jump, sneak,
          x, y, z,
          yaw, pitch,
          sprinting,
          packetCounter
        );

        parentIntegration.pushPacket(player(), emulatedPacket, shadowContext);
        super.channelRead(context, emulatedPacket);
        return;
      }
    }
    super.channelRead(context, packet);
  }

  public Player player() {
    return Bukkit.getPlayer(id);
  }
}
