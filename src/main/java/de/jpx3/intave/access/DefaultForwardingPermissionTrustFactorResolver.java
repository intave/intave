package de.jpx3.intave.access;

import de.jpx3.intave.permission.PermissionCheck;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

public final class DefaultForwardingPermissionTrustFactorResolver implements TrustFactorResolver {
  private final TrustFactorResolver defaultResolver;

  public DefaultForwardingPermissionTrustFactorResolver(TrustFactorResolver defaultResolver) {
    this.defaultResolver = defaultResolver;
  }

  @Override
  public void lazyResolve(Player player, Consumer<TrustFactor> callback) {
    Optional<TrustFactor> resolvedTrustFactor =
      Arrays.stream(TrustFactor.values())
      .filter(trustFactor -> hasPermissionFor(player, trustFactor))
      .findFirst();

    if(resolvedTrustFactor.isPresent()) {
      callback.accept(resolvedTrustFactor.get());
    } else {
      defaultResolver.lazyResolve(player, callback);
    }
  }

  private boolean hasPermissionFor(Player player, TrustFactor trustFactor) {
    return PermissionCheck.permissionCheck(player, trustFactor.permission());
  }
}
