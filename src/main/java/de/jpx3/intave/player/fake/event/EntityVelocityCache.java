package de.jpx3.intave.player.fake.event;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityVelocityReader;
import de.jpx3.intave.packet.view.EntityVelocityView;
import de.jpx3.intave.packet.view.PacketEventsEntityVelocityView;
import de.jpx3.intave.packet.view.ProtocolLibEntityVelocityView;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_VELOCITY;

public final class EntityVelocityCache implements PacketEventSubscriber {
  private static final double VELOCITY_CONVERT_FACTOR = 8000.0D;
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
  public void receiveEntityVelocity(
    User user, EntityVelocityReader reader
  ) {
    // The reader is not released here: the ProtocolLib path never owned it, the subscription
    // linker hands in a pooled reader and takes it back once this method returns.
    handleEntityVelocity(
      user,
      new ProtocolLibEntityVelocityView(user.hasPlayer() ? user.player() : null, reader)
    );
  }

  /**
   * PacketEvents entry point. Mirrors the ProtocolLib subscription above; nothing is written back
   * here, the view only reads the knockback.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      ENTITY_VELOCITY
    }
  )
  public void receiveEntityVelocity(PacketSendEvent event) {
    PacketEventsEntityVelocityView view = PacketEventsEntityVelocityView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleEntityVelocity(UserRepository.userOf(player), view);
    view.release();
  }

  /** Engine independent velocity sampling; see {@link EntityVelocityView}. */
  private void handleEntityVelocity(User user, EntityVelocityView view) {
    FakePlayer fakePlayer = user.meta().attack().fakePlayer();
    int entityId = view.entityId();
    double motionX = view.motionX();
    double motionY = view.motionY();
    double motionZ = view.motionZ();
    if (horizontalVelocities.size() < 10) {
      registerHorizontalVelocity(motionX);
      registerHorizontalVelocity(motionZ);
    }
    if (verticalVelocities.size() < 10) {
      registerVerticalVelocity(motionY);
    }
    if (fakePlayer != null && user.hasPlayer() && entityId == user.player().getEntityId()) {
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