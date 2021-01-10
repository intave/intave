package de.jpx3.intave;

import de.jpx3.intave.adapter.ComponentLoader;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.config.ConfigurationService;
import de.jpx3.intave.detect.CheckService;
import de.jpx3.intave.event.EventService;
import de.jpx3.intave.event.bukkit.BukkitEventLinker;
import de.jpx3.intave.event.packet.PacketSubscriptionLinker;
import de.jpx3.intave.event.service.RetributionService;
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
import org.bukkit.plugin.java.JavaPlugin;

public final class IntavePlugin extends JavaPlugin {
  private static IntavePlugin singletonInstance;
  private static String version = "UNKNOWN";

  private IntaveLogger logger;
  private ConfigurationService configurationService;
  private ComponentLoader componentLoader;
  private BukkitEventLinker eventLinker;
  private PacketSubscriptionLinker packetSubscriptionLinker;
  private EventService eventService;
  private RetributionService retributionService;
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

    componentLoader = new ComponentLoader(this);
    componentLoader.loadComponents();

    trustFactorService = new TrustFactorService(this);
    trustFactorService.setup();

    // version mambo jumbo

    // stage 5

    packetSubscriptionLinker = new PacketSubscriptionLinker(this);

    // stage 6

//    BlockBoxResolver.setup();

    ProtocolLibAdapter.checkIfOutdated();

    Raytracer.setup();
    SinusCache.setup();
    Synchronizer.setup();
    BlockAccessor.setup();
    ViaVersionAdapter.setup();
    InventoryUseItemHelper.setup();
    BoundingBoxPatcher.setup();

    try {
      // stage 7
      configurationService = new ConfigurationService(this);
      String configurationKey = configurationService.configurationKey();

      // license check call


      // resolve config hash

      String requiredConfigurationHash = "server response";
      configurationService.setupConfiguration(requiredConfigurationHash);

      interactionPermissionService = new InteractionPermissionService();
      checkService = new CheckService(this);
      retributionService = new RetributionService();
      eventService = new EventService(this);

      // stage 8

      checkService.setup();
      eventService.setup();
    } catch (Exception exception) {
      logger.error("Unable to boot");
      exception.printStackTrace();
    }
    packetSubscriptionLinker.refreshInternalSubscriptions();
    logger.info("Intave booted successfully");
  }

  @Natify
  @Override
  public void onDisable() {
    logger.shutdown();
    packetSubscriptionLinker.reset();
    eventLinker.performShutdown();
  }

  public IntaveLogger logger() {
    return logger;
  }

  public CheckService checkService() {
    return checkService;
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

  public RetributionService retributionService() {
    return this.retributionService;
  }

  public InteractionPermissionService interactionPermissionService() {
    return interactionPermissionService;
  }

  public static String version() {
    return version;
  }

  public static IntavePlugin singletonInstance() {
    return singletonInstance;
  }
}