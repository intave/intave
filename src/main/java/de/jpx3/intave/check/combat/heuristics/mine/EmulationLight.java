package de.jpx3.intave.check.combat.heuristics.mine;

import de.jpx3.intave.check.combat.heuristics.MiningStrategy;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.player.fake.movement.PositionRotationLookup;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;

import java.util.concurrent.ThreadLocalRandom;

public final class EmulationLight extends MiningStrategyExecutor {
  public EmulationLight(User user) {
    super(user);
  }

  @Override
  protected void setup() {
    MetadataBundle meta = user().meta();
    AttackMetadata attackData = meta.attack();
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
    MovementMetadata movementData = user.meta().movement();
    float rotationYaw = movementData.rotationYaw;
    Location location = movementData.verifiedLocation().clone();
    location.setYaw(rotationYaw);
    location = PositionRotationLookup.lookup(location, ThreadLocalRandom.current().nextDouble(6.0, 8.0));
    location.add(0.0, 2.0, 0.0);
    return location;
  }

  @Override
  protected void stopStrategy() {
    AttackMetadata attackData = user().meta().attack();
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