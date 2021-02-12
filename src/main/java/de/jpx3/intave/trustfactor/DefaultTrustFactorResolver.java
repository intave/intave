package de.jpx3.intave.trustfactor;

import de.jpx3.intave.access.TrustFactor;
import de.jpx3.intave.access.TrustFactorResolver;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public final class DefaultTrustFactorResolver implements TrustFactorResolver {

  /*

    - client version
       1.8 - likely
       1.9 - 1.16 less likely

    - labymod/lunar/badlion
       less likely
       -> check spoof

    - proxy socks4/socks5 check

   */

  @Override
  public void lazyResolve(Player player, Consumer<TrustFactor> callback) {





  }

  public static class Options {

    public static final int CLIENT_VERSION = 1 << 0;
    public static final int CLIENT_IDENTIFICATION = 1 << 1;
    public static final int PROFILE_NAME_HISTORY = 1 << 2;


  }
}
