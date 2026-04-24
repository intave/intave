package de.jpx3.intave.player;

import com.google.common.collect.Maps;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.executor.BackgroundExecutors;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ProfileLookup {
  private static final Map<String, UUID> requestCache =
    GarbageCollector.watch(Maps.newConcurrentMap());

  public static void lookupIdFromName(String name, Consumer<UUID> lazyReturn) {
    if (requestCache.containsKey(name)) {
      lazyReturn.accept(requestCache.get(name));
      return;
    }
    BackgroundExecutors.execute(() ->
      lazyReturn.accept(requestCache.computeIfAbsent(name, ProfileLookup::loadIfFromName))
    );
  }

  private static UUID loadIfFromName(String name) {
    return null;
  }
}
