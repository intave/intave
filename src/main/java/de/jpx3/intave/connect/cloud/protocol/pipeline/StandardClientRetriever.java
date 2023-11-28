package de.jpx3.intave.connect.cloud.protocol.pipeline;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.connect.cloud.Session;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.Packet;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;
import de.jpx3.intave.connect.cloud.protocol.packets.*;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.Classifier;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

import static de.jpx3.intave.connect.cloud.protocol.Direction.CLIENTBOUND;

public final class StandardClientRetriever extends ChannelInboundHandlerAdapter implements Clientbound {
  private final Session session;

  public StandardClientRetriever(Session session) {
    this.session = session;
  }

  @Override
  public void channelRead(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
    if (o instanceof Packet) {
      Packet<?> packet = (Packet<?>) o;
      if (packet.direction() == CLIENTBOUND) {
        onSelect(packet);
      }
    }
  }

  @Override
  public void onCloseConnection(ClientboundDisconnect packet) {
    System.out.println("Connection closed: " + packet.reason());
    session.close();
  }

  @Override
  public void onClientHello(ClientboundHello packet) {
    throw new RuntimeException("Unexpected packet " + packet.name());
  }

  @Override
  public void onKeepAlive(ClientboundKeepAlive packet) {
    // do nothing
  }

  @Override
  public void onSetTrustfactor(ClientboundSetTrustfactor packet) {
    session.serveTrustfactorRequest(packet.id(), packet.trustFactor());
  }

  @Override
  public void onDownloadStorage(ClientboundDownloadStorage packet) {
    session.serveStorageRequest(packet.id(), packet.data());
  }

  @Override
  public void onLogReceive(ClientboundLogReceive packet) {
    session.serverUploadPlayerLogsRequest(packet.id(), packet.packetNonceResult(), packet.logId());
  }

  @Override
  public void onSampleTransmissionAcknowledgement(ClientboundSampleTransmissionAcknowledgement packet) {
    session.serveSampleTransmissionRequest(
      packet.identity(),
      packet.state() == ClientboundSampleTransmissionAcknowledgement.AcceptedState.ACCEPTED,
      Classifier.UNKNOWN
    );
  }

  @Override
  public void onShardsPacket(ClientboundShardsPacket packet) {
    session.onShardsAddition(packet.shards());
  }

  @Override
  public void onViolation(ClientboundViolation packet) {
    Identity id = packet.id();
    String name = id.name();
    UUID uuid = id.id();
    Player player = uuid != null ? Bukkit.getPlayer(uuid) : Bukkit.getPlayerExact(name);
    if (player == null) {
      return;
    }
    try {
      IntavePlugin intave = IntavePlugin.singletonInstance();
      CheckService checks = intave.checks();
      Check check = checks.searchCheck(packet.check());
      Violation violation = Violation.builderFor(check.getClass())
        .forPlayer(player)
        .withCustomThreshold(packet.threshold())
        .withMessage(packet.message())
        .withDetails(packet.details())
        .withVL(packet.vl())
        .build();
      ViolationProcessor violationProcessor = Modules.violationProcessor();
      violationProcessor.processViolation(violation);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }
}
