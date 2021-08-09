package de.jpx3.intave.trustfactor;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public final class DefaultTrustFactorResolver implements TrustFactorResolver {
  @Override
  public void resolve(Player player, Consumer<TrustFactor> callback) {

  }
}
