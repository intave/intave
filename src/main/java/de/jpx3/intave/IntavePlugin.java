package de.jpx3.intave;

import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.accessbackend.IntaveAccessService;
import de.jpx3.intave.adapter.ComponentLoader;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.agent.AgentAccessor;
import de.jpx3.intave.annotate.NameIntrinsicallyImportant;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.cleanup.Shutdown;
import de.jpx3.intave.command.CommandProcessor;
import de.jpx3.intave.config.ConfigurationService;
import de.jpx3.intave.connect.customclient.CustomClientSupportService;
import de.jpx3.intave.connect.proxy.ProxyMessenger;
import de.jpx3.intave.connect.shadow.LabymodShadowIntegration;
import de.jpx3.intave.connect.sibyl.SibylIntegrationService;
import de.jpx3.intave.detect.CheckService;
import de.jpx3.intave.diagnostic.report.RuntimeDiagnostics;
import de.jpx3.intave.event.CustomEventService;
import de.jpx3.intave.event.EventService;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.fakeplayer.event.FakePlayerEventService;
import de.jpx3.intave.filter.Filters;
import de.jpx3.intave.lib.asm.Frame;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.metric.Metrics;
import de.jpx3.intave.module.BootSegment;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import de.jpx3.intave.reflect.access.ReflectiveAccess;
import de.jpx3.intave.reflect.access.ReflectiveTPSAccess;
import de.jpx3.intave.reflect.hitbox.typeaccess.DualEntityTypeAccess;
import de.jpx3.intave.reflect.locate.Locator;
import de.jpx3.intave.resource.EncryptedResource;
import de.jpx3.intave.security.*;
import de.jpx3.intave.security.blacklist.BlackListService;
import de.jpx3.intave.security.letis.Letis;
import de.jpx3.intave.tool.AccessHelper;
import de.jpx3.intave.trustfactor.TrustFactorService;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.version.DurationTranslator;
import de.jpx3.intave.version.IntaveVersion;
import de.jpx3.intave.version.IntaveVersionList;
import de.jpx3.intave.version.JavaVersion;
import de.jpx3.intave.violation.ViolationProcessor;
import de.jpx3.intave.world.blockaccess.*;
import de.jpx3.intave.world.blockphysic.BlockPhysics;
import de.jpx3.intave.world.blockphysic.BlockProperties;
import de.jpx3.intave.world.blockshape.boxresolver.BoundingBoxResolver;
import de.jpx3.intave.world.blockshape.boxresolver.patcher.BoundingBoxPatcher;
import de.jpx3.intave.world.collider.Collider;
import de.jpx3.intave.world.collision.CollisionModifiers;
import de.jpx3.intave.world.fluid.Fluids;
import de.jpx3.intave.world.items.ItemProperties;
import de.jpx3.intave.world.permission.WorldPermission;
import de.jpx3.intave.world.raytrace.Raytracing;
import de.jpx3.intave.world.wrapper.link.WrapperLinkage;
import org.bukkit.ChatColor;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogRecord;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;
import static de.jpx3.intave.security.InterceptorFilterPrintStream.foundInterceptor;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VERSION_DETAILS;

@NameIntrinsicallyImportant
public final class IntavePlugin extends JavaPlugin {
  private static IntavePlugin singletonInstance;
  private static String version = "UNKNOWN";
  private static String prefix = ChatColor.translateAlternateColorCodes('&', "&8[&c&lIntave&8]&7 ");
  private static String defaultColor = ChatColor.getLastColors(prefix);;
  private static boolean offlineMode = false;

  static {
    // stage 1
  }

  private IntaveLogger logger;
  private ProxyMessenger proxyMessenger;
  private SibylIntegrationService sibylIntegrationService;
  private ConfigurationService configurationService;
  private ComponentLoader componentLoader;
  private EventService eventService;
  private FakePlayerEventService fakePlayerEventService;
  private CustomEventService customEventService;
  private ViolationProcessor violationProcessor;
  private CheckService checkService;
  private Filters filters;
  private TrustFactorService trustFactorService;
  private IntaveVersionList versions;
  private LabymodShadowIntegration shadowIntegration;
  private CustomClientSupportService customClientSupportService;
  private IntaveAccessService accessService;
  private IntaveAccess access;
  private BlackListService blackListService;
  private Letis letis;
  private Metrics metrics;

  public IntavePlugin() {
    // stage 2
    stage2();
  }

  @Native
  public void stage2() {
    singletonInstance = this;
    version = getDescription().getVersion();
    manifestDataFolder();
    this.logger = new IntaveLogger(this);
    this.logger.checkColorAvailability();
    Modules.prepareModules();
    Modules.proceedBoot(BootSegment.STAGE_2);
    redirectPluginLogger();
    checkClassLoaderAvailability();
  }

  @Native
  @Override
  public void onLoad() {
    // stage 3
    Modules.proceedBoot(BootSegment.STAGE_3);
  }

  @Native
  @Override
  public void onEnable() {
    logger.info("Please stand by..");
    // stage 4
    Modules.proceedBoot(BootSegment.STAGE_4);
    
    if (AgentAccessor.agentAvailable()) {
      logger.info("Using agent :{~"+"-"+"~}:");
    }

    prefix = ChatColor.translateAlternateColorCodes('&', prefix);

    SecurityManager securityManager = System.getSecurityManager();
    if (securityManager != null) {
      logger.error("A security manager of class " + securityManager.getClass().getName() + " is present, unable to start");
      bootFailure("Internal failure");
      return;
    }

    InterceptorDetection.setup();

    try {
      // We need to put this here before setting up the Synchronizer
      componentLoader = new ComponentLoader(this);
      componentLoader.loadComponents();

      ProtocolLibraryAdapter.checkIfOutdated();

      // check again, after ProtocolLibs availability is guaranteed
      logger.checkColorAvailability();

      // version mambo jumbo
      // stage 5
      Modules.proceedBoot(BootSegment.STAGE_5);

      Locator.setup();
      SinusCache.setup();
      ReflectiveTPSAccess.setup();
      Synchronizer.setup();
      ContextSecrets.setup();
      DualEntityTypeAccess.setup();

      trustFactorService = new TrustFactorService(this);

      // stage 6
      Modules.proceedBoot(BootSegment.STAGE_6);

      // we need to put this here
      BackgroundExecutor.start();

//      packetSubscriptionLinker = new PacketSubscriptionLinker(this);

      // stage 7
      configurationService = new ConfigurationService(this);
      String configurationKey = configurationService.configurationKey();

      // causes interceptor output
      for (int i = 0; i < 1; i++) {
        URL url = new URL("https://intave.de/api/versions.json");
        url.getDefaultPort();
      }

      if (foundInterceptor) {
        System.exit(1);
        return;
      }

      InterceptorDetection.revert();

      EncryptedResource contextStatusResource = new EncryptedResource("context-status", false);

      String requiredState = null; // leave this be
      boolean offlineMode = false;

      // ja das muss so krebsig hier hin
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        logger().info(ChatColor.DARK_RED + "This self-signed version bypasses certification requirements");
        System.setProperty("java.net.serviceprovider.key", "~bypass");
        VERSION_DETAILS |= 0x100;
        VERSION_DETAILS |= 0x200;
      } else {
        File currentJavaJarFile = new File(IntavePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        long identificationKey;
        try {
          identificationKey = Frame.class.getField("x").getLong(null);
        } catch (NoSuchFieldException exception) {
          identificationKey = 0;
        }
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
          byte value = (byte) ((identificationKey >> (i * 8) & 0xFF));
          bytes[7 - i] = value;
        }
        long longOne = ThreadLocalRandom.current().nextLong(0x4000000000000000L, Long.MAX_VALUE);
        long longTwo = ThreadLocalRandom.current().nextLong(0x4000000000000000L, Long.MAX_VALUE);
        String requestedId = String.valueOf(new UUID(longOne, longTwo));
        String idKey = identificationKey > 0 ? new String(bytes) : "aaaaaaaa", response = "";
        try {
          String path = "https://intave.de/auth.php";
          URL url = new URL(path);
          URLConnection connection = url.openConnection();
          connection.setUseCaches(false);
          connection.setDefaultUseCaches(false);
          connection.addRequestProperty("User-Agent", "Intave/" + version());
          connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
          connection.addRequestProperty("Pragma", "no-cache");
          connection.addRequestProperty("A", HashAccess.hashOf(currentJavaJarFile));
          connection.addRequestProperty("B", idKey);
          connection.addRequestProperty("C", HWIDVerification.publicHardwareIdentifier());
          connection.addRequestProperty("D", configurationKey);
          connection.addRequestProperty("E", LicenseAccess.rawLicense());
          connection.addRequestProperty("F", requestedId);
          connection.setConnectTimeout(2000);
          connection.setReadTimeout(2000);
          connection.connect();
          Scanner scanner2 = new Scanner(connection.getInputStream(), "UTF-8");
          StringBuilder raw2 = new StringBuilder();
          while (scanner2.hasNext())
            raw2.append(scanner2.next());
          response = raw2.toString();
          if (response.equalsIgnoreCase("timeout")) {
            response += "_";
          }
        } catch (IOException exception) {
          response = "timeout";
        }
        String message = "";
        boolean bad = false;
        boolean clearReloCache = false;
        // VMProtect doesn't like switches :(
        //noinspection IfCanBeSwitch
        if ("banned".equals(response) || "invalid".equals(response) || "error".equals(response)) {
          message = "Unable to boot: Something went wrong verifying integrity";
          bad = true;
          clearReloCache = true;
        } else if ("hwid".equals(response)) {
          message = "Unable to boot: Hardware identification failed (see website)";
          bad = true;
//          clearReloCache = true;
        } else if ("hwidr".equals(response)) {
          message = "Unable to boot: Hardware identification required (see website)";
          bad = true;
        } else if ("expired".equals(response)) {
          message = "Unable to boot: Buy Intave for continued use";
          bad = true;
          clearReloCache = true;
        } else if ("timeout".equals(response)) {
          message = "Unable to connect to service";
        }
        if (!message.isEmpty()) {
          logger.error(message);
        }
        if (clearReloCache) {
          String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
          String filePath = null;
          if (operatingSystem.contains("win")) {
            filePath = System.getenv("APPDATA") + "/Intave/Relocator/";
          } else {
            filePath = System.getProperty("user.home") + "/.intave/relocator/";
          }
          File workDirectory = new File(filePath);
          File[] files = workDirectory.listFiles();
          if (workDirectory.exists() && files != null) {
            for (File file : files) {
              file.delete();
            }
          }
        }
        if (bad) {
          contextStatusResource.write(new ByteArrayInputStream(("failure-"+response).getBytes(StandardCharsets.UTF_8)));
          bootFailure(message);
          performShutdown();
          return;
        }
        if (response.equals("timeout")) {
          System.setProperty("java.net.serviceprovider.key", "~timeout");
          offlineMode = true;
          requiredState = null;
        } else {
          // Intavede#key1=value1#key2=value2 ...
          String[] split = response.split("#");
          String licenseName = split[0];
          System.setProperty("java.net.serviceprovider.key", licenseName);
          Map<String, String> properties = new HashMap<>();
          boolean first = true;
          for (String propertyPair : split) {
            if (first) {
              first = false;
              continue;
            }
            String[] split1 = propertyPair.split("=");
            properties.put(split1[0], split1[1]);
          }
          if (properties.isEmpty()) {
            logger.error("Invalid server response " + response);
            contextStatusResource.write(new ByteArrayInputStream(("failure-"+response).getBytes(StandardCharsets.UTF_8)));
            bootFailure("Internal failure");
            performShutdown();
            return;
          }
          if (VERSION_DETAILS == 97) {
            requiredState = properties.get("configuration-hash");
            if (properties.containsKey("partner")) {
              VERSION_DETAILS |= 0b0100000000;
            }
            if (properties.containsKey("enterprise")) {
              VERSION_DETAILS |= 0b1000000000;
            }
          } else {
            VERSION_DETAILS = 0b0001100001;
          }
          String keyResponse = properties.get("exchange-key");
          // verify the server integrity
          boolean validResponse = false;
          long receivedMSB = 0;
          long receivedLSB = 0;
          if (keyResponse != null) {
            UUID receivedResponse = UUID.fromString(keyResponse);
            for (int i = 0; i < 64; i++) {
              longOne |= (longTwo & (1L << i));
              longTwo |= (longOne & (1L << i * 2));
            }
            receivedMSB = receivedResponse.getMostSignificantBits();
            receivedLSB = receivedResponse.getLeastSignificantBits();
            validResponse = receivedMSB == longOne && receivedLSB == longTwo;
          }
          if (!validResponse || foundInterceptor) {
            logger.error("Unable to boot: Authentication response not trustworthy");
            contextStatusResource.write(new ByteArrayInputStream(("failure-"+response).getBytes(StandardCharsets.UTF_8)));
            bootFailure("Internal failure");
            performShutdown();
            return;
          }
        }
      }

      boolean partner = (VERSION_DETAILS & 0x100) != 0;
      boolean enterprise = (VERSION_DETAILS & 0x200) != 0;

      if (partner || enterprise) {
        logger.info("Identity confirmed");
      }

      if (offlineMode) {
        // check last online
        boolean allowLeniency = IntaveControl.DISABLE_LICENSE_CHECK;
        //noinspection ConstantConditions
        if (!allowLeniency && contextStatusResource.exists()) {
          InputStream input = contextStatusResource.read();
          Scanner scanner = new Scanner(input);
          StringBuilder text = new StringBuilder();
          while (scanner.hasNext()) {
            text.append(scanner.next());
          }
          String textString = text.toString();
          if (textString.startsWith("success")) {
            Long lastSuccessfulStart = null;
            try {
              lastSuccessfulStart = Long.valueOf(textString.split("/")[1]);
            } catch (Exception ignored) {}
            if (lastSuccessfulStart != null) {
              try {
                String url_path = "https://raw.githubusercontent.com/Jpx3/IntaveStatus/main/availability";
                URL url = new URL(url_path);
                URLConnection connection = url.openConnection();
                connection.setUseCaches(false);
                connection.setDefaultUseCaches(false);
                connection.addRequestProperty("User-Agent", "Intave/" + version());
                connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                connection.addRequestProperty("Pragma", "no-cache");
                connection.connect();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                if (AccessHelper.now() - lastSuccessfulStart <= TimeUnit.DAYS.toMillis(3)) {
                  try {
                    connection.connect();
                    allowLeniency = true;
                  } catch (Exception ignored) {}
                } else {
                  // perform github check
                  Scanner scanner2 = new Scanner(connection.getInputStream(), "UTF-8");
                  Map<String, String> availabilities = new HashMap<>();
                  while (scanner2.hasNextLine()) {
                    String line = scanner2.nextLine();
                    String[] split = line.split("=");
                    availabilities.put(split[0], split[1]);
                  }
                  allowLeniency = availabilities.get("intave.de").equalsIgnoreCase("false");
                }
              } catch (Exception exception) {
                allowLeniency = false;
              }
            }
          }
        }

        if (allowLeniency && !configurationService().loader().configurationCacheExists()) {
          logger().error("Unable to boot: Intave requires an internet connection for first-time startup");
          bootFailure("No internet connection");
          performShutdown();
          return;
        }

        if (!allowLeniency) {
          logger().error("Unable to boot: Internet connection required to proceed");
          bootFailure("No internet connection");
          performShutdown();
          return;
        }
      } else {
        boolean writeSuccessLog = true;
        try {
          if (contextStatusResource.exists()) {
            InputStream input = contextStatusResource.read();
            Scanner scanner = new Scanner(input);
            StringBuilder text = new StringBuilder();
            while (scanner.hasNext()) {
              text.append(scanner.next());
            }
            String textString = text.toString();
            if (textString.startsWith("success")) {
              long lastSuccessfulStart = Long.parseLong(textString.split("/")[1]);
              if (AccessHelper.now() - lastSuccessfulStart < TimeUnit.DAYS.toMillis(2)) {
                writeSuccessLog = false;
              }
            }
          }
        } catch (Exception ignored) {}
        if (writeSuccessLog) {
          contextStatusResource.write(new ByteArrayInputStream(("success/" + AccessHelper.now()).getBytes(StandardCharsets.UTF_8)));
        }
      }
      
      // stage 7
      Modules.proceedBoot(BootSegment.STAGE_7);

      SSLConnectionVerifier.setup();
      BlockVariantRegister.prepareIndex();

      BoundingBoxResolver.setup();
      WrappedEntity.setup();
      ReflectiveAccess.setup();
      UserRepository.setup();
      WrapperLinkage.setup();
      Raytracing.setup();
      Collider.setup();
      Fluids.setup();
      BukkitBlockAccess.setup();
      BlockAccessProvider.setup();
      BlockInnerAccess.setup();
      BlockVariantAccess.setup();
      BlockTypeAccess.setup();
      CollisionModifiers.setup();
      ViaVersionAdapter.setup();
      WorldPermission.setup();
      BlockPhysics.setup();
      BlockProperties.setup();
      ItemProperties.setup();
      BoundingBoxPatcher.setup();

      versions = new IntaveVersionList();
      try {
        versions.setup();
      } catch (Exception | Error exception) {
        logger.error("Something went wrong checking version");
        exception.printStackTrace();
      }

      IntavePlugin.offlineMode = offlineMode;

      // resolve config hash
      configurationService.setupConfiguration(requiredState);
      prefix = configurationService.configuration().getString("layout.prefix", prefix);
      prefix = ChatColor.translateAlternateColorCodes('&', prefix);
      defaultColor = ChatColor.getLastColors(prefix);

      // stage 8
      Modules.proceedBoot(BootSegment.STAGE_8);
      filters = new Filters(this);
      filters.setup();
      shadowIntegration = new LabymodShadowIntegration(this);
      shadowIntegration.setup();
      accessService = new IntaveAccessService(this);
      accessService.setup();
      customClientSupportService = new CustomClientSupportService(this);
      customClientSupportService.setup();
      customEventService = new CustomEventService(this);
      checkService = new CheckService(this);
      violationProcessor = new ViolationProcessor(this);
      eventService = new EventService(this);
      fakePlayerEventService = new FakePlayerEventService(this);
      proxyMessenger = new ProxyMessenger(this);
      sibylIntegrationService = new SibylIntegrationService(this);
      blackListService = new BlackListService(this);
      letis = new Letis(this);

      getCommand("intave").setExecutor(new CommandProcessor());

      if (IntaveControl.DISABLE_BLOCK_CACHING_ENTIRELY) {
        logger().info("This version does not cache block-accesses");
      }

      // stage 9
      Modules.proceedBoot(BootSegment.STAGE_9);
      metrics = new Metrics(this, 6019);

      trustFactorService.setup();
      checkService.setup();
      customEventService.setup();
      eventService.setup();
      fakePlayerEventService.setup();
      blackListService.setup();
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

    ViaVersionAdapter.patchConfiguration();

    GarbageCollector.setup();
    BackgroundExecutor.execute(this::clearIntegrityGarbage);
    BackgroundExecutor.execute(this::clearSaveFolderGarbage);
    logger.performCompression();

    RuntimeDiagnostics.applicationBoot();

    if (JavaVersion.current() < 16) {
      String noticePrefix = ChatColor.DARK_RED + "Notice" + ChatColor.DARK_GRAY + ": ";
      logger.info(noticePrefix + ChatColor.RED + "Upgrading to Java 16 has incredible performance benefits");
      logger.info(noticePrefix + ChatColor.RED + "We strongly recommend you update Java now");
      logger.info(noticePrefix + ChatColor.RED + "Support for older versions of Java might eventually be dropped");
    }

    Modules.linker().packetEvents().refreshLinkages();
    displayVersionInformation();
    logger.info( "Intave booted successfully");

    Synchronizer.synchronize(() -> {
      // stage 11
      Modules.proceedBoot(BootSegment.STAGE_11);
    });
  }

  public void manifestDataFolder() {
    try {
      File dataFolder = new File(getServer().getUpdateFolderFile().getParentFile(), "Intave");
      if (!(dataFolder.exists() || dataFolder.mkdirs())) {
        logger.error("Failed to create Intave folder " + dataFolder.getAbsolutePath());
      }
      Field dataFolderField = JavaPlugin.class.getDeclaredField("dataFolder");
      dataFolderField.setAccessible(true);
      dataFolderField.set(this, dataFolder);
    } catch (IllegalAccessException | NoSuchFieldException exception) {
      exception.printStackTrace();
    }
  }

  public void redirectPluginLogger() {
    PluginLogger pluginLogger = new PluginLogger(this) {
      @Override
      public void log(LogRecord logRecord) {
        logger().info(logRecord.getMessage());
      }
    };
    try {
      Field loggerField = JavaPlugin.class.getDeclaredField("logger");
      loggerField.setAccessible(true);
      loggerField.set(this, pluginLogger);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  public void checkClassLoaderAvailability() {
    if (!IntaveControl.DISABLE_LICENSE_CHECK) {
      return;
    }
    if (de.jpx3.classloader.ClassLoader.usesNativeAccess() && !de.jpx3.classloader.ClassLoader.loaded()) {
      try {
        de.jpx3.classloader.ClassLoader.setupEnvironment(Files.createTempDirectory("intave-debug").toFile());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Native
  public void displayVersionInformation() {
    IntaveVersion version = versions.versionInformation(version());
    if (version == null) {
      logger().info(ChatColor.YELLOW + "This version of Intave is not listed in the official version index");
    } else {
      long duration = AccessHelper.now() - version.release();
      String durationAsString = DurationTranslator.translateDuration(duration);

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

  public final static long INTEGRITY_ERASE_BUFFER = TimeUnit.MINUTES.toMillis(1);

  @Native
  public void clearIntegrityGarbage() {
    File tempDir = new File(System.getProperty("java.io.tmpdir"));
    try {
      Files.walk(tempDir.toPath())
        .map(Path::toFile)
        .filter(File::canRead)
        .filter(File::canWrite)
        .filter(file -> file.getName().equalsIgnoreCase("deleteme") && file.getParentFile().getName().toLowerCase(Locale.ROOT).contains("intave"))
        .filter(file -> (AccessHelper.now() - file.lastModified()) > INTEGRITY_ERASE_BUFFER)
        .map(File::getParentFile)
        .filter(File::canRead)
        .filter(File::canWrite)
        .forEach(file -> {
          try {
            clearDirectory(file);
          } catch (IOException ignored) {}
        });
    } catch (Exception ignored) {}
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
        } catch (IOException ignored) {}
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

  private final static long FILE_EXPIRE = TimeUnit.DAYS.toMillis(90);

  @Native
  public void clearSaveFolderGarbage() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/";
    } else {
      if (GOMME_MODE) {
        filePath = ContextSecrets.secret("cache-directory");
      } else {
        filePath = System.getProperty("user.home") + "/.intave/";
      }
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      return;
    }
    try {
      Files.walk(workDirectory.toPath())
        .filter(Files::isRegularFile)
        .map(Path::toFile)
        .filter(File::canWrite)
        .filter(File::canRead)
        .filter(file -> (AccessHelper.now() - file.lastModified()) > FILE_EXPIRE)
        .forEach(file -> {
          try {
            file.delete();
          } catch (Exception exception) {
            exception.printStackTrace();
          }
        });
    } catch (Exception | Error throwable) {
      throwable.printStackTrace();
    }
  }

  @Native
  public void invalidateCaches() {
    if (configurationService != null) {
      configurationService.deleteCache();
    }
    clearIntegrityGarbage();
  }

  @Native
  public void bootFailure(String reason) {
    getCommand("intave").setExecutor((commandSender, command, s, strings) -> {
      commandSender.sendMessage(prefix() + ChatColor.RED + "Intave couldn't boot properly: " + reason);
      return false;
    });
  }

  @Native
  @Override
  public void onDisable() {
    performShutdown();
  }

  @Native
  public void performShutdown() {
    logger.info("Stopping Intave");
    BackgroundExecutor.stopBlocking();
    Shutdown.executeShutdownTasks();
    deleteIntegrityCache();
    logger.info("Intave offline");
    logger.shutdown();
  }

  private void deleteIntegrityCache() {
    try {
      // mark caches as deletable
      Class<?> relocator = Class.forName("de.jpx3.relocator.Relocator");
      relocator.getMethod("i").invoke(null);
    } catch (Exception ignored) {}
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

  public CustomEventService customEventService() {
    return customEventService;
  }

  public IntaveLogger logger() {
    return logger;
  }

  public ProxyMessenger proxy() {
    return proxyMessenger;
  }

  public CheckService checks() {
    return checkService;
  }

  public ConfigurationService configurationService() {
    return configurationService;
  }

  @Deprecated
  public EventService eventService() {
    return eventService;
  }

  public FakePlayerEventService fakePlayerEventService() {
    return fakePlayerEventService;
  }

  @Deprecated
  public BukkitEventSubscriptionLinker eventLinker() {
    return Modules.linker().bukkitEvents();
  }

  @Deprecated
  public PacketSubscriptionLinker packetSubscriptionLinker() {
    return Modules.linker().packetEvents();
  }

  public ViolationProcessor violationProcessor() {
    return this.violationProcessor;
  }

  public SibylIntegrationService sibylIntegrationService() {
    return sibylIntegrationService;
  }

  public IntaveVersionList versions() {
    return versions;
  }

  public static String version() {
    return version;
  }

  public static String prefix() {
    return prefix;
  }

  public static boolean isInOfflineMode() {
    return offlineMode;
  }

  public static String defaultColor() {
    return defaultColor;
  }

  public static IntavePlugin singletonInstance() {
    return singletonInstance;
  }
}