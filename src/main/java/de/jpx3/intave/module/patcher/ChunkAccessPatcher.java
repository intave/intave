package de.jpx3.intave.module.patcher;

import com.google.common.collect.Sets;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.world.chunk.ChunkProviderServerAccess;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.world.WorldInitEvent;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;

@DoNotFlowObfuscate
public final class ChunkAccessPatcher extends Module {
  private static final boolean ENABLED = !MinecraftVersions.VER1_14_0.atOrAbove();

  static {
    if (ENABLED) {
      ClassLoader classLoader = IntavePlugin.class.getClassLoader();
      PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.module.patcher.SynchronizedBukkitLongHashSet");
    }
  }

  @Override
  public void enable() {
    Bukkit.getWorlds().forEach(this::patchWorld);
  }

  @BukkitEventSubscription
  public void worldInit(WorldInitEvent event) {
    patchWorld(event.getWorld());
  }

  private static boolean failedDSIFirstPatch = false;

  public void patchWorld(World world) {
    if (!ENABLED) {
      return;
    }
    String patchName = "Unknown";
    try {
      Field unloadQueueField = unloadQueueField();
      if (unloadQueueField == null) {
        return;
      }
      if (!unloadQueueField.isAccessible()) {
        unloadQueueField.setAccessible(true);
      }
      String unloadQueueFieldClassName = unloadQueueField.getType().getName();
      Object chunkProviderServer = ChunkProviderServerAccess.chunkProviderServerOf(world);
      Object unloadQueue = unloadQueueField.get(chunkProviderServer);
      Iterator<Long> iterator;
      try {
        //noinspection unchecked
        iterator = (Iterator<Long>) unloadQueue.getClass().getMethod("iterator").invoke(unloadQueue);
      } catch (Exception exception) {
        iterator = Collections.emptyIterator();
      }
//      IntaveLogger.logger().info("Name is " + unloadQueueFieldClassName);
      if (unloadQueueFieldClassName.contains("dsi.fastutil.longs")) {
//        IntaveLogger.logger().info("Patching unload queue of world " + world.getName() + " with " + unloadQueueFieldClassName);
        if (unloadQueueFieldClassName.endsWith("LongArraySet")) { // TacoSpigot - SynchronizedLongHashSet
          patchName = "s(dsi/ls)";
          SynchronizedLongArraySet newQueue = new SynchronizedLongArraySet();
          unloadQueueField.set(chunkProviderServer, newQueue);
          iterator.forEachRemaining(newQueue::add);
        } else { // NachoSpigot - SynchronizedDSILongHashSet
          patchName = "s(dsi/lhs)";
          try {
            if (failedDSIFirstPatch) {
              throw new Exception("Failed before");
            }
            SynchronizedDSILongHashSet newQueue = new SynchronizedDSILongHashSet();
            unloadQueueField.set(chunkProviderServer, newQueue);
            iterator.forEachRemaining(newQueue::add);
          } catch (Throwable ignored) {
            if (!failedDSIFirstPatch) {
              IntaveLogger.logger().info("Using alternative patch for unload queue for " + unloadQueueFieldClassName);
              failedDSIFirstPatch = true;
            }
            LongSet queue = (LongSet) unloadQueue;
            DSILongSetWrapper wrapper = new DSILongSetWrapper(queue);
            unloadQueueField.set(chunkProviderServer, wrapper);
          }
        }
      } else if (unloadQueueFieldClassName.endsWith("util.LongHashSet")) { // newer minecraft versions
//        IntaveLogger.logger().info("Patching unload queue of world " + world.getName() + " with " + unloadQueueFieldClassName);
        patchName = "s(ut/lhs)";
        SynchronizedBukkitLongHashSet newQueue = new SynchronizedBukkitLongHashSet();
        unloadQueueField.set(chunkProviderServer, newQueue);
        iterator.forEachRemaining(newQueue::add);
      } else { // older minecraft versions
//        IntaveLogger.logger().info("Patching unload queue of world " + world.getName() + " with " + unloadQueueFieldClassName);
        patchName = "s(java/hs)";
        SynchronizedSet<Long> newQueue = new SynchronizedSet<>(Sets.newHashSet());
        unloadQueueField.set(chunkProviderServer, newQueue);
        iterator.forEachRemaining(newQueue::add);
      }
    } catch (Exception | Error exception) {
      IntaveLogger.logger().info(String.format("Failed to patch chunk unload queue of \"%s\" with \"%s\": %s", world.getName(), patchName, exception.getMessage()));
      exception.printStackTrace();
    }
  }

  private Field unloadQueueField() {
    try {
      return Lookup.serverClass("ChunkProviderServer").getField("unloadQueue");
    } catch (NoSuchFieldException ignoredEZ) {
      return null;
    }
  }
}
