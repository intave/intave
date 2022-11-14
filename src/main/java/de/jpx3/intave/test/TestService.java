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
import de.jpx3.intave.block.variant.BlockVariantTests;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.locate.ReferenceExistenceTests;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.security.HashAccess;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@HighOrderService
public final class TestService implements EventProcessor {
  private static final Resource environmentHashResource = Resources.fileCache("environmentHashes");
  private static final Map<String, Long> supportedEnvironments = environmentHashResource.lines().stream()
    .filter(s -> s.contains(":"))
    .map(line -> line.split(":"))
    .collect(Collectors.toMap(split -> split[0], split -> Long.parseLong(split[1])));
  private static final String environmentHash = environmentHash();

  private static String environmentHash() {
    StringBuilder bigString = new StringBuilder(Bukkit.getServer().getName());
//    for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
//      bigString.append(plugin.getName());
//      PluginDescriptionFile description = plugin.getDescription();
//      bigString.append(description.getMain());
//      bigString.append(description.getVersion());
//
//      YamlConfiguration config = loadConfiguration(plugin);
//      if (config != null) {
//        bigString.append(config.saveToString());
//      }
//    }
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
      IntaveLogger.logger().info("Since it is unfamiliar with your environment, Intave will self-test.");
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

    IntaveLogger.logger().info("Intave will take a few moments to self-test");
    IntaveLogger.logger().info("If all tests succeed, you may ignore any error warnings that appear during this time");
    long start = System.currentTimeMillis();
    try {
      // we can assume all classes loaded

      // parts
      performTest(BlockAccessTests.class);
      performTest(BlockVariantTests.class);
      performTest(BlockShapeTests.class);
      performTest(BlockShapeDrillTests.class);
      performTest(BlockShapePipelineTests.class);

      // checks
//      performTest(SimulatorBasicTests.class);

      // locate
      performTest(ReferenceExistenceTests.class);

    } catch (Exception exception) {
      if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
        exception.printStackTrace();
      }
      IntaveLogger.logger().error("Failure reported from a self-test, aborting with notice.");
      IntaveLogger.logger().error("You are advised to report and resolve this fault before using this version of Intave.");
      return;
    }
    dontCheckThisEnvironmentAgain();
    IntaveLogger.logger().info("Testing completed after " + MathHelper.formatDouble((System.currentTimeMillis() - start) / 1000d, 1) + "s, no problems found.");
  }

  public boolean environmentKnown() {
    String environmentHash = environmentHash();
    return supportedEnvironments.containsKey(environmentHash);
  }

  private static final long MILLIS_IN_A_MONTH = 1000L * 60L * 60L * 24L * 30L;

  public void dontCheckThisEnvironmentAgain() {
    supportedEnvironments.put(environmentHash, System.currentTimeMillis());
    // delete system older than 1 month
    supportedEnvironments.entrySet().removeIf(entry -> entry.getValue() < System.currentTimeMillis() - MILLIS_IN_A_MONTH);
    environmentHashResource.write(supportedEnvironments.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).collect(Collectors.joining(System.lineSeparator())));
  }

  private static int testsInInstance = 0;

  public void performTest(Class<? extends Tests> testsClass) {
    try {
      testsInInstance++;
      new Tester(testsClass).run();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private static final Set<String> cleared = new HashSet<>();

  public static void testClearedByGC(String name) {
    if (cleared.contains(name)) {
      return;
    }
    cleared.add(name);
    testsInInstance--;
    if (testsInInstance == 0 && IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
      IntaveLogger.logger().info("[debug] All tests cleared by GC, no memory leaks detected");
      cleared.clear();
    }
  }
}
