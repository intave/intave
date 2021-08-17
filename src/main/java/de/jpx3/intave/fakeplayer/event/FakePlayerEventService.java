package de.jpx3.intave.fakeplayer.event;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;

public final class FakePlayerEventService {
  private final IntavePlugin plugin;
  private EntityVelocityCache entityVelocityCache;

  public FakePlayerEventService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  static {
    String className = "de.jpx3.intave.fakeplayer.ScoreboardAccessor";
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
  }

  public void setup() {
    new PlayerPingPacketDispatcher(plugin);
    this.entityVelocityCache = new EntityVelocityCache(plugin);
  }

  public EntityVelocityCache entityVelocityCache() {
    return entityVelocityCache;
  }
}