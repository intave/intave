package de.jpx3.intave.check.combat.heuristics.mine;

import de.jpx3.intave.check.combat.heuristics.MiningStrategy;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;

import static de.jpx3.intave.check.combat.heuristics.mine.EmulationLight.locationBehind;

public final class EmulationHeavy extends MiningStrategyExecutor {
  public EmulationHeavy(User user) {
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
    AttackMetadata attackData = user().meta().attack();
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