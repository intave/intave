package de.jpx3.intave;

import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.accessbackend.IntaveAccessService;
import de.jpx3.intave.adapter.ComponentLoader;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.agent.AgentAccessor;
import de.jpx3.intave.analytics.Analytics;
import de.jpx3.intave.annotate.NameIntrinsicallyImportant;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.block.access.BlockAccess;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.BlockWrapper;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.modifier.CollisionModifiers;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.shape.resolve.patch.BoundingBoxPatcher;
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
import de.jpx3.intave.connect.customclient.CustomClientSupportService;
import de.jpx3.intave.connect.proxy.ProxyMessenger;
import de.jpx3.intave.connect.sibyl.SibylBroadcast;
import de.jpx3.intave.connect.sibyl.SibylIntegrationService;
import de.jpx3.intave.connect.upload.ScheduledUploadService;
import de.jpx3.intave.diagnostic.ConsoleOutput;
import de.jpx3.intave.diagnostic.natives.NativeCheck;
import de.jpx3.intave.entity.EntityLookup;
import de.jpx3.intave.entity.size.HitboxSizeAccess;
import de.jpx3.intave.entity.type.EntityTypeDataAccessor;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.klass.locate.Locate;
import de.jpx3.intave.library.Libraries;
import de.jpx3.intave.library.asm.Frame;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.metric.Metrics;
import de.jpx3.intave.metric.ServerHealth;
import de.jpx3.intave.module.BootSegment;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.player.fake.IdentifierReserve;
import de.jpx3.intave.player.fake.event.FakePlayerEventService;
import de.jpx3.intave.reflect.access.ReflectiveAccess;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.resource.legacy.EncryptedLegacyResource;
import de.jpx3.intave.security.*;
import de.jpx3.intave.security.letis.Letis;
import de.jpx3.intave.share.link.WrapperConverter;
import de.jpx3.intave.test.TestService;
import de.jpx3.intave.trustfactor.TrustFactorService;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.ViolationStorage;
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
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.IntaveControl.*;
import static de.jpx3.intave.library.asm.ClassVisitor.LICENSE_NAME;
import static de.jpx3.intave.security.InterceptorFilterPrintStream.foundInterceptor;
import static de.jpx3.intave.user.meta.ProtocolMetadata.MARKED_FOR_PLAYER_REPORT;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VERSION_DETAILS;
import static java.nio.charset.StandardCharsets.UTF_8;

@NameIntrinsicallyImportant
public final class IntavePlugin extends JavaPlugin {
  private static IntavePlugin singletonInstance;
  private static String version = "UNKNOWN";
  private static String prefix = ChatColor.translateAlternateColorCodes('&', "&8[&c&lIntave&8]&7 ");
  private static String defaultColor = ChatColor.getLastColors(prefix);
  private static boolean offlineMode = false, successfullyBooted = false;

  static {
    // stage 1
  }

  private IntaveLogger logger;
  private ProxyMessenger proxyMessenger;
  private SibylIntegrationService sibylIntegrationService;
  private ConfigurationService configurationService;
  private ComponentLoader componentLoader;
  private FakePlayerEventService fakePlayerEventService;
  private CheckService checkService;
  private TrustFactorService trustFactorService;
  private IntaveVersionList versions;
  private CustomClientSupportService customClientSupportService;
  private IntaveAccessService accessService;
  private IntaveAccess access;
  private PlayerListService blackListService;
  private ScheduledUploadService uploadService;
  private Letis letis;
  private Analytics analytics;
  private Metrics metrics;
  private TestService testService;

  public IntavePlugin() {
    // stage 2
    stage2();
  }

  @Native
  public void stage2() {
    singletonInstance = this;
    version = getDescription().getVersion();
    createDataFolder();
    if (IntaveControl.GOMME_MODE) {
      ContextSecrets.setup();
    }
    this.logger = new IntaveLogger(this);
    this.logger.checkColorAvailability();
    Modules.prepareModules();
    Modules.proceedBoot(BootSegment.STAGE_2);
    redirectPluginLogger();
    checkClassLoaderAvailability();

    System.setProperty("org.bytedeco.javacpp.cachedir", integrityFolder().getAbsolutePath());

    Libraries.setupLibraries();
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

//    if (IntaveControl.DEBUG_SERVER_VERSION) {
//      MinecraftVersion version = MinecraftVersion.getCurrentVersion();
//      int ver = version.getMinor() * 10 + version.getBuild();
//      logger.info("[debug] Server version: " + version + " (" + ver + ")");
//    }

    // stage 4
    Modules.proceedBoot(BootSegment.STAGE_4);

    if (AgentAccessor.agentAvailable()) {
      logger.info("Using agent :{~-~}:");
    }

    IntaveDomains.setup();

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
      componentLoader.prepareComponents();
      componentLoader.loadComponents();

      configurationService = new ConfigurationService(this);
      String configurationKey = configurationService.configurationKey();

      ProtocolLibraryAdapter.checkIfOutdated();

      // check again, after ProtocolLibs availability is guaranteed
      logger.checkColorAvailability();

//      Python.setup();

      // version mambo jumbo
      // stage 5
      Modules.proceedBoot(BootSegment.STAGE_5);

      TaskTracker.setup();
      Locate.setup();

      // for some reason, we get an ILLEGAL_ACCESS_VIOLATION if we don't sleep here
      // I don't know why, I don't care why, but this approach works

      SinusCache.setup();
      ServerHealth.setup();

      Thread.sleep(5);

      Synchronizer.setup();
      PacketReaders.setup();

      Thread.sleep(5);

      SibylBroadcast.setup();
      ReflectiveAccess.setup();

      Thread.sleep(5);

      IdentifierReserve.setup();
      EntityTypeDataAccessor.setup();

      Thread.sleep(5);

      ChunkProviderServerAccess.setup();

      Thread.sleep(5);

      trustFactorService = new TrustFactorService(this);
      blackListService = new PlayerListService(this);

      Thread.sleep(5);

      // stage 6
      Modules.proceedBoot(BootSegment.STAGE_6);

      // we need to put this here
      BackgroundExecutors.start();

      // stage 7

      // causes interceptor output
      for (int i = 0; i < 1; i++) {
        URL url = new URL("https://"+IntaveDomains.primaryServiceDomain()+"/versions");
        url.getDefaultPort();
      }

      if (foundInterceptor) {
        System.exit(1);
        return;
      }

      InterceptorDetection.revert();

      EncryptedLegacyResource contextStatusResource = new EncryptedLegacyResource("context-status", false);

      String requiredState = null; // leave this be
      boolean offlineMode = false;

      // ja das muss so krebsig hier hin
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        logger().warn("This version has no license check, deal with caution.");
        logger().warn("Do not distribute this file and keep any copies of this file on your local computer.");
//        System.setProperty("java.net.serviceprovider.key", "~bypass");
        LICENSE_NAME = "~bypass";
        VERSION_DETAILS |= 0x100;
        VERSION_DETAILS |= 0x200;
        if (IntaveControl.DEBUG_GRAYLIST) {
          logger.info(blackListService.encryptedGrayKnowledgeData());
        }
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

        String response = "";

        /*
         * this is our new protection against proxy-based attacks
         */

        long nanoTime = System.nanoTime();
        String hashOfJarFile = HashAccess.hashOf(currentJavaJarFile);
        long longOne = ThreadLocalRandom.current().nextLong(0x4000000000000000L, Long.MAX_VALUE);
        long longTwo = ThreadLocalRandom.current().nextLong(0x4000000000000000L, Long.MAX_VALUE);
        String requestedId = String.valueOf(new UUID(longOne, longTwo)).replace("-", "").toUpperCase(Locale.ROOT);
        String secretKey = identificationKey > 0 ? new String(bytes) : "aaaaaaaa";
        String processString = secretKey + configurationKey + requestedId;
        // randomize the process string with a given seed
        long seed = (longOne + (hashOfJarFile.hashCode() * 1337L)) ^ nanoTime;
        Random random = new Random(seed);
        // add 64 random characters to the process string with a given seed
        for (int i = 0; i < 64; i++) {
          //noinspection StringConcatenationInLoop
          processString += String.valueOf((char) random.nextInt(0xFF));
        }
        // replace 16 characters at random index with random characters with a given seed
        for (int i = 0; i < 16; i++) {
          int index = random.nextInt(processString.length());
          processString = processString.substring(0, index) + (char) random.nextInt(0xFF) + processString.substring(index + 1);
        }
        for (int i = 0; i < processString.length(); i++) {
          char c = (char) (processString.charAt(i) + random.nextInt(0xFFFF));
          processString = processString.substring(0, i) + c + processString.substring(i + 1);
        }

        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        Charset utf8 = UTF_8;

        messageDigest.update(configurationKey.getBytes(utf8));
        messageDigest.update(requestedId.getBytes(utf8));
        messageDigest.update(String.valueOf(nanoTime).getBytes(utf8));
        messageDigest.update(hashOfJarFile.getBytes(utf8));
        messageDigest.update(processString.getBytes(utf8));
        messageDigest.update(String.valueOf(longTwo).getBytes(utf8));

        byte[] digest = messageDigest.digest();

        // randomize the "digest" bytes with a given seed
        for (int i = 0; i < digest.length; i++) {
          byte value = digest[i];
          value += random.nextInt(0xFF);
          digest[i] = value;
        }
        byte[] nanoBytes = new byte[8];
        for (int i = 0; i < 8; i++) {
          nanoBytes[i] = (byte) (nanoTime >> (i * 8));
        }

        // put the nano bytes to a human-readable string
        StringBuilder nanoBuilder = new StringBuilder();
        for (byte nanoByte : nanoBytes) {
          nanoBuilder.append(String.format("%02X", nanoByte));
        }

        try {
          String domain = IntaveDomains.primaryServiceDomain();
          if (!domain.contains("intave") || !domain.contains("service")) {
            throw new Exception("Invalid domain");
          }
          String path = "https://" + domain + "/auth.php";
          URL url = new URL(path);
          URLConnection connection = url.openConnection();
          connection.setUseCaches(false);
          connection.setDefaultUseCaches(false);
          connection.addRequestProperty("User-Agent", "Intave/" + version());
          connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
          connection.addRequestProperty("Pragma", "no-cache");
          connection.addRequestProperty("A", hashOfJarFile);
          connection.addRequestProperty("B", secretKey);
          connection.addRequestProperty("C", HWIDVerification.publicHardwareIdentifier());
          connection.addRequestProperty("D", configurationKey);
          connection.addRequestProperty("E", LicenseAccess.rawLicense());
          connection.addRequestProperty("F", "X9-" + requestedId + "-" + nanoBuilder.toString().toUpperCase(Locale.ROOT));
          connection.addRequestProperty("G", blackListService.encryptedGrayKnowledgeData());
          connection.addRequestProperty("H", blackListService.encryptedBlueKnowledgeData());
          connection.setConnectTimeout(2000);
          connection.setReadTimeout(2000);
          connection.connect();

          if (IntaveControl.AUTHENTICATION_DEBUG_MODE) {
            System.out.println("Requesting authentication from " + path);
          }

          Scanner scanner2 = new Scanner(connection.getInputStream(), "UTF-8");
          StringBuilder raw2 = new StringBuilder();
          while (scanner2.hasNext())
            raw2.append(scanner2.next());
          response = raw2.toString();
          if (IntaveControl.AUTHENTICATION_DEBUG_MODE) {
            System.out.println("Response: " + response);
          }
          if ("timeout".equalsIgnoreCase(response)) {
            response += "_";
          }
        } catch (IOException exception) {
          response = "timeout";
        }
        String message = "";
        boolean bad = false;
        boolean clearReloCache = false;
        // VMProtect doesn't like JNICs switch-equivalent :(
        //noinspection IfCanBeSwitch
        if ("banned".equals(response) || "invalid".equals(response) || "error".equals(response)) {
          message = "Unable to boot: Authentication failed";
          bad = true;
          clearReloCache = true;
        } else if ("hwid".equals(response) || "hwidr".equals(response)) {
          message = "Unable to boot: Hardware identification failed - verify this machine on your dashboard";
          bad = true;
        } else if ("rate".equals(response)) {
          message = "Unable to boot: Too many pending hardware requests, please contact support";
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
          contextStatusResource.write(new ByteArrayInputStream(("failure-" + response).getBytes(UTF_8)));
          bootFailure(message);
          performShutdown();
          return;
        }
        if ("timeout".equals(response)) {
          LICENSE_NAME = "~timeout";
//          System.setProperty("java.net.serviceprovider.key", "~timeout");
          offlineMode = true;
          requiredState = null;
        } else {
          // Intavede#key1=value1#key2=value2 ...
          String[] split;
          if (response.contains("#")) {
            split = response.split("#");
            LICENSE_NAME = split[0];
          } else {
            split = new String[]{response};
            LICENSE_NAME = response;
          }
//          System.setProperty("java.net.serviceprovider.key", licenseName);
          Map<String, String> properties = new HashMap<>();
          boolean first = true;
          for (String propertyPair : split) {
            if (first) {
              first = false;
              continue;
            }
            String[] split1 = propertyPair.split("=");
            try {
              properties.put(split1[0], split1[1]);
            } catch (Exception e) {
              System.out.println("Unable to parse property pair: " + propertyPair);
              e.printStackTrace();
            }
          }
          if (properties.isEmpty()) {
            logger.error("Invalid server response " + response);
            contextStatusResource.write(new ByteArrayInputStream(("failure-" + response).getBytes(UTF_8)));
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
          if (properties.containsKey("classloader-update")) {
            // delete classloader library
            String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String filePath;
            String suffix = operatingSystem.contains("win") ? ".dll" : ".so";
            if (operatingSystem.contains("win")) {
              filePath = System.getenv("APPDATA") + "/Intave/";
            } else {
              filePath = System.getProperty("user.home") + "/.intave/";
            }
            File deleteFile = new File(filePath, "classloader.X" + suffix + ".delete");
            deleteFile.createNewFile();
          }
          // fake, used to note all suspicious licenses
          if (properties.containsKey("prefer-ipv4-stack")) {
            MARKED_FOR_PLAYER_REPORT |= 128;
          }
          if (properties.containsKey("debug-output")) {
            String debugOutput = properties.get("debug-output");
            if (debugOutput != null) {
              logger.info("Debug output: " + debugOutput);
            }
          }
          String keyResponse = properties.get("exchange-key");
          // verify the server integrity
          boolean validInputConfirmation = false;
          if (keyResponse != null) {
            byte[] responseBytes = new byte[keyResponse.length() / 2];
            for (int i = 0; i < responseBytes.length; i++) {
              responseBytes[i] = (byte) Integer.parseInt(keyResponse.substring(i * 2, i * 2 + 2), 16);
            }
            if (responseBytes == digest) {
              validInputConfirmation = true;
            } else {
              int length = responseBytes.length;
              if (digest.length == length) {
                validInputConfirmation = length > 1;
                for (int i = 0; i < length; i++) {
                  if (responseBytes[i] != digest[i]) {
                    validInputConfirmation = false;
                    break;
                  }
                }
              }
            }
          }

          boolean validOutputConfirmation = false;
          String macResponse = properties.get("mac");
          if (macResponse != null) {}
          // believe
          validOutputConfirmation = true;

          if (!validInputConfirmation || !validOutputConfirmation) {
            logger.error("Unable to boot: Authentication response not trustworthy");
            contextStatusResource.write(new ByteArrayInputStream(("failure-" + response).getBytes(UTF_8)));
            bootFailure("Internal failure");
            performShutdown();
            return;
          }
        }
      }

      boolean partner = (VERSION_DETAILS & 0x100) != 0;
      boolean enterprise = (VERSION_DETAILS & 0x200) != 0;

//      if (partner || enterprise) {
//        logger.info("Identity confirmed");
//      }

      if (offlineMode) {
        // check last online
        boolean allowLeniency = IntaveControl.DISABLE_LICENSE_CHECK;
        //noinspection ConstantConditions
        if (!allowLeniency && contextStatusResource.exists()) {
          String textString = contextStatusResource.readAsString();
          if (textString.startsWith("success")) {
            Long lastSuccessfulStart = null;
            try {
              lastSuccessfulStart = Long.valueOf(textString.split("/")[1]);
            } catch (Exception ignored) {
            }
            if (lastSuccessfulStart != null) {
              try {
                String url_path = "https://raw.githubusercontent.com/intave/status/main/availability";
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
                if (System.currentTimeMillis() - lastSuccessfulStart <= TimeUnit.DAYS.toMillis(3)) {
                  try {
                    connection.connect();
                    allowLeniency = true;
                  } catch (Exception ignored) {}
                } else {
                  // perform GitHub check
                  Scanner scanner2 = new Scanner(connection.getInputStream(), "UTF-8");
                  Map<String, String> availabilities = new HashMap<>();
                  while (scanner2.hasNextLine()) {
                    String line = scanner2.nextLine();
                    String[] split = line.split("=");
                    availabilities.put(split[0], split[1]);
                  }
                  allowLeniency = "false".equalsIgnoreCase(availabilities.get("intave.de"));
                }
              } catch (Exception ignored) {
              }
            }
          }
        }

        if (allowLeniency && !configurationService.loader().configurationCacheExists()) {
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
            String textString = contextStatusResource.readAsString();
            if (textString.startsWith("success")) {
              try {
                long lastSuccessfulStart = Long.parseLong(textString.split("/")[1]);
                if (System.currentTimeMillis() - lastSuccessfulStart < TimeUnit.DAYS.toMillis(2)) {
                  writeSuccessLog = false;
                }
              } catch (Exception ignored) {}
            }
          }
        } catch (Exception ignored) {
        }
        if (writeSuccessLog) {
          contextStatusResource.write(new ByteArrayInputStream(("success/" + System.currentTimeMillis()).getBytes(UTF_8)));
        }
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
      BoundingBoxPatcher.setup();
      EntityLookup.setup();

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
      FaultKicks.applyFrom(configurationService.configuration().getConfigurationSection("fault-kicks"));
      ConsoleOutput.applyFrom(configurationService.configuration().getConfigurationSection("logging"));

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
      letis = new Letis(this);

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
      analytics.setup();
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
    BackgroundExecutors.executeWhenever(this::clearIntegrityGarbage);
    BackgroundExecutors.executeWhenever(this::clearSaveFolderGarbage);
    BackgroundExecutors.executeWhenever(this::clearUnusedSamples);
    logger.performCompression();

    ViolationStorage.setup();

    if (JavaVersion.current() < 12) {
//      logger.warn(ChatColor.RED + "Upgrading Java has incredible performance benefits");
//      logger.warn(ChatColor.RED + "We strongly recommend updating Java now");
//      logger.warn(ChatColor.RED + "Support for older versions of Java might eventually be dropped");
      logger.info(ChatColor.RED + "Your version of Java is outdated, consider updating");
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

    preventIncorrectStates();
    registerNativeCheck();
    Modules.linker().packetEvents().refreshLinkages();
    displayVersionInformation();
    successfullyBooted = true;
    randomExitMessages = Resources.localServiceCacheResource("exitmessages","exitmessages", TimeUnit.DAYS.toMillis(7)).readLines();
    logger.info("Intave booted successfully");

    Synchronizer.synchronize(() -> {
      // stage 11
      Modules.proceedBoot(BootSegment.STAGE_11);

      StartupTasks.runAll();

      // perform a complete native self-check
      BackgroundExecutors.execute(NativeCheck::run);
    });
  }

  private void preventIncorrectStates() {
    // can - for some reason - not be native
    Map<String, Boolean> enforceDisabled = new HashMap<>();
    enforceDisabled.put("Debug SK is enabled", SIBYL_ALLOW_ALL);
    enforceDisabled.put("Heuristic debugging is enabled", DEBUG_HEURISTICS);
    enforceDisabled.put("Movement debugging is enabled", DEBUG_MOVEMENT);
    enforceDisabled.put("Interaction debugging is enabled", DEBUG_INTERACTION);
    enforceDisabled.put("CM debugging is enabled", DEBUG_CMS);
    enforceDisabled.put("Red trustfactor is enabled globally", APPLY_GLOBAL_LOW_TRUSTFACTOR);
    enforceDisabled.put("Block-placements are deactivated", DISALLOW_ALL_BLOCK_PLACEMENTS);
    enforceDisabled.put("Hitboxes are dumped on right-click", DUMP_BLOCK_HITBOX_ON_RIGHT_CLICK);

    for (Map.Entry<String, Boolean> entry : enforceDisabled.entrySet()) {
      if (entry.getValue()) {
        logger.warn(entry.getKey());
        if (!DISABLE_LICENSE_CHECK /*|| GOMME_MODE*/) {
          throw new IllegalStateException(entry.getKey() + ", but license check is disabled");
        }
      }
    }
  }

  private void registerNativeCheck() {
    NativeCheck.registerNative(this::invalidateCaches);
    NativeCheck.registerNative(() -> bootFailure(null));
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
    if (!IntaveControl.DISABLE_LICENSE_CHECK) {
      return;
    }
    if (de.jpx3.classloader.ClassLoader.usesNativeAccess() && !de.jpx3.classloader.ClassLoader.loaded()) {
      try {
        de.jpx3.classloader.ClassLoader.setupEnvironment(Files.createTempDirectory("intave-debug").toFile());
      } catch (IOException exception) {
        logger.error("[Intave] Failed to create temporary directory for classloader");
        exception.printStackTrace();
      }
    }
  }

  @Native
  public void displayVersionInformation() {
    IntaveVersion version = versions.versionInformation(version());
    if (version == null) {
      logger().info(ChatColor.YELLOW + "This version of Intave is not listed in the official version index");
    } else {
      long duration = System.currentTimeMillis() - version.release();
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

  public static final long INTEGRITY_ERASE_BUFFER = TimeUnit.MINUTES.toMillis(1);

  @Native
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

  @Native
  public void clearUnusedSamples() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/Samples";
    } else {
      if (GOMME_MODE) {
        filePath = ContextSecrets.secret("cache-directory") + "/samples";
      } else {
        filePath = System.getProperty("user.home") + "/.intave/samples";
      }
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
        .filter(file -> (System.currentTimeMillis() - file.lastModified()) > 3 * 24 * 60 * 60)
        .forEach(file -> {
          try {
            file.delete();
          } catch (Exception exception) {
            exception.printStackTrace();
          }
        });
      // clear empty directories
      Files.walk(workDirectory.toPath())
        .filter(Files::isDirectory)
        .map(Path::toFile)
        .filter(File::canWrite)
        .filter(File::canRead)
        .filter(file -> file.listFiles() == null)
        .filter(file -> (System.currentTimeMillis() - file.lastModified()) > 3 * 24 * 60 * 60)
        .forEach(file -> {
          try {
            file.delete();
          } catch (Exception exception) {
            exception.printStackTrace();
          }
        });
    } catch (NoSuchFileException ignored) {
      // ignore
    } catch (Exception | Error throwable) {
      throwable.printStackTrace();
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
            exception.printStackTrace();
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
            exception.printStackTrace();
          }
        });
    } catch (NoSuchFileException ignored) {
      // ignore
    } catch (Exception | Error throwable) {
      throwable.printStackTrace();
    }
  }

  @Native
  public void invalidateCaches() {
    if (NativeCheck.checkActive()) {
      return;
    }
    if (configurationService != null) {
      configurationService.deleteCache();
    }
    clearIntegrityGarbage();
  }

  @Native
  public void bootFailure(String reason) {
    if (NativeCheck.checkActive()) {
      return;
    }
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
    } catch (Exception ignored) {}
  }

  private File integrityFolder() {
    try {
      // mark caches as deletable
      Class<?> relocator = Class.forName("de.jpx3.relocator.Relocator");
      File child = (File) relocator.getMethod("h").invoke(null, "a", "b");
      return child.getParentFile();
    } catch (Exception ignored) {}
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

  public ProxyMessenger proxy() {
    return proxyMessenger;
  }

  public CheckService checks() {
    return checkService;
  }

  public YamlConfiguration settings() {
    return configurationService.configuration();
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