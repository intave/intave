package de.jpx3.intave.connect.cloud.request;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import de.jpx3.intave.connect.cloud.Cloud;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public final class CloudTrustfactorResolver implements TrustFactorResolver {
  private final Cloud cloud;

  public CloudTrustfactorResolver(Cloud cloud) {
    this.cloud = cloud;
  }

  @Override
  public void resolve(Player player, Consumer<TrustFactor> callback) {
    cloud.trustfactorRequest(player, callback);
  }

  @Override
  public String toString() {
    return "CloudTrustfactorResolver";
  }
}
