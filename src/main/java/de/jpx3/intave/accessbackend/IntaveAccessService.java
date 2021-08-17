package de.jpx3.intave.accessbackend;

import com.google.common.base.Preconditions;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.access.IntaveEvent;
import de.jpx3.intave.access.check.Check;
import de.jpx3.intave.access.check.CheckAccess;
import de.jpx3.intave.access.check.UnknownCheckException;
import de.jpx3.intave.access.player.PlayerAccess;
import de.jpx3.intave.access.player.UnknownPlayerException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import de.jpx3.intave.access.server.ServerAccess;
import de.jpx3.intave.accessbackend.check.CheckAccessor;
import de.jpx3.intave.accessbackend.player.PlayerAccessor;
import de.jpx3.intave.accessbackend.server.ServerAccessor;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.io.PrintStream;
import java.util.function.BiConsumer;

/**
 * Created by Jpx3 on 01.12.2017.
 */

public final class IntaveAccessService {
  private final IntavePlugin plugin;
  private final CheckAccessor checkAccessor;
  private final PlayerAccessor playerAccessor;
  private final ServerAccessor serverAccessor;

  public IntaveAccessService(IntavePlugin plugin) {
    this.plugin = plugin;
    this.checkAccessor = new CheckAccessor(plugin);
    this.playerAccessor = new PlayerAccessor(plugin);
    this.serverAccessor = new ServerAccessor(plugin);
  }

  public void setup() {
    plugin.setAccess(newIntaveAccess());
  }

  public void fireEvent(IntaveEvent externalEvent) {
    plugin.eventLinker().fireEvent(externalEvent);
  }

  private IntaveAccess newIntaveAccess() {
    return new IntaveAccess() {
      @Override
      @Native
      public void setTrustFactorResolver(TrustFactorResolver resolver) {
        plugin.trustFactorService().setTrustFactorResolver(resolver);
      }

      @Override
      @Native
      public void setDefaultTrustFactor(TrustFactor defaultTrustFactor) {
        plugin.trustFactorService().setDefaultTrustFactor(defaultTrustFactor);
      }

      @Override
      public void subscribeOutputStream(PrintStream stream) {
        IntaveLogger.logger().addOutputStream(stream);
      }

      @Override
      public void unsubscribeOutputStream(PrintStream stream) {
        IntaveLogger.logger().removeOutputStream(stream);
      }

      @Override
      @Native
      public void subscribeINX(BiConsumer<Object, Object> biConsumer) {}

      @Override
      public PlayerAccess player(Player player) {
        Preconditions.checkNotNull(player);
        if (!UserRepository.hasUser(player)) {
          throw new UnknownPlayerException("Player " + player.getName() + " couldn't be found");
        }
        return playerAccessor.playerAccessOf(player);
      }

      @Override
      public ServerAccess server() {
        return serverAccessor.serverAccess();
      }

      @Override
      public CheckAccess check(String checkName) throws UnknownCheckException {
        return checkAccessor.checkMirrorOf(checkName);
      }

      @Override
      public CheckAccess check(Check check) {
        return check(check.typeName());
      }
    };
  }

  public PlayerAccessor playerAccessor() {
    return playerAccessor;
  }

  public ServerAccessor serverAccessor() {
    return serverAccessor;
  }
}
