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
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.annotate.Natify;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.trustfactor.TrustFactorService;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.collision.patches.BoundingBoxPatcher;
import de.jpx3.intave.world.permission.InteractionPermissionService;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class IntavePlugin extends JavaPlugin {
  private static IntavePlugin singletonInstance;
  private static String version = "UNKNOWN";
  private static String prefix = "§8[§c§lIntave§8]§7 ";
  private static String defaultColor = "";

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

  static {
    // stage 1


 }

  public IntavePlugin() {
    // stage 2
    singletonInstance = this;
    version = getDescription().getVersion();
    this.logger = new IntaveLogger(this);
  }

  @Natify
  @Override
  public void onLoad() {
    // stage 3

    // event links must be available throughout the onEnable call
    eventLinker = new BukkitEventLinker(this);

  }

  @Natify
  @Override
  public void onEnable() {
    logger.info("Please stand by..");
    // stage 4

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


      // resolve config hash

      String requiredConfigurationHash = "server response";
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
        commandSender.sendMessage(prefix() + ChatColor.RED + "Intave wasn't properly loaded");
        return false;
      });
      performShutdown();
      return;
    }
    packetSubscriptionLinker.refreshInternalSubscriptions();
    logger.info("Intave booted successfully");
  }

  @Natify
  @Override
  public void onDisable() {
    performShutdown();
  }

  @Natify
  public void performShutdown() {
    BackgroundExecutor.stopBlocking();

    logger.shutdown();
    packetSubscriptionLinker.reset();
    eventLinker.performShutdown();
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

  public static String version() {
    return version;
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