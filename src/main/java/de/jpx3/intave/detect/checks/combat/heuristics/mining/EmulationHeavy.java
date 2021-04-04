package de.jpx3.intave.detect.checks.combat.heuristics.mining;

import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.fakeplayer.movement.types.RealEntityMovement;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaSynchronizeData;

import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.command.stages.IntaveCommandStage.locationBehind;

public final class EmulationHeavy extends MiningStrategyExecutor {
  public EmulationHeavy(User user) {
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
        .setMovement(new RealEntityMovement())
        .setInvisible(false)
        .setInTablist(true)
        .setEquipArmor(true)
        .setEquipHeldItem(true)
        .setParentPlayer(user().player())
        .setTimeout(-1)
        .setAttackSubscriber(() -> saveAnomalyWithID(3))
        .build();
      fakePlayer.spawn(locationBehind(user(), ThreadLocalRandom.current().nextInt(1, 2)));
    });
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
    return MiningStrategy.EMULATION_HEAVY;
  }
}