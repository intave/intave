package de.jpx3.intave.player.fake.action;

import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.player.fake.MetadataAccess;
import de.jpx3.intave.player.fake.event.EntityVelocityCache;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.player.fake.FakePlayer.SPAWN_HEALTH_STATE;

public final class HurtAnimationAction extends Action {
  private static final EntityVelocityCache entityVelocityCache = IntavePlugin.singletonInstance().fakePlayerEventService().entityVelocityCache();
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

  private void sendHurtAnimation() {
    send(new WrapperPlayServerEntityAnimation(
      this.fakePlayer.identifier(),
      WrapperPlayServerEntityAnimation.EntityAnimationType.HURT
    ));
    sendHealthUpdate(Math.max(1, currentHealthState - ThreadLocalRandom.current().nextInt(1, 4)));
  }

  private void sendEntityVelocity() {
    double motionX = randomHorizontalVelocity();
    double motionY = randomVerticalVelocity();
    double motionZ = randomHorizontalVelocity();
    send(new WrapperPlayServerEntityVelocity(this.fakePlayer.identifier(), new Vector3d(motionX, motionY, motionZ)));
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
