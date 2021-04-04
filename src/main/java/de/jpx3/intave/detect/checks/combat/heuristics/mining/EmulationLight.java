package de.jpx3.intave.detect.checks.combat.heuristics.mining;

import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.fakeplayer.movement.CameraUtils;
import de.jpx3.intave.fakeplayer.movement.types.ConvertEntityMovement;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserMetaSynchronizeData;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public final class EmulationLight extends MiningStrategyExecutor {
  public EmulationLight(User user) {
    super(user);
  }

  @Override
  protected void setup() {
    User.UserMeta meta = user().meta();
    UserMetaSynchronizeData synchronizeData = meta.synchronizeData();
    UserMetaAttackData attackData = meta.attackData();
    if (attackData.fakePlayer() != null) {
      return;
    }
    int entityIDAdd = ThreadLocalRandom.current().nextInt(10, 20);
    int entityID = synchronizeData.resolveEntityID(entityIDAdd);
    BackgroundExecutor.execute(() -> {
      FakePlayer fakePlayer = FakePlayer
        .builder()
        .setEntityID(entityID)
        .setMovement(new ConvertEntityMovement())
        .setInvisible(true)
        .setInTablist(false)
        .setEquipArmor(false)
        .setEquipHeldItem(false)
        .setParentPlayer(user().player())
        .setTimeout(-1)
        .setAttackSubscriber(() -> saveAnomalyWithID(1))
        .build();
      fakePlayer.spawn(locationBehind(user(), ThreadLocalRandom.current().nextInt(1, 2)));
    });
  }

  public static Location locationBehind(User user, double distance) {
    UserMetaMovementData movementData = user.meta().movementData();
    float rotationYaw = movementData.rotationYaw;
    Location location = movementData.verifiedLocation().clone();
    Vector direction = CameraUtils.getDirection(rotationYaw, 0.0f);
    location.add(direction.multiply(-distance));
    location.add(0.0, 2.0, 0.0);
    return location;
  }

  @Override
  protected void stopStrategy() {
    UserMetaAttackData attackData = user().meta().attackData();
    FakePlayer fakePlayer = attackData.fakePlayer();
    if (fakePlayer != null) {
      fakePlayer.despawn();
    }
  }

  @Override
  public MiningStrategy miningStrategy() {
    return MiningStrategy.EMULATION_LIGHT;
  }
}