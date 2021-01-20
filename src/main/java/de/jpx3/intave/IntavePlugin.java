package de.jpx3.intave;

import de.jpx3.intave.adapter.ComponentLoader;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.command.CommandProcessor;
import de.jpx3.intave.config.ConfigurationService;
import de.jpx3.intave.connect.proxy.ProxyMessenger;
import de.jpx3.intave.connect.sibyl.SibylIntegrationService;
import de.jpx3.intave.detect.CheckService;
import de.jpx3.intave.event.EventService;
import de.jpx3.intave.event.bukkit.BukkitEventLinker;
import de.jpx3.intave.event.packet.PacketSubscriptionLinker;
import de.jpx3.intave.event.service.CustomEventService;
import de.jpx3.intave.event.service.ViolationService;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.lib.asm.Frame;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.security.HWIDVerification;
import de.jpx3.intave.security.SSLConnectionVerifier;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.DurationTranslator;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.trustfactor.TrustFactorService;
import de.jpx3.intave.update.VersionInformation;
import de.jpx3.intave.update.VersionList;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.collision.patches.BoundingBoxPatcher;
import de.jpx3.intave.world.permission.InteractionPermissionService;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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
  private CustomEventService customEventService;
  private ViolationService violationService;
  private CheckService checkService;
  private InteractionPermissionService interactionPermissionService;
  private TrustFactorService trustFactorService;
  private VersionList versionList;

  public IntavePlugin() {
    // stage 2
    singletonInstance = this;
    version = getDescription().getVersion();
    this.logger = new IntaveLogger(this);
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

    prefix = ChatColor.translateAlternateColorCodes('&', prefix);

    try {
      SinusCache.setup();
      Synchronizer.setup();
      BackgroundExecutor.start();

      componentLoader = new ComponentLoader(this);
      componentLoader.loadComponents();

      trustFactorService = new TrustFactorService(this);
      // version mambo jumbo

      // stage 5

      packetSubscriptionLinker = new PacketSubscriptionLinker(this);

      // stage 6

//    BlockBoxResolver.setup();

      ProtocolLibAdapter.checkIfOutdated();

      Raytracer.setup();
      BlockAccessor.setup();
      ViaVersionAdapter.setup();
      InventoryUseItemHelper.setup();
      BoundingBoxPatcher.setup();
      // stage 7
      configurationService = new ConfigurationService(this);
      String configurationKey = configurationService.configurationKey();
      logger.info("Using the \"" + configurationKey + "\" configuration");

      // license check call

      String requiredConfigurationHash;

      // ja das muss so krebsig hier hin
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        logger().info("This self-signed version bypasses certification requirements");
        System.setProperty("8ugyoiodfg", "~bypass");
        requiredConfigurationHash = null;
      } else {
        File currentJavaJarFile = new File(IntavePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        StringBuilder jarChecksum = new StringBuilder();
        try {
          MessageDigest md = MessageDigest.getInstance("SHA-256");// MD5
          FileInputStream fis = new FileInputStream(currentJavaJarFile);
          byte[] dataBytes = new byte[1024];
          int nread;
          while ((nread = fis.read(dataBytes)) != -1) {
            md.update(dataBytes, 0, nread);
          }
          byte[] mdbytes = md.digest();
          for (byte mdbyte : mdbytes)
            jarChecksum.append(Integer.toString((mdbyte & 0xff) + 0x100, 16).substring(1));
        } catch (NoSuchAlgorithmException | IOException exception) {
          exception.printStackTrace();
          jarChecksum.append("~invalid");
        }
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
        String idKey = identificationKey > 0 ? new String(bytes) : "aaaaaaaa", response = "";
        try {
          String url_path = "https://intave.de/auth.php";
          URL url = new URL(url_path);
          URLConnection connection = url.openConnection();
          connection.setUseCaches(false);
          connection.setDefaultUseCaches(false);
          connection.addRequestProperty("User-Agent", "Intave/" + version());
          connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
          connection.addRequestProperty("Pragma", "no-cache");
          connection.addRequestProperty("A", jarChecksum.toString());
          connection.addRequestProperty("B", idKey);
          connection.addRequestProperty("C", HWIDVerification.publicHardwareIdentifier());
          connection.addRequestProperty("D", configurationKey);
          connection.connect();
          SSLConnectionVerifier.verifyURLConnection((HttpsURLConnection) connection);
          connection.setConnectTimeout(4000);
          connection.setReadTimeout(4000);
          Scanner scanner2 = new Scanner(connection.getInputStream(), "UTF-8");
          StringBuilder raw2 = new StringBuilder();
          while (scanner2.hasNext())
            raw2.append(scanner2.next());
          response = raw2.toString();
//          System.out.println(response);
        } catch (IOException exception) {
          exception.printStackTrace();
          response = "timeout";
        }
        String message;
        boolean bad = false;
        switch (response) {
          case "banned":
          case "invalid":
            message = "Unable to boot: Something went wrong verifying integrity";
            bad = true;
            break;
          case "hwid":
            message = "Unable to boot: Hardware identification failed";
            bad = true;
            break;
          case "hwidr":
            message = "Unable to boot: You need to enable hardware locking (see website)";
            bad = true;
            break;
          case "expired":
            message = "Unable to boot: Buy Intave for continued use";
            bad = true;
            break;
          case "timeout":
            message = "Unable to connect to service";
            break;
          default:
            message = null;
            break;
        }
        if (message != null) {
          logger().error(message);
        }
        if (bad || response.length() < 2) {
          getCommand("intave").setExecutor((commandSender, command, s, strings) -> {
            commandSender.sendMessage(prefix() + ChatColor.RED + "Intave couldn't boot properly");
            return false;
          });
          performShutdown();
          return;
        }

        if (response.equals("timeout")) {
          System.setProperty("8ugyoiodfg", "~timeout");
          offlineMode = true;
          requiredConfigurationHash = null;
        } else {
          // Intavede#key1=value1#key2=value2 ...

          String[] split = response.split("#");
          String licenseName = split[0];
          System.setProperty("8ugyoiodfg", licenseName);

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
          requiredConfigurationHash = properties.get("configuration-hash");
        }
      }

      if (offlineMode && !configurationService.loader().configurationCacheExists()) {
        logger().error("Unable to boot: Intave requires an internet connection for first-time startup");
        getCommand("intave").setExecutor((commandSender, command, s, strings) -> {
          commandSender.sendMessage(prefix() + ChatColor.RED + "Intave couldn't boot properly");
          return false;
        });
        performShutdown();
        return;
      }

      versionList = new VersionList();
      versionList.setup();

      VersionInformation versionInformation = versionList.versionInformation(version());

      if (versionInformation == null) {
        logger().info("This version of Intave is not listed in the official index");
      } else {
        long duration = AccessHelper.now() - versionInformation.release();
        String durationAsString = DurationTranslator.translateDuration(duration);

        String infoMessage = "";
        switch (versionInformation.typeClassifier()) {
          case LATEST:
            infoMessage = "Using the latest version of Intave (" + durationAsString + " old)";
            break;
          case STABLE:
            infoMessage = "Using a stable version of Intave (" + durationAsString + " old)";
            break;
          case OUTDATED:
            infoMessage = "A newer version of Intave is available (this version is " + durationAsString + " old)";
            break;
          case INVALID:
            logger().error("Unable to boot: This version has been deactivated");
            getCommand("intave").setExecutor((commandSender, command, s, strings) -> {
              commandSender.sendMessage(prefix() + ChatColor.RED + "This version has been deactivated. Please update Intave immediately");
              return false;
            });
            performShutdown();
            return;
        }
        logger().info(infoMessage);
      }

      // resolve config hash
      configurationService.setupConfiguration(requiredConfigurationHash);

      prefix = configurationService.configuration().getString("layout.prefix", prefix);
      prefix = ChatColor.translateAlternateColorCodes('&', prefix);
      defaultColor = ChatColor.getLastColors(prefix);

      customEventService = new CustomEventService(this);
      interactionPermissionService = new InteractionPermissionService();
      checkService = new CheckService(this);
      violationService = new ViolationService(this);
      eventService = new EventService(this);
      proxyMessenger = new ProxyMessenger(this);
      sibylIntegrationService = new SibylIntegrationService(this);

      getCommand("intave").setExecutor(new CommandProcessor());

      // stage 8

      trustFactorService.setup();
      checkService.setup();
      customEventService.setup();
      eventService.setup();
    } catch (Exception exception) {
      logger.error("Unable to boot");
      exception.printStackTrace();
      getCommand("intave").setExecutor((commandSender, command, s, strings) -> {
        commandSender.sendMessage(prefix() + ChatColor.RED + "Intave couldn't boot properly");
        return false;
      });
      performShutdown();
      return;
    }
    packetSubscriptionLinker.refreshInternalSubscriptions();
    logger.info("Intave booted successfully");
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
    logger.shutdown();
    packetSubscriptionLinker.reset();
    eventLinker.performShutdown();
    logger().info("Intave offline");
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

  public BukkitEventLinker eventLinker() {
    return eventLinker;
  }

  public PacketSubscriptionLinker packetSubscriptionLinker() {
    return packetSubscriptionLinker;
  }

  public ViolationService retributionService() {
    return this.violationService;
  }

  public InteractionPermissionService interactionPermissionService() {
    return interactionPermissionService;
  }

  public SibylIntegrationService sibylIntegrationService() {
    return sibylIntegrationService;
  }

  public VersionList versionList() {
    return versionList;
  }
}