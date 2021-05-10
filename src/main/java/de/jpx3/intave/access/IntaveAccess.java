package de.jpx3.intave.access;

import de.jpx3.intave.access.check.CheckAccess;
import de.jpx3.intave.access.check.UnknownCheckException;
import de.jpx3.intave.access.player.PlayerAccess;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import de.jpx3.intave.access.server.ServerAccess;
import org.bukkit.entity.Player;

import java.io.PrintStream;
import java.util.function.BiConsumer;

public interface IntaveAccess {
  void setTrustFactorResolver(TrustFactorResolver resolver);
  void setDefaultTrustFactor(TrustFactor defaultTrustFactor);

  void subscribeOutputStream(PrintStream stream);
  void unsubscribeOutputStream(PrintStream stream);

  void subscribeINX(BiConsumer<Object, Object> context);

  PlayerAccess player(Player player);
  ServerAccess server();
  CheckAccess check(String checkName) throws UnknownCheckException;
}
