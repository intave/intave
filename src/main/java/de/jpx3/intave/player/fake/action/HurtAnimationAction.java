package de.jpx3.intave.player.fake.action;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.player.fake.MetadataAccess;
import de.jpx3.intave.player.fake.event.EntityVelocityCache;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.player.fake.FakePlayer.SPAWN_HEALTH_STATE;

public final class HurtAnimationAction extends Action {
  private final static EntityVelocityCache entityVelocityCache = IntavePlugin.singletonInstance().fakePlayerEventService().entityVelocityCache();
  private float currentHealthState = SPAWN_HEALTH_STATE;
  private long lastNaturalHealthUpdate = System.currentTimeMillis();

  public HurtAnimationAction(Player player, FakePlayer fakePlayer) {
    super(Probability.MEDIUM, player, fakePlayer);
  }

  @Override
  public void perform() {
    sendHurtAnimation();
    sendEntityVelocity();
  }

  @Override
  public void performMissed() {
    long time = System.currentTimeMillis();
    long timePassed = time - lastNaturalHealthUpdate;
    long expectedTime = currentHealthState < 5 ? 1_000 : 3_000;
    if (timePassed > expectedTime && currentHealthState < 20) {
      sendHealthUpdate(currentHealthState);
      lastNaturalHealthUpdate = time;
    }
  }

  private final static byte DAMAGE_ANIMATION = 1;

  private void sendHurtAnimation() {
    PacketContainer packet = create(PacketType.Play.Server.ANIMATION);
    packet.getIntegers().writeSafely(0, this.fakePlayer.identifier());
    packet.getModifier().writeSafely(1, DAMAGE_ANIMATION);
    send(packet);
    sendHealthUpdate(Math.max(1, currentHealthState - ThreadLocalRandom.current().nextInt(1, 4)));
  }

  private final static double VELOCITY_CONVERT_FACTOR = 8000.0D;

  private void sendEntityVelocity() {
    PacketContainer packet = create(PacketType.Play.Server.ENTITY_VELOCITY);
    packet.getIntegers().writeSafely(0, this.fakePlayer.identifier());
    double motionX = randomHorizontalVelocity();
    double motionY = randomVerticalVelocity();
    double motionZ = randomHorizontalVelocity();
    packet.getIntegers().writeSafely(1, (int) (motionX * VELOCITY_CONVERT_FACTOR));
    packet.getIntegers().writeSafely(2, (int) (motionY * VELOCITY_CONVERT_FACTOR));
    packet.getIntegers().writeSafely(3, (int) (motionZ * VELOCITY_CONVERT_FACTOR));
    send(packet);
  }

  private void sendHealthUpdate(float health) {
    MetadataAccess.updateHealthFor(observer, fakePlayer, SPAWN_HEALTH_STATE);
    if (health != this.currentHealthState) {
      MetadataAccess.updateHealthFor(observer, fakePlayer, health);
    }
    this.currentHealthState = health;
  }

  private double randomHorizontalVelocity() {
    List<Double> horizontalVelocities = entityVelocityCache.horizontalVelocities();
    if (horizontalVelocities.size() <= 2) {
      return ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
    }
    int position = ThreadLocalRandom.current().nextInt(0, horizontalVelocities.size() - 1);
    return horizontalVelocities.get(position);
  }

  private double randomVerticalVelocity() {
    List<Double> verticalVelocities = entityVelocityCache.verticalVelocities();
    if (verticalVelocities.size() <= 2) {
      return ThreadLocalRandom.current().nextDouble(0.0, 0.4);
    }
    int position = ThreadLocalRandom.current().nextInt(0, verticalVelocities.size() - 1);
    return verticalVelocities.get(position);
  }
}