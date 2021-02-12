package de.jpx3.intave.access;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

public interface TrustFactorResolver {
  void lazyResolve(Player player, Consumer<TrustFactor> callback);
}
