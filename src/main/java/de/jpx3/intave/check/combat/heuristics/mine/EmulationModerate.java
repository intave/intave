package de.jpx3.intave.check.combat.heuristics.mine;

import de.jpx3.intave.check.combat.heuristics.MiningStrategy;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;

import static de.jpx3.intave.check.combat.heuristics.mine.EmulationLight.locationBehind;
import static de.jpx3.intave.player.fake.FakePlayerAttribute.*;

public final class EmulationModerate extends MiningStrategyExecutor{
  public EmulationModerate(User user) {
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
        .acceptAttributes(ARMORED | IN_TABLIST | ITEM_IN_HAND)
        .attackSubscribe(x -> saveAnomalyWithID(2))
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
    return MiningStrategy.EMULATION_MODERATE;
  }
}