package de.jpx3.intave.accessbackend.internal;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.*;
import de.jpx3.intave.accessbackend.internal.check.CheckAccessor;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.sync.Synchronizer;

import java.io.PrintStream;
import java.util.List;

public final class IntaveInternalAccessor {
  private final IntavePlugin plugin;
  private final CheckAccessor checkAccessor;
  private IntaveInternalAccess internalAccess;

  public IntaveInternalAccessor(IntavePlugin plugin) {
    this.plugin = plugin;
    this.checkAccessor = new CheckAccessor(plugin);
  }

  public synchronized IntaveInternalAccess internalAccess() {
    if(internalAccess == null) {
      internalAccess = newInternalAccess();
    }
    return internalAccess;
  }

  private IntaveInternalAccess newInternalAccess() {
    return new IntaveInternalAccess() {
      @Override
      public void setTrustFactorResolver(TrustFactorResolver resolver) {
        plugin.trustFactorService().setTrustFactorResolver(resolver);
      }

      @Override
      public void setDefaultTrustFactor(TrustFactor defaultTrustFactor) {
        plugin.trustFactorService().setDefaultTrustFactor(defaultTrustFactor);
      }

      @Override
      public void overrideBreakPermissionCheck(BlockBreakPermissionCheck check) {
        plugin.interactionPermissionService().setBlockBreakPermissionCheck(check);
      }

      @Override
      public void overridePlacePermissionCheck(BlockPlacePermissionCheck check) {
        plugin.interactionPermissionService().setBlockPlacePermissionCheck(check);
      }

      @Override
      public void overrideInteractPermissionCheck(BlockInteractionPermissionCheck check) {

      }

      @Override
      public void overrideBucketActionPermissionCheck(BucketActionPermissionCheck check) {

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
      public IntaveCheckAccess accessCheck(String checkName) throws UnknownCheckException {
        return checkAccessor.checkMirrorOf(checkName);
      }

      @Override
      public List<String> loadedCheckNames() {
        return plugin.checkService().checkNames();
      }

      @Override
      public void restart() {
        Synchronizer.synchronize(() -> {
          plugin.getServer().getPluginManager().disablePlugin(plugin);
          plugin.onLoad();
          plugin.getServer().getPluginManager().enablePlugin(plugin);
          plugin.violationProcessor().broadcastNotify("Intave has been restarted");
        });
      }
    };
  }
}
