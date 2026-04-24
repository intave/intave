package de.jpx3.intave.player.fake.event;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_VELOCITY;

public final class EntityVelocityCache implements PacketEventSubscriber {
  private final List<Double> horizontalVelocities = Lists.newArrayList();
  private final List<Double> verticalVelocities = Lists.newArrayList();

  public EntityVelocityCache(IntavePlugin plugin) {
    Modules.linker().packetEvents().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    packetsOut = {
      ENTITY_VELOCITY
    }
  )
  public void receiveEntityVelocity(ProtocolPacketEvent event, WrapperPlayServerEntityVelocity packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    FakePlayer fakePlayer = user.meta().attack().fakePlayer();
    int entityID = packet.getEntityId();
    Vector3d velocity = packet.getVelocity();
    double motionX = velocity.x;
    double motionY = velocity.y;
    double motionZ = velocity.z;
    if (horizontalVelocities.size() < 10) {
      registerHorizontalVelocity(motionX);
      registerHorizontalVelocity(motionZ);
    }
    if (verticalVelocities.size() < 10) {
      registerVerticalVelocity(motionY);
    }
    if (fakePlayer != null && entityID == player.getEntityId()) {
      notifyFakePlayer(fakePlayer, motionX, motionY, motionZ);
    }
  }

  private void notifyFakePlayer(
    FakePlayer fakePlayer,
    double velocityX, double velocityY, double velocityZ
  ) {
    fakePlayer.registerParentPlayerVelocity(velocityX, velocityY, velocityZ);
  }

  private void registerHorizontalVelocity(double velocity) {
    if (!horizontalVelocities.contains(velocity)) {
      horizontalVelocities.add(velocity);
    }
  }

  private void registerVerticalVelocity(double velocity) {
    if (!verticalVelocities.contains(velocity)) {
      verticalVelocities.add(velocity);
    }
  }

  public List<Double> horizontalVelocities() {
    return horizontalVelocities;
  }

  public List<Double> verticalVelocities() {
    return verticalVelocities;
  }
}
