package de.jpx3.intave.test;

import com.google.common.base.Charsets;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.block.access.BlockAccessTests;
import de.jpx3.intave.block.fluid.FluidTests;
import de.jpx3.intave.block.shape.BlockShapeTests;
import de.jpx3.intave.block.shape.resolve.BlockShapeDrillTests;
import de.jpx3.intave.block.shape.resolve.BlockShapePipelineTests;
import de.jpx3.intave.block.variant.BlockVariantTests;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.check.movement.physics.MovementConfigurationTests;
import de.jpx3.intave.check.movement.physics.SimulatorBasicTests;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.entity.size.EntitySizeTests;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.locate.ReferenceExistenceTests;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackTests;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.player.StorageTests;
import de.jpx3.intave.packet.reader.ReaderTests;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.security.HWIDVerification;
import de.jpx3.intave.security.HashAccess;
import de.jpx3.intave.share.ShareTests;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static de.jpx3.intave.IntaveControl.USE_DEBUG_LOCATE_RESOURCE;

@HighOrderService
public final class IntegrationTestService implements EventProcessor {
  private static final boolean IS_INTEGRATION_TEST_RUN = "shutdown".equalsIgnoreCase(System.getProperty("intave.test.success"));
  private static final Resource environmentHashResource = Resources.fileCache("environmentHashes");
  private static final Map<String, Long> supportedEnvironments =
    environmentHashResource.collectLines(
      Collectors.mapping(
        line -> {
          String[] split = line.split(":");
          return new AbstractMap.SimpleEntry<>(split[0], Long.parseLong(split[1]));
        },
        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::max, HashMap::new)
      ),
      8192
    );
  private static final String environmentHash = environmentHash();
  private boolean testsWereRun = false;

  private static String environmentHash() {
    StringBuilder bigString = new StringBuilder(Bukkit.getServer().getName());
    try {
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
    } catch (Throwable werfbares) {
      bigString.append("no-plugins");
    }
    String jarHash;
    try {
      File currentJavaJarFile = new File(IntavePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      jarHash = HashAccess.hashOf(currentJavaJarFile);
    } catch (URISyntaxException e) {
      jarHash = "no-hash";
    }
    bigString.append(jarHash);
    bigString.append(System.getProperty("java.version"));
    bigString.append(System.getProperty("java.vendor"));
    bigString.append(System.getProperty("java.home"));
    bigString.append(System.getProperty("os.name"));
    bigString.append(System.getProperty("os.version"));
    bigString.append(Bukkit.getVersion());
    bigString.append(Bukkit.getBukkitVersion());
    bigString.append(HWIDVerification.publicHardwareIdentifier());
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

  public void setup() {
    if (IS_INTEGRATION_TEST_RUN) {
      ShutdownTasks.add(() -> {
        if (!testsWereRun) {
          System.err.println("Tests were not run, but this is a test run.");
          System.exit(1);
        }
      });
    }

    scheduleTestsForFifthTick();
  }

  public void scheduleTestsForFifthTick() {
    if (!environmentKnown() || IS_INTEGRATION_TEST_RUN) {
      Modules.linker().bukkitEvents().registerEventsIn(this);
      Location anchor = testAnchor();
      if (anchor != null) {
        // The world-touching self-tests read/write the block at (0,1,0) of the
        // primary world. On Folia that must happen on the region owning that
        // block, so anchor the whole test run to that location.
        Synchronizer.synchronizeDelayed(anchor, this::performTests, 5);
      } else {
        // No world loaded yet; performTests() re-queues itself on WorldLoadEvent.
        Synchronizer.synchronizeDelayed(this::performTests, 5);
      }
    }
  }

  private static Location testAnchor() {
    if (Bukkit.getWorlds().isEmpty()) {
      return null;
    }
    return new Location(Bukkit.getWorlds().get(0), 0, 1, 0);
  }

  private final Queue<Runnable> loadQueue = new ConcurrentLinkedQueue<>();

  @BukkitEventSubscription
  // on world load event
  public void on(WorldLoadEvent event) {
    Runnable runnable;
    Location anchor = new Location(event.getWorld(), 0, 1, 0);
    while ((runnable = loadQueue.poll()) != null) {
      Synchronizer.synchronize(anchor, runnable);
    }
  }

  private static final Class<?>[] TEST_CLASSES = {
    // parts
    BlockAccessTests.class,
    BlockVariantTests.class,
    BlockShapeDrillTests.class,
    BlockShapePipelineTests.class,
    BlockShapeTests.class,
    EntitySizeTests.class,
    StorageTests.class,
    FeedbackTests.class,
    ReaderTests.class,
    FluidTests.class,
    ShareTests.class,
    MovementConfigurationTests.class,
    // checks
    SimulatorBasicTests.class,
    // locate
    ReferenceExistenceTests.class,
  };

  public void performTests() {
    if (Bukkit.getWorlds().isEmpty()) {
      IntaveLogger.logger().info("No worlds loaded, delaying integration tests");
      loadQueue.add(this::performTests);
      return;
    }

    if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
      IntaveLogger.logger().info("Start integration testing..");
    }
    // The self-tests touch real world state at spawn, so they must run on that
    // region's thread. But the suite is CPU-heavy; running it all in one tick
    // would stall the region past Folia's watchdog threshold. Run each test
    // class on its own region tick instead: this yields between classes (short
    // ticks, watchdog stays happy) while preserving sequential, single-threaded
    // execution. On non-Folia servers this simply runs on the main thread.
    runTestChain(0, System.currentTimeMillis());
  }

  @SuppressWarnings("unchecked")
  private void runTestChain(int index, long startMillis) {
    if (index >= TEST_CLASSES.length) {
      finishTests(startMillis);
      return;
    }
    Class<? extends IntegrationTests> testClass = (Class<? extends IntegrationTests>) TEST_CLASSES[index];
    Runnable runOne = () -> {
      try {
        performTest(testClass);
      } catch (Throwable werfbares) {
        reportTestFailure(werfbares);
        return; // abort the remaining chain on a hard failure
      }
      runTestChain(index + 1, startMillis);
    };
    Location anchor = testAnchor();
    if (anchor != null) {
      Synchronizer.synchronize(anchor, runOne);
    } else {
      Synchronizer.synchronize(runOne);
    }
  }

  private void reportTestFailure(Throwable werfbares) {
    testsWereRun = true;
    Throwable throwable = werfbares;
    while (throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    String exceptionName = throwable.getClass().getSimpleName();
    IntaveLogger.logger().error("Reported " + resolveArticleOf(exceptionName) + " " + exceptionName + ": " + throwable.getMessage());
    IntaveLogger.logger().error("You are hereby advised to report this fault to us before using this version of Intave.");
    IntaveLogger.logger().error("If possible, include the following stacktrace in your report:");
    throwable.printStackTrace();
    if (IS_INTEGRATION_TEST_RUN) {
      IntaveLogger.logger().error("Shutting down server due to test failure");
      BackgroundExecutors.execute(() -> System.exit(1));
    }
  }

  private void finishTests(long startMillis) {
    testsWereRun = true;
    dontCheckThisEnvironmentAgain();
    if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
      IntaveLogger.logger().info("No problems found after " + MathHelper.formatDouble((System.currentTimeMillis() - startMillis) / 1000d, 1) + "s.");
    } else {
      IntaveLogger.logger().info("All integration tests completed successfully.");
    }
    if (IS_INTEGRATION_TEST_RUN) {
      IntaveLogger.logger().info("Shutting down server due to test success");
      Synchronizer.synchronizeDelayed(Bukkit::shutdown, 10);
    }
  }

  private static final char[] vocals = "AEIOU".toCharArray();

  private String resolveArticleOf(String exceptionName) {
    if (exceptionName.isEmpty()) {
      return "";
    }
    char c = exceptionName.toUpperCase(Locale.ROOT).toCharArray()[0];
    boolean isVocal = false;
    for (char vocal : vocals) {
      if (vocal == c) {
        isVocal = true;
        break;
      }
    }
    return isVocal ? "an" : "a";
  }

  public boolean environmentKnown() {
//    System.out.println("Environment hash: " + environmentHash);
//    System.out.println("Supported environments: " + supportedEnvironments);
    return supportedEnvironments.containsKey(environmentHash) && !IS_INTEGRATION_TEST_RUN && !USE_DEBUG_LOCATE_RESOURCE;
  }

  private static final long MILLIS_IN_A_MONTH = 1000L * 60L * 60L * 24L * 30L;

  public void dontCheckThisEnvironmentAgain() {
    if (IS_INTEGRATION_TEST_RUN) {
      return;
    }
    long currentTimeMillis = System.currentTimeMillis();
    supportedEnvironments.put(environmentHash, currentTimeMillis);
    // delete system older than 1 month
    supportedEnvironments.entrySet().removeIf(entry -> currentTimeMillis - entry.getValue() > MILLIS_IN_A_MONTH);
    environmentHashResource.write(supportedEnvironments.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()));
  }

  private static int testsInInstance = 0;

  public void performTest(Class<? extends IntegrationTests> testsClass) {
    try {
      testsInInstance++;
      new IntegrationTester(testsClass).run();
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
