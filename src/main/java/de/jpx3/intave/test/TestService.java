package de.jpx3.intave.test;

import com.google.common.base.Charsets;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.block.access.BlockAccessTests;
import de.jpx3.intave.block.shape.BlockShapeTests;
import de.jpx3.intave.block.shape.resolve.BlockShapeDrillTests;
import de.jpx3.intave.block.shape.resolve.BlockShapePipelineTests;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.locate.ReferenceExistenceTests;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.security.HashAccess;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@HighOrderService
public final class TestService implements EventProcessor {
  private static final Resource environmentHashResource = Resources.fileCache("environmentHashes");
  private static final List<String> supportedEnvironments = environmentHashResource.lines();
  private static final String environmentHash = environmentHash();

  private static String environmentHash() {
    StringBuilder bigString = new StringBuilder(Bukkit.getServer().getName());
    for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
      bigString.append(plugin.getName());
      PluginDescriptionFile description = plugin.getDescription();
      bigString.append(description.getMain());
      bigString.append(description.getVersion());

      YamlConfiguration config = loadConfiguration(plugin);
      if (config != null) {
        bigString.append(config.saveToString());
      }
    }
    String jarHash;
    try {
      File currentJavaJarFile = new File(IntavePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      jarHash = HashAccess.hashOf(currentJavaJarFile);
    } catch (URISyntaxException e) {
      jarHash = "unable-to-hash";
    }
    bigString.append(jarHash);
    bigString.append(System.getProperty("java.version"));
    bigString.append(System.getProperty("java.vendor"));
    bigString.append(System.getProperty("java.home"));
    bigString.append(System.getProperty("os.name"));
    bigString.append(System.getProperty("os.version"));
    bigString.append(Bukkit.getVersion());
    bigString.append(Bukkit.getBukkitVersion());
    // hash with SHA-256
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new RuntimeException(exception);
    }
    byte[] hash = digest.digest(bigString.toString().getBytes());
    StringBuilder hashString = new StringBuilder();
    for (byte bite : hash) {
      hashString.append(String.format("%02x", bite));
    }
    return hashString.toString();
  }

  @Nullable
  private static YamlConfiguration loadConfiguration(Plugin plugin) {
    YamlConfiguration config = new YamlConfiguration();
    InputStream resource = plugin.getResource("config.yml");

    if (resource == null) {
      return null;
    }

    try {
      InputStreamReader reader = new InputStreamReader(resource, Charsets.UTF_8);
      config.load(reader);
      return config;
    } catch (Throwable whoAsked) {
      return null;
    }
  }

  public void scheduleTestsForFirstTick() {
    if (!environmentKnown()) {
      Modules.linker().bukkitEvents().registerEventsIn(this);
      IntaveLogger.logger().info("Self-tests are performed after startup");
      Synchronizer.synchronize(this::performTests);
    }
  }

  private final Queue<Runnable> loadQueue = new ConcurrentLinkedQueue<>();

  @BukkitEventSubscription
  // on world load event
  public void on(WorldLoadEvent event) {
    Runnable runnable;
    while ((runnable = loadQueue.poll()) != null) {
      Synchronizer.synchronize(runnable);
    }
  }

  @Native
  public void performTests() {
    if (Bukkit.getWorlds().isEmpty()) {
      IntaveLogger.logger().info("No worlds loaded, delaying self-tests");
      loadQueue.add(this::performTests);
      return;
    }

    IntaveLogger.logger().info("Intave will take a few seconds to self-test");
    IntaveLogger.logger().info("Ignore any error warnings from bukkit or other plugins during this time");
    long start = System.currentTimeMillis();
    try {
      // we can assume all classes loaded

      // parts
      performTest(BlockShapeTests.class);
      performTest(BlockShapeDrillTests.class);
      performTest(BlockShapePipelineTests.class);

      performTest(BlockAccessTests.class);

      // checks
//      performTest(SimulatorBasicTests.class);

      // locate
//      performTest(ReferenceExistenceTests.class);

    } catch (Exception exception) {
      if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
        exception.printStackTrace();
      }
      IntaveLogger.logger().error("Failure reported from a CAT1 self-test, aborting with ERROR notice.");
      IntaveLogger.logger().error("You must report and resolve this fault before using this version of Intave.");
      if (!IntaveControl.GOMME_MODE) {
        IntaveLogger.logger().warn("Waiting a few seconds so you NOTICE the error and REPORT it.");
        try {
          Thread.sleep(16000);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      return;
    }
    dontCheckThisEnvironmentAgain();
    IntaveLogger.logger().info("Testing completed after " + (System.currentTimeMillis() - start) + "ms");
  }

  public boolean environmentKnown() {
//    return true;
    String environmentHash = environmentHash();
    return supportedEnvironments.contains(environmentHash);
  }

  public void dontCheckThisEnvironmentAgain() {
    supportedEnvironments.add(environmentHash);
    environmentHashResource.write(supportedEnvironments);
  }

  public void performTest(Class<? extends Tests> testsClass) {
    try {
      new Tester(testsClass).run();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }
}
