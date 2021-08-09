package de.jpx3.intave.detect.checks.combat.heuristics.mining;

import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMeta;
import de.jpx3.intave.user.UserMetaAttackData;

import static de.jpx3.intave.detect.checks.combat.heuristics.mining.EmulationLight.locationBehind;

public final class EmulationHeavy extends MiningStrategyExecutor {
  public EmulationHeavy(User user) {
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
        .walking()
        .visible()
        .visibleInTabList()
        .equipArmor()
        .equipHeldItem()
        .attackSubscribe(x -> saveAnomalyWithID(3))
        .build();
      fakePlayer.create(locationBehind(user()));
    });
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
    return MiningStrategy.EMULATION_HEAVY;
  }
}