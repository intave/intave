package de.jpx3.intave;

import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.accessbackend.IntaveAccessService;
import de.jpx3.intave.adapter.ComponentLoader;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.agent.IntaveAgentAccessor;
import de.jpx3.intave.command.CommandProcessor;
import de.jpx3.intave.config.ConfigurationService;
import de.jpx3.intave.connect.proxy.ProxyMessenger;
import de.jpx3.intave.connect.shadow.LabymodShadowIntegration;
import de.jpx3.intave.connect.sibyl.SibylIntegrationService;
import de.jpx3.intave.detect.CheckService;
import de.jpx3.intave.event.EventService;
import de.jpx3.intave.event.bukkit.BukkitEventLinker;
import de.jpx3.intave.event.packet.PacketSubscriptionLinker;
import de.jpx3.intave.event.service.CustomEventService;
import de.jpx3.intave.event.service.violation.ViolationProcessor;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.fakeplayer.event.FakePlayerEventService;
import de.jpx3.intave.filter.Filters;
import de.jpx3.intave.lib.asm.Frame;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.metrics.Metrics;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.relocate.ClassRelocator;
import de.jpx3.intave.security.*;
import de.jpx3.intave.tools.*;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.link.WrapperLinkage;
import de.jpx3.intave.trustfactor.TrustFactorService;
import de.jpx3.intave.update.Version;
import de.jpx3.intave.update.VersionList;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.warning.ClientWarningService;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockphysics.BlockPhysics;
import de.jpx3.intave.world.collider.Collider;
import de.jpx3.intave.world.collision.BoundingBoxAccess;
import de.jpx3.intave.world.collision.patches.BoundingBoxPatcher;
import de.jpx3.intave.world.permission.WorldPermission;
import de.jpx3.intave.world.raytrace.Raytracer;
import de.jpx3.intave.world.waterflow.Waterflow;
import org.apache.commons.io.FileUtils;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

public final class IntavePlugin extends JavaPlugin {
  private static IntavePlugin singletonInstance;
  private static String version = "UNKNOWN";
  private static String prefix = "&8[&c&lIntave&8]&7 ";
  private static String defaultColor = "";
  private static boolean offlineMode = false;

  static {
    // stage 1

  }

  private IntaveLogger logger;
  private ProxyMessenger proxyMessenger;
  private SibylIntegrationService sibylIntegrationService;
  private ConfigurationService configurationService;
  private ComponentLoader componentLoader;
  private BukkitEventLinker eventLinker;
  private PacketSubscriptionLinker packetSubscriptionLinker;
  private EventService eventService;
  private FakePlayerEventService fakePlayerEventService;
  private CustomEventService customEventService;
  private ViolationProcessor violationProcessor;
  private CheckService checkService;
  private Filters filters;
  private WorldPermission worldPermission;
  private TrustFactorService trustFactorService;
  private VersionList versionList;
  private LabymodShadowIntegration shadowIntegration;
  private ClientWarningService clientWarningService;
  private IntaveAccessService accessService;
  private IntaveAccess access;
  private Metrics metrics;

  public IntavePlugin() {
    // stage 2
    stage2();
  }

  @Native
  public void stage2() {
    singletonInstance = this;
    version = getDescription().getVersion();
    this.logger = new IntaveLogger(this);
  }

  @Native
  @Override
  public void onLoad() {
    // stage 3

    // event links must be available throughout the onEnable call
    eventLinker = new BukkitEventLinker(this);
  }

  @Native
  @Override
  public void onEnable() {
    logger.info("Please stand by..");
    // stage 4

    if(IntaveAgentAccessor.agentAvailable()) {
      logger.info("Using agent :{~"+"-"+"~}:");
    }

    prefix = ChatColor.translateAlternateColorCodes('&', prefix);

    SecurityManager securityManager = System.getSecurityManager();
    if(securityManager != null) {
      logger.error("A security manager of class " + securityManager.getClass().getName() + " is present, unable to start");
      return;
    }

    try {
      SinusCache.setup();
      TpsResolver.setup();
      Synchronizer.setup();
      ContextSecrets.setup();
      BackgroundExecutor.start();

      trustFactorService = new TrustFactorService(this);
      // version mambo jumbo

      // stage 5
      componentLoader = new ComponentLoader(this);
      componentLoader.loadComponents();

      packetSubscriptionLinker = new PacketSubscriptionLinker(this);

      // stage 6

      ProtocolLibAdapter.checkIfOutdated();

      // stage 7
      configurationService = new ConfigurationService(this);
      String configurationKey = configurationService.configurationKey();

      if(IntaveControl.USE_EXTERNAL_CONFIGURATION_FILE || configurationKey.equalsIgnoreCase("file")) {
        logger.info("Using the file configuration");
      } else {
        logger.info("Using the \"" + configurationKey + "\" configuration");
      }

      // license check call

      EncryptedResource contextStatusResource = new EncryptedResource("context-status", false);

      String requiredState = null; // leave this be
      boolean offlineMode = false;

      // ja das muss so krebsig hier hin
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        logger().info("This self-signed version bypasses certification requirements");
        System.setProperty("java.net.serviceprovider.key", "~bypass");

        UserMetaClientData.VERSION_DETAILS |= 0x100;
        UserMetaClientData.VERSION_DETAILS |= 0x200;
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

        long longOne = ThreadLocalRandom.current().nextLong(0x4000000000000000L, Long.MAX_VALUE), originalLongOne = longOne;
        long longTwo = ThreadLocalRandom.current().nextLong(0x4000000000000000L, Long.MAX_VALUE), originalLongTwo = longTwo;
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
          connection.addRequestProperty("E", LicenseVerification.rawLicense());
          connection.addRequestProperty("F", requestedId);
          connection.connect();
          connection.setConnectTimeout(4000);
          connection.setReadTimeout(4000);
          Scanner scanner2 = new Scanner(connection.getInputStream(), "UTF-8");
          StringBuilder raw2 = new StringBuilder();
          while (scanner2.hasNext())
            raw2.append(scanner2.next());
          response = raw2.toString();
          if(response.equalsIgnoreCase("timeout")) {
            response += "_";
          }
        } catch (IOException exception) {
//          exception.printStackTrace();
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

        if(clearReloCache) {
          String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
          String filePath = null;
          if(operatingSystem.contains("win")) {
            filePath = System.getenv("APPDATA") + "/Intave/Relocator/";
          } else {
            filePath = System.getProperty("user.home") + "/.intave/relocator/";
          }
          File workDirectory = new File(filePath);
          File[] files = workDirectory.listFiles();
          if(workDirectory.exists() && files != null) {
            for (File file : files) {
              file.delete();
            }
          }
        }

        if (bad) {
          contextStatusResource.write(new ByteArrayInputStream(("failure-"+response).getBytes(StandardCharsets.UTF_8)));
          boolFailure();
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

          if(properties.isEmpty()) {
            logger.error("Invalid server response " + response);
            contextStatusResource.write(new ByteArrayInputStream(("failure-"+response).getBytes(StandardCharsets.UTF_8)));
            boolFailure();
            performShutdown();
            return;
          }

          requiredState = properties.get("configuration-hash");
          String keyResponse = properties.get("exchange-key");
          if(properties.containsKey("partner")) {
            UserMetaClientData.VERSION_DETAILS |= 0x100;
          }
          if(properties.containsKey("enterprise")) {
            UserMetaClientData.VERSION_DETAILS |= 0x200;
          }

          // verify the server integrity
          boolean validResponse = false;
          long receivedMSB = 0;
          long receivedLSB = 0;
          if(keyResponse != null) {
            UUID receivedResponse = UUID.fromString(keyResponse);
            for (int i = 0; i < 64; i++) {
              longOne |= (longTwo & (1L << i));
              longTwo |= (longOne & (1L << i * 2));
            }
            receivedMSB = receivedResponse.getMostSignificantBits();
            receivedLSB = receivedResponse.getLeastSignificantBits();
            validResponse = receivedMSB == longOne && receivedLSB == longTwo;
          }
          if(!validResponse) {
            logger.error("Unable to boot: Authentication response not trustworthy");
            contextStatusResource.write(new ByteArrayInputStream(("failure-"+response).getBytes(StandardCharsets.UTF_8)));
            boolFailure();
            performShutdown();
            return;
          }
        }
      }

      boolean partner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
      boolean enterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;

      if (partner || enterprise) {
        logger.info("Identity confirmed, special features enabled");
      } else {
        logger.info("Identity verification missing");
      }

      ClassRelocator.findClass("de.jpx3.intave.IntavePlugin");

      if (offlineMode) {
        // check last online
        boolean allowLeniency = IntaveControl.DISABLE_LICENSE_CHECK;
        //noinspection ConstantConditions
        if(!allowLeniency && contextStatusResource.exists()) {
          InputStream input = contextStatusResource.read();
          Scanner scanner = new Scanner(input);
          StringBuilder text = new StringBuilder();
          while (scanner.hasNext()) {
            text.append(scanner.next());
          }
          String textString = text.toString();
          if(textString.startsWith("success")) {
            Long lastSuccessfulStart = null;
            try {
              lastSuccessfulStart = Long.valueOf(textString.split("/")[1]);
            } catch (Exception ignored) {}
            if(lastSuccessfulStart != null) {
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
              if(AccessHelper.now() - lastSuccessfulStart <= TimeUnit.DAYS.toMillis(3)) {
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
            }
          }
        }

        if(allowLeniency && !configurationService().loader().configurationCacheExists()) {
          logger().error("Unable to boot: Intave requires an internet connection for first-time startup");
          boolFailure();
          performShutdown();
          return;
        }

        if(!allowLeniency) {
          logger().error("Unable to boot: Internet connection required to proceed");
          boolFailure();
//          Synchronizer.synchronize(this::performShutdown);
          performShutdown();
          return;
        }
      } else {
        contextStatusResource.write(new ByteArrayInputStream(("success/" + AccessHelper.now()).getBytes(StandardCharsets.UTF_8)));
      }

      SSLConnectionVerifier.setup();

      ReflectiveAccess.setup();
      WrapperLinkage.setup();
      Raytracer.setup();
      Collider.setup();
      Waterflow.setup();
      BukkitBlockAccess.setup();
      BlockDataAccess.setup();
      ViaVersionAdapter.setup();
      BoundingBoxAccess.setup();
      WorldPermission.setup(this);
      BlockPhysics.setup();
      InventoryUseItemHelper.setup();
      BoundingBoxPatcher.setup();

      versionList = new VersionList();
      versionList.setup();
      displayVersionInformation();

      IntavePlugin.offlineMode = offlineMode;

      // resolve config hash
      configurationService.setupConfiguration(requiredState);

      prefix = configurationService.configuration().getString("layout.prefix", prefix);
      prefix = ChatColor.translateAlternateColorCodes('&', prefix);
      defaultColor = ChatColor.getLastColors(prefix);

      filters = new Filters(this);
      filters.setup();

      shadowIntegration = new LabymodShadowIntegration(this);
      shadowIntegration.setup();

      accessService = new IntaveAccessService(this);
      accessService.setup();

      clientWarningService = new ClientWarningService(this);
      clientWarningService.setup();

      customEventService = new CustomEventService(this);
      checkService = new CheckService(this);
      violationProcessor = new ViolationProcessor(this);
      eventService = new EventService(this);
      fakePlayerEventService = new FakePlayerEventService(this);
      proxyMessenger = new ProxyMessenger(this);
      sibylIntegrationService = new SibylIntegrationService(this);

      getCommand("intave").setExecutor(new CommandProcessor());

      if(IntaveControl.DISABLE_BLOCK_CACHING_ENTIRELY) {
        logger().info("This version does not cache block-accesses");
      }

      // stage 8
      metrics = new Metrics(this, 6019);

      trustFactorService.setup();
      checkService.setup();
      customEventService.setup();
      eventService.setup();
      fakePlayerEventService.setup();
    } catch (Exception exception) {
      logger.error("Unable to boot: " + exception.getMessage());
      exception.printStackTrace();

      clearCacheFiles();
      boolFailure();
      performShutdown();
      return;
    }

    BackgroundExecutor.execute(this::clearIntegrityGarbage);
    BackgroundExecutor.execute(this::clearSaveFolderGarbage);
    packetSubscriptionLinker.refreshLinkages();
    logger.info("Intave booted successfully");
  }

  @Native
  public void displayVersionInformation() {
    Version version = versionList.versionInformation(version());
    if (version == null) {
      logger().info("This version of Intave is not listed in the official version index");
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
        case INVALID:
          logger().error("Unable to boot: This version has been deactivated");
          boolFailure();
          performShutdown();
          return;
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
        .filter(Files::isRegularFile)
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
            FileUtils.deleteDirectory(file);
          } catch (IOException exception) {
//            exception.printStackTrace();
          }
        });
    } catch (Exception exception) {
//      exception.printStackTrace();
    }
  }

  private final static long FILE_EXPIRE = TimeUnit.DAYS.toMillis(90);

  @Native
  public void clearSaveFolderGarbage() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if(operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/";
    } else {
      if(GOMME_MODE) {
        filePath = ContextSecrets.secret("cache-directory");
      } else {
        filePath = System.getProperty("user.home") + "/.intave/";
      }
    }
    workDirectory = new File(filePath);
    if(!workDirectory.exists()) {
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
  public void clearCacheFiles() {
    if(configurationService != null) {
      configurationService.deleteCache();
    }
    clearIntegrityGarbage();
  }

  @Native
  public void boolFailure() {
    getCommand("intave").setExecutor((commandSender, command, s, strings) -> {
      commandSender.sendMessage(prefix() + ChatColor.RED + "Intave couldn't boot properly");
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
    logger().info("Stopping Intave");
    BackgroundExecutor.stopBlocking();
    GarbageCollector.die();
    UserRepository.die();
    if(shadowIntegration != null) {
      shadowIntegration.shutdown();
    }
    if(packetSubscriptionLinker != null) {
      packetSubscriptionLinker.reset();
    }
    if(eventLinker != null) {
      eventLinker.performShutdown();
    }
    if(accessService != null) {
      accessService.serverAccessor().pluginShutdown();
    }
    if(proxyMessenger != null) {
      proxyMessenger.closeChannel();
    }
    try {
      // mark caches as deletable
      Class<?> relocator = Class.forName("de.jpx3.relocator.Relocator");
      relocator.getMethod("i").invoke(null);
    } catch (Exception ignored) {}
    logger().info("Intave offline");
    logger.shutdown();
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

  public ProxyMessenger proxyMessenger() {
    return proxyMessenger;
  }

  public CheckService checkService() {
    return checkService;
  }

  public ConfigurationService configurationService() {
    return configurationService;
  }

  public EventService eventService() {
    return eventService;
  }

  public FakePlayerEventService fakePlayerEventService() {
    return fakePlayerEventService;
  }

  public BukkitEventLinker eventLinker() {
    return eventLinker;
  }

  public PacketSubscriptionLinker packetSubscriptionLinker() {
    return packetSubscriptionLinker;
  }

  public ViolationProcessor violationProcessor() {
    return this.violationProcessor;
  }

  public WorldPermission interactionPermissionService() {
    return worldPermission;
  }

  public SibylIntegrationService sibylIntegrationService() {
    return sibylIntegrationService;
  }

  public ClientWarningService clientWarningService() {
    return clientWarningService;
  }

  public VersionList versionList() {
    return versionList;
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