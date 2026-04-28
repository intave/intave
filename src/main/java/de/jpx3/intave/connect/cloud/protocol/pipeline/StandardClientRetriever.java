package de.jpx3.intave.connect.cloud.protocol.pipeline;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.connect.cloud.Session;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.Packet;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;
import de.jpx3.intave.connect.cloud.protocol.packets.*;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.nayoro.Classifier;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.security.LicenseAccess;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;

import static de.jpx3.intave.connect.cloud.protocol.Direction.CLIENTBOUND;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VERSION_DETAILS;

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
  public void onCommand(ClientboundCommand packet) {

  }

  @Override
  public void onCombatModifier(ClientboundCombatModifier packet) {
    Identity identity = packet.identity();
    Player player = Bukkit.getPlayer(identity.id());
    if (player == null) {
      player = Bukkit.getPlayerExact(identity.name());
    }
    if (player == null) {
      return;
    }
    User user = UserRepository.userOf(player);
    AttackNerfStrategy strat = AttackNerfStrategy.byName(packet.modifier());
    if (strat == null) {
      return;
    }
    switch (packet.duration()) {
      case 0:
        user.nerfOnce(strat, "cc");
        break;
      case 1:
        user.nerf(strat, "cc");
        break;
      case 2:
        user.nerfPermanently(strat, "cc");
        break;
    }
  }

  @Override
  public void onDownloadStorage(ClientboundDownloadStorage packet) {
    session.serveStorageRequest(packet.id(), packet.data());
  }

  @Override
  public void onInquiryResponse(ClientboundInquiryResponse packet) {
    session.serveInquiryResponse(packet.requestId(), packet.response());
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
