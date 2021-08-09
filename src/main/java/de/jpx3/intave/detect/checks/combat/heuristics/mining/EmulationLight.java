package de.jpx3.intave.detect.checks.combat.heuristics.mining;

import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.fakeplayer.movement.PositionRotationLookup;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMeta;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.Location;

import java.util.concurrent.ThreadLocalRandom;

public final class EmulationLight extends MiningStrategyExecutor {
  public EmulationLight(User user) {
    super(user);
  }

  @Override
  protected void setup() {
    UserMeta meta = user().meta();
    UserMetaAttackData attackData = meta.attackData();
    if (attackData.fakePlayer() != null) {
      return;
    }
    BackgroundExecutor.execute(() -> {
      FakePlayer fakePlayer = FakePlayer
        .builderFor(user().player())
        .floating()
        .invisible()
        .invisibleInTabList()
        .attackSubscribe(x -> saveAnomalyWithID(1))
        .build();

      fakePlayer.create(locationBehind(user()));
    });
  }

  public static Location locationBehind(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    float rotationYaw = movementData.rotationYaw;
    Location location = movementData.verifiedLocation().clone();
    location.setYaw(rotationYaw);
    location = PositionRotationLookup.lookup(location, ThreadLocalRandom.current().nextDouble(6.0, 8.0));
    location.add(0.0, 2.0, 0.0);
    return location;
  }

  @Override
  protected void stopStrategy() {
    UserMetaAttackData attackData = user().meta().attackData();
    FakePlayer fakePlayer = attackData.fakePlayer();
    if (fakePlayer != null) {
      fakePlayer.remove();
    }
  }

  @Override
  public MiningStrategy miningStrategy() {
    return MiningStrategy.EMULATION_LIGHT;
  }
}