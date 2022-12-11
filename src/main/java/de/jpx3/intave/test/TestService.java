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
import de.jpx3.intave.module.player.StorageTests;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.security.HWIDVerification;
import de.jpx3.intave.security.HashAccess;
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
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static de.jpx3.intave.IntaveControl.USE_DEBUG_LOCATE_RESOURCE;

@HighOrderService
public final class TestService implements EventProcessor {
  private static final Resource environmentHashResource = Resources.fileCache("environmentHashes");
  private static final Map<String, Long> supportedEnvironments = environmentHashResource.readLines().stream()
    .filter(s -> s.contains(":"))
    .limit(8192)
    .map(line -> line.split(":"))
    .collect(Collectors.toMap(split -> split[0], split -> Long.parseLong(split[1])));
  private static final String environmentHash = environmentHash();

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

  public void scheduleTestsForFifthTick() {
    if (!environmentKnown()) {
      Modules.linker().bukkitEvents().registerEventsIn(this);
      Synchronizer.synchronizeDelayed(this::performTests, 5);
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

    if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
      IntaveLogger.logger().info("Start self-testing..");
    }
    long start = System.currentTimeMillis();
    try {
      // we can assume all classes loaded

      // parts
      performTest(BlockAccessTests.class);
      performTest(BlockVariantTests.class);
      performTest(BlockShapeTests.class);
      performTest(BlockShapeDrillTests.class);
      performTest(BlockShapePipelineTests.class);
      performTest(StorageTests.class);

      // checks
//      performTest(SimulatorBasicTests.class);

      // locate
      performTest(ReferenceExistenceTests.class);

    } catch (Throwable werfbares) {
      Throwable throwable = werfbares;
      while (throwable.getCause() != null) {
        throwable = throwable.getCause();
      }
      String exceptionName = throwable.getClass().getSimpleName();
      IntaveLogger.logger().error("Reported " + resolveArticle(exceptionName) + " " + exceptionName + ": " + throwable.getMessage());
      IntaveLogger.logger().error("You are hereby advised to report this fault to us before using this version of Intave.");
      IntaveLogger.logger().error("If possible, include the following stacktrace in your report:");
      throwable.printStackTrace();
      return;
    }
    dontCheckThisEnvironmentAgain();
    if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
      IntaveLogger.logger().info("No problems found after " + MathHelper.formatDouble((System.currentTimeMillis() - start) / 1000d, 1) + "s.");
    } else {
      IntaveLogger.logger().info("All self-tests completed successfully.");
    }
  }

  private static final char[] vocals = "AEIOU".toCharArray();

  private String resolveArticle(String exceptionName) {
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
    return supportedEnvironments.containsKey(environmentHash) && !USE_DEBUG_LOCATE_RESOURCE;
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
