package de.jpx3.intave;

import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.accessbackend.IntaveAccessService;
import de.jpx3.intave.adapter.ComponentLoader;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.agent.AgentAccessor;
import de.jpx3.intave.analytics.Analytics;
import de.jpx3.intave.block.access.BlockAccess;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.BlockWrapper;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.modifier.CollisionModifiers;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.shape.resolve.patch.BlockShapePatcher;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.cleanup.StartupTasks;
import de.jpx3.intave.command.CommandForwarder;
import de.jpx3.intave.config.ConfigurationService;
import de.jpx3.intave.connect.IntaveDomains;
import de.jpx3.intave.connect.cloud.Cloud;
import de.jpx3.intave.connect.cloud.LogTransmittor;
import de.jpx3.intave.connect.customclient.CustomClientSupportService;
import de.jpx3.intave.connect.proxy.ProxyMessenger;
import de.jpx3.intave.connect.sibyl.SibylBroadcast;
import de.jpx3.intave.connect.sibyl.SibylIntegrationService;
import de.jpx3.intave.connect.upload.ScheduledUploadService;
import de.jpx3.intave.diagnostic.ConsoleOutput;
import de.jpx3.intave.entity.EntityLookup;
import de.jpx3.intave.entity.size.HitboxSizeAccess;
import de.jpx3.intave.entity.type.EntityTypeDataAccessor;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.klass.locate.Locate;
import de.jpx3.intave.library.Libraries;
import de.jpx3.intave.library.pledge.TickEnd;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.metric.Metrics;
import de.jpx3.intave.metric.ServerHealth;
import de.jpx3.intave.module.BootSegment;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.nayoro.Inventory;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.player.fake.IdentifierReserve;
import de.jpx3.intave.player.fake.event.FakePlayerEventService;
import de.jpx3.intave.reflect.access.ReflectiveAccess;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.resource.legacy.EncryptedLegacyResource;
import de.jpx3.intave.security.PlayerListService;
import de.jpx3.intave.share.FriendlyByteBuf;
import de.jpx3.intave.share.link.WrapperConverter;
import de.jpx3.intave.test.TestService;
import de.jpx3.intave.trustfactor.TrustFactorService;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.LongTermViolationStorage;
import de.jpx3.intave.version.DurationTranslator;
import de.jpx3.intave.version.IntaveVersion;
import de.jpx3.intave.version.IntaveVersionList;
import de.jpx3.intave.version.JavaVersion;
import de.jpx3.intave.world.border.WorldBorders;
import de.jpx3.intave.world.chunk.ChunkProviderServerAccess;
import de.jpx3.intave.world.permission.WorldPermission;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VERSION_DETAILS;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class IntavePlugin extends JavaPlugin {
  private static IntavePlugin singletonInstance;
  private static String version = "UNKNOWN";
  private static String prefix = ChatColor.translateAlternateColorCodes('&', "&8[&c&lIntave&8]&7 ");
  private static String defaultColor = ChatColor.getLastColors(prefix);
  private static final UUID gameId = UUID.randomUUID();
  private static boolean offlineMode = false, successfullyBooted = false;

  static {
    // stage 1 (unused)
  }

  private IntaveLogger logger;
  private LogTransmittor transmittor;
  private Cloud cloud;

  private ProxyMessenger proxyMessenger; // module candidate
  private SibylIntegrationService sibylIntegrationService;
  private FakePlayerEventService fakePlayerEventService; // module candidate
  private ConfigurationService configService;
  private CheckService checkService;
  private TrustFactorService trustFactorService; // module candidate
  private IntaveVersionList versions;
  private CustomClientSupportService customClientSupportService; // module candidate
  private IntaveAccessService accessService;
  private IntaveAccess access;
  private YamlConfiguration configuration;
  private PlayerListService blackListService; // module candidate
  private ScheduledUploadService uploadService; // module candidate
  private Analytics analytics; // module candidate
  private Metrics metrics;
  private TestService testService;

  public IntavePlugin() {
    // stage 2
    stage2();
  }

  public void stage2() {
    singletonInstance = this;
    version = getDescription().getVersion();
    createDataFolder();

    this.logger = new IntaveLogger(this);
    this.logger.checkColorAvailability();
    Modules.prepareModules();
    Modules.proceedBoot(BootSegment.STAGE_2);
    redirectPluginLogger();
    checkClassLoaderAvailability();

    System.setProperty("org.bytedeco.javacpp.cachedir", integrityFolder().getAbsolutePath());

    Libraries.setupLibraries();

    TickEnd.start();

    configService = new ConfigurationService();
    configService.init();

    // preload
    prefix = configService.configuration().getString("layout.prefix", prefix);
    prefix = ChatColor.translateAlternateColorCodes('&', prefix);
  }

  @Override
  public void onLoad() {
    // stage 3
    Modules.proceedBoot(BootSegment.STAGE_3);
  }

  @Override
  public void onEnable() {
    logger.info("Please stand by..");

    // stage 4
    Modules.proceedBoot(BootSegment.STAGE_4);

    if (AgentAccessor.agentAvailable()) {
      logger.info("Using agent :{~-~}:");
    }

    IntaveDomains.setup();

    prefix = ChatColor.translateAlternateColorCodes('&', prefix);

    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (securityManager != null) {
        logger.error("A security manager of class " + securityManager.getClass().getName() + " is present, unable to start");
        bootFailure("Internal failure");
        return;
      }
    } catch (Exception e) {
    }

    try {
      // We need to put this here before setting up the Synchronizer
      ComponentLoader componentLoader = new ComponentLoader(this);
      componentLoader.prepareComponents();
      componentLoader.loadComponents();

      ProtocolLibraryAdapter.checkIfOutdated();

      // check again, after ProtocolLibs availability is guaranteed
      logger.checkColorAvailability();

      // version mambo jumbo
      // stage 5
      Modules.proceedBoot(BootSegment.STAGE_5);

      TaskTracker.setup();
      Locate.setup();

      ReflectiveAccess.setup();

      SinusCache.setup();
      ServerHealth.setup();

      Synchronizer.setup();
      PacketReaders.setup();

      SibylBroadcast.setup();

      IdentifierReserve.setup();
      Inventory.populateCache();
      EntityTypeDataAccessor.setup();

      ChunkProviderServerAccess.setup();

      trustFactorService = new TrustFactorService(this);
      blackListService = new PlayerListService(this);
      cloud = new Cloud();
      cloud.init();

      transmittor = new LogTransmittor();
      transmittor.init();

      // stage 6
      Modules.proceedBoot(BootSegment.STAGE_6);

      // we need to put this here
      BackgroundExecutors.start();

      FriendlyByteBuf.setup();

      // stage 7

      EncryptedLegacyResource contextStatusResource = new EncryptedLegacyResource("context-status", false);

      boolean offlineMode = false;

      VERSION_DETAILS |= 0x100;
      VERSION_DETAILS |= 0x200;
      if (IntaveControl.DEBUG_GRAYLIST) {
        logger.info(blackListService.encryptedGrayKnowledgeData());
      }

      boolean writeSuccessLog = true;
      try {
        if (contextStatusResource.exists()) {
          String textString = contextStatusResource.readAsString();
          if (textString.startsWith("success")) {
            try {
              long lastSuccessfulStart = Long.parseLong(textString.split("/")[1]);
              if (System.currentTimeMillis() - lastSuccessfulStart < TimeUnit.DAYS.toMillis(2)) {
                writeSuccessLog = false;
              }
            } catch (Exception ignored) {
            }
          }
        }
      } catch (Exception ignored) {
      }
      if (writeSuccessLog) {
        contextStatusResource.write(new ByteArrayInputStream(("success/" + System.currentTimeMillis()).getBytes(UTF_8)));
      }

      BlockVariantRegister.index();

//      PacketReaders.setup();
      BlockWrapper.setup();
      WorldBorders.setup();
//      ShapeResolver.setup();

      // stage 7
      Modules.proceedBoot(BootSegment.STAGE_7);

      Entity.setup();
      HitboxSizeAccess.setup();
      UserRepository.setup();
      WrapperConverter.setup();
      Raytracing.setup();
      Fluids.setup();

      VolatileBlockAccess.setup();
      BlockAccess.setup();
      BlockInteractionAccess.setup();
      BlockVariantNativeAccess.setup();
      BlockTypeAccess.setup();
      CollisionModifiers.setup();
      ViaVersionAdapter.setup();
      WorldPermission.setup();
      BlockPhysics.setup();
      BlockProperties.setup();
      ItemProperties.setup();
      BlockShapePatcher.setup();
      EntityLookup.setup();

      versions = new IntaveVersionList();
      try {
        versions.setup();
      } catch (Exception | Error exception) {
        logger.error("Something went wrong checking version");
        exception.printStackTrace();
      }

      IntavePlugin.offlineMode = offlineMode;

      // load config

      YamlConfiguration configuration = configService.configuration();

      prefix = configuration.getString("layout.prefix", prefix);
      prefix = ChatColor.translateAlternateColorCodes('&', prefix);
      defaultColor = ChatColor.getLastColors(prefix);
      FaultKicks.applyFrom(configuration.getConfigurationSection("fault-kicks"));
      ConsoleOutput.applyFrom(configuration.getConfigurationSection("logging"));
      cloud.configInit(configuration.getConfigurationSection("cloud"));

      // stage 8
      Modules.proceedBoot(BootSegment.STAGE_8);
      accessService = new IntaveAccessService(this);
      accessService.setup();
      analytics = new Analytics(this);
      customClientSupportService = new CustomClientSupportService(this);
      customClientSupportService.setup();
      checkService = new CheckService(this);
      fakePlayerEventService = new FakePlayerEventService(this);
      proxyMessenger = new ProxyMessenger(this);
      sibylIntegrationService = new SibylIntegrationService(this);
      testService = new TestService();
      testService.scheduleTestsForFifthTick();
      uploadService = new ScheduledUploadService();
      uploadService.enable();

      getCommand("intave").setExecutor(new CommandForwarder());

      if (IntaveControl.DISABLE_BLOCK_CACHING_ENTIRELY) {
        logger().info("This version does not cache block-accesses");
      }

      if (IntaveControl.DEBUG_VARIANT_COMPILATION) {
        logger().info("This version outputs debug information for block-variant compilation");
      }

      // stage 9
      Modules.proceedBoot(BootSegment.STAGE_9);
      metrics = new Metrics(this, 6019);

      trustFactorService.setup();
      checkService.setup();
      fakePlayerEventService.setup();
      blackListService.setup();
//      analytics.setup();

      try {
        cloud.connectMasterShard();
      } catch (Exception exception) {
        logger.info("Unable to connect to cloud: " + exception.getMessage());
      }
    } catch (Exception exception) {
      logger.error("Unable to boot: " + exception.getMessage());
      exception.printStackTrace();

      invalidateCaches();
      bootFailure("Internal error occurred");
      performShutdown();
      return;
    }

    // stage 10
    Modules.proceedBoot(BootSegment.STAGE_10);

    try {
      ViaVersionAdapter.patchConfiguration();
    } catch (Exception exception) {
      exception.printStackTrace();
    }

    GarbageCollector.setup();
    BackgroundExecutors.executeWhenever(this::clearIntegrityGarbage);
    BackgroundExecutors.executeWhenever(this::clearSaveFolderGarbage);
//    BackgroundExecutors.executeWhenever(this::clearUnusedSamples);
    logger.performCompression();

    LongTermViolationStorage.setup();

    if (JavaVersion.current() < 12) {
//      logger.warn(ChatColor.RED + "Upgrading Java has incredible performance benefits");
//      logger.warn(ChatColor.RED + "We strongly recommend updating Java now");
//      logger.warn(ChatColor.RED + "Support for older versions of Java might eventually be dropped");
      logger.info(ChatColor.RED + "Your version of Java is seriously outdated, consider updating");
    }

    if (IntaveControl.NETTY_DUMP_ON_TIMEOUT) {
      logger.info(ChatColor.YELLOW + "This version will dump netty threads when a player times out");
    }

    if (IntaveControl.USE_DEBUG_LOCATE_RESOURCE) {
      logger.info(ChatColor.YELLOW + "This version will use the Intave/locate file for class mappings");
    }

    if (IntaveControl.LATENCY_PING_AS_XP_LEVEL) {
      logger.info(ChatColor.YELLOW + "This version sets the latency ping as the player's xp level");
    }

    if (IntaveControl.APPLY_GLOBAL_LOW_TRUSTFACTOR) {
      logger.info(ChatColor.YELLOW + "This version assigns only the red trustfactor for debugging");
    }

    Plugin viaBackwards = Bukkit.getPluginManager().getPlugin("ViaBackwards");
    if (viaBackwards != null) {
      if (!viaBackwards.getConfig().getBoolean("handle-pings-as-inv-acknowledgements", false)) {
        logger.warn("ViaBackwards is misconfigured, causing false-positives and fault kicks");
        logger.warn("Go to plugins/ViaBackwards/config.yml and set \"handle-pings-as-inv-acknowledgements\" to TRUE");
      }
    }

    Modules.linker().packetEvents().refreshLinkages();
    displayVersionInformation();
    successfullyBooted = true;
    randomExitMessages = Resources.localServiceCacheResource("exitmessages", "exitmessages", TimeUnit.DAYS.toMillis(7)).readLines();
    logger.info("Intave booted successfully");

    Synchronizer.synchronize(() -> {
      // stage 11
      Modules.proceedBoot(BootSegment.STAGE_11);

      StartupTasks.runAll();
    });
  }

  public void createDataFolder() {
    File dataFolder = dataFolder();
    if (!(dataFolder.exists() || dataFolder.mkdirs())) {
      logger.error("Failed to create Intave folder " + dataFolder.getAbsolutePath());
    }
  }

  public File dataFolder() {
    return new File(getServer().getUpdateFolderFile().getParentFile(), "Intave");
  }

  public void redirectPluginLogger() {
    try {
      Field loggerField = JavaPlugin.class.getDeclaredField("logger");
      loggerField.setAccessible(true);
      loggerField.set(this, logger());
    } catch (Exception exception) {
      logger.error("[Intave] Failed to inject logger to bukkit");
    }
  }

  public void checkClassLoaderAvailability() {
    if (de.jpx3.classloader.ClassLoader.usesNativeAccess() && !de.jpx3.classloader.ClassLoader.loaded()) {
      try {
        de.jpx3.classloader.ClassLoader.setupEnvironment(Files.createTempDirectory("intave-debug").toFile());
      } catch (IOException exception) {
        logger.error("[Intave] Failed to create temporary directory for classloader");
        exception.printStackTrace();
      }
    }
  }

  public void displayVersionInformation() {
    IntaveVersion version = versions.versionInformation(version());
    if (version == null) {
      logger().info(ChatColor.YELLOW + "This version of Intave is not listed in the official version index");
    } else {
      long duration = System.currentTimeMillis() - version.release();
      String durationAsString = DurationTranslator.translateHours(duration);

      String infoMessage = "";
      switch (version.typeClassifier()) {
        case LATEST:
          infoMessage = "Running the latest version of Intave (" + durationAsString + " old)";
          break;
        case STABLE:
          infoMessage = "Running a stable version of Intave (" + durationAsString + " old)";
          break;
        case OUTDATED:
          infoMessage = "A newer version of Intave is available (this version is " + durationAsString + " old)";
          break;
        case TEST:
          infoMessage = "Running a test version of Intave";
          break;
        case DISABLED:
        case INVALID:
          logger().error("Unable to boot: This version has been deactivated");
          bootFailure("Version deactivated");
          performShutdown();
          throw new IntaveInternalException("Escape exception");
      }
      logger().info(infoMessage);
    }
  }

  public static final long INTEGRITY_ERASE_BUFFER = TimeUnit.MINUTES.toMillis(1);

  public void clearIntegrityGarbage() {
    File tempDir = new File(System.getProperty("java.io.tmpdir"));
    try {
      Files.walk(tempDir.toPath())
        .map(Path::toFile)
        .filter(File::canRead)
        .filter(File::canWrite)
        .filter(file -> "deleteme".equalsIgnoreCase(file.getName()) && file.getParentFile().getName().toLowerCase(Locale.ROOT).contains("intave"))
        .filter(file -> (System.currentTimeMillis() - file.lastModified()) > INTEGRITY_ERASE_BUFFER)
        .map(File::getParentFile)
        .filter(File::canRead)
        .filter(File::canWrite)
        .forEach(file -> {
          try {
            clearDirectory(file);
          } catch (IOException ignored) {
          }
        });
    } catch (Exception ignored) {
    }
  }

  private void clearDirectory(File directory) throws IOException {
    if (!directory.exists() || !directory.isDirectory()) {
      return;
    }
    File[] files = directory.listFiles();
    if (files == null) {
      throw new IOException("Failed to list contents of " + directory);
    } else {
      for (File file : files) {
        try {
          forceDelete(file);
        } catch (IOException ignored) {
        }
      }
    }
    directory.delete();
  }

  private void forceDelete(File file) throws IOException {
    if (file.isDirectory()) {
      clearDirectory(file);
    } else {
      boolean exists = file.exists();
      if (!file.delete()) {
        if (!exists) {
          throw new FileNotFoundException("File does not exist: " + file);
        }
        throw new IOException("Unable to delete file: " + file);
      }
    }
  }

  private static final long FILE_EXPIRE = TimeUnit.DAYS.toMillis(90);

  public void clearSaveFolderGarbage() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/";
    } else {
      filePath = System.getProperty("user.home") + "/.intave/";
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      return;
    }
    try {
      // clear unused files
      Files.walk(workDirectory.toPath())
        .filter(Files::isRegularFile)
        .map(Path::toFile)
        .filter(File::canWrite)
        .filter(File::canRead)
        .filter(file -> (System.currentTimeMillis() - file.lastModified()) > FILE_EXPIRE)
        .forEach(file -> {
          try {
            file.delete();
          } catch (Exception exception) {
//            exception.printStackTrace();
          }
        });
      // clear empty directories
      Files.walk(workDirectory.toPath())
        .filter(Files::isDirectory)
        .map(Path::toFile)
        .filter(File::canWrite)
        .filter(File::canRead)
        .filter(file -> file.listFiles() == null)
        .filter(file -> (System.currentTimeMillis() - file.lastModified()) > FILE_EXPIRE)
        .forEach(file -> {
          try {
            file.delete();
          } catch (Exception exception) {
//            exception.printStackTrace();
          }
        });
    } catch (NoSuchFileException ignored) {
      // ignore
    } catch (Exception | Error throwable) {
//      throwable.printStackTrace();
    }
  }

  public void invalidateCaches() {
    clearIntegrityGarbage();
  }

  public void bootFailure(String reason) {
    getCommand("intave").setExecutor((commandSender, command, s, strings) -> {
      commandSender.sendMessage(prefix() + ChatColor.RED + "Intave couldn't boot properly: " + reason);
      return false;
    });
  }

  @Override
  public void onDisable() {
    performShutdown();
  }

  public void performShutdown() {
    logger.info("Stopping Intave");
    try {
      configService.shutdown();
    } catch (Exception ignored) {
    }
    Bukkit.getScheduler().cancelTasks(this);
    ShutdownTasks.runAll();
    BackgroundExecutors.stopAllBlocking();
    deleteIntegrityCache();
    if (successfullyBooted) {
      logger.info(randomExitMessage());
    }
    logger.info("Intave offline");
    logger.shutdown();
  }

  private List<String> randomExitMessages = new ArrayList<>();

  private String randomExitMessage() {
    return randomExitMessages.isEmpty() ? "No jokes? :(" : randomExitMessages.get(ThreadLocalRandom.current().nextInt(randomExitMessages.size()));
  }

  private void deleteIntegrityCache() {
    try {
      // mark caches as deletable
      Class<?> relocator = Class.forName("de.jpx3.relocator.Relocator");
      relocator.getMethod("i").invoke(null);
    } catch (Exception ignored) {
    }
  }

  private File integrityFolder() {
    try {
      // mark caches as deletable
      Class<?> relocator = Class.forName("de.jpx3.relocator.Relocator");
      File child = (File) relocator.getMethod("h").invoke(null, "a", "b");
      return child.getParentFile();
    } catch (Exception ignored) {
    }
    try {
      return Files.createTempDirectory("intave").toFile();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public IntaveAccess access() {
    return access;
  }

  public void setAccess(IntaveAccess access) {
    this.access = access;
  }

  public IntaveAccessService accessService() {
    return accessService;
  }

  public TrustFactorService trustFactorService() {
    return trustFactorService;
  }

  public IntaveLogger logger() {
    return logger;
  }

  public Cloud cloud() {
    return cloud;
  }

  public LogTransmittor logTransmittor() {
    return transmittor;
  }

  public ProxyMessenger proxy() {
    return proxyMessenger;
  }

  public CheckService checks() {
    return checkService;
  }

  public YamlConfiguration settings() {
    return configService.configuration();
  }

  public FakePlayerEventService fakePlayerEventService() {
    return fakePlayerEventService;
  }

  @Deprecated
  public BukkitEventSubscriptionLinker eventLinker() {
    return Modules.linker().bukkitEvents();
  }

  public SibylIntegrationService sibyl() {
    return sibylIntegrationService;
  }

  public Analytics analytics() {
    return analytics;
  }

  public ScheduledUploadService uploader() {
    return uploadService;
  }

  public IntaveVersionList versions() {
    return versions;
  }

  public static String version() {
    return version;
  }

  public static UUID gameId() {
    return gameId;
  }

  public static String prefix() {
    return prefix;
  }

  public static String defaultColor() {
    return defaultColor;
  }

  public static IntavePlugin singletonInstance() {
    return singletonInstance;
  }
}