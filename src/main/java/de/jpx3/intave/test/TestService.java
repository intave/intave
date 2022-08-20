package de.jpx3.intave.test;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.block.shape.BlockShapeTests;
import de.jpx3.intave.block.shape.resolve.BlockShapeDrillTests;
import de.jpx3.intave.block.shape.resolve.BlockShapePipelineTests;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.locate.ReferenceExistenceTests;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@HighOrderService
public final class TestService {
  public void scheduleTestsForFirstTick() {
    if (!environmentKnown()) {
      IntaveLogger.logger().info("Self-tests are performed after startup");
      Synchronizer.synchronize(this::performTests);
    }
  }

  @Native
  public void performTests() {
    IntaveLogger.logger().info("Intave will take a few seconds to self-test,");
    IntaveLogger.logger().info("since it is yet unfamiliar with your environment.");
    try {
      // we can assume all classes loaded

      // parts
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
    IntaveLogger.logger().info("Self-tests finished");
  }

  private static final Resource environmentHashResource = Resources.fileCache("environmentHashes");
  private static final List<String> supportedEnvironments = environmentHashResource.lines();
  private static final String environmentHash = environmentHash();

  public boolean environmentKnown() {
    return true;
//    String environmentHash = environmentHash();
//    return supportedEnvironments.contains(environmentHash);
  }

  public void dontCheckThisEnvironmentAgain() {
    supportedEnvironments.add(environmentHash);
    environmentHashResource.write(supportedEnvironments);
  }


  private static String environmentHash() {
    StringBuilder bigString = new StringBuilder(Bukkit.getServer().getName());
    for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
      bigString.append(plugin.getName());
      PluginDescriptionFile description = plugin.getDescription();
      bigString.append(description.getMain());
      bigString.append(description.getVersion());
      bigString.append(plugin.getConfig().saveToString());
    }
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

  public void performTest(Class<? extends Tests> testsClass) {
    try {
      new Tester(testsClass).run();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }
}
