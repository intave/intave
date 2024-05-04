package de.jpx3.intave.check.world;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.world.placementanalysis.*;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.BlockPlaceEvent;

import static de.jpx3.intave.IntaveControl.DISABLE_LICENSE_CHECK;
import static de.jpx3.intave.IntaveControl.GOMME_MODE;

@DoNotFlowObfuscate
public final class PlacementAnalysis extends Check {
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;
  public static final String COMMON_FLAG_MESSAGE = "suspicious block-placement";

  public PlacementAnalysis(IntavePlugin plugin) {
    super("PlacementAnalysis", "placementanalysis");
    this.plugin = plugin;
    this.setupSubChecks();
    decrementer = new CheckViolationLevelDecrementer(this, 0.15);
  }

  @Native
  public void setupSubChecks() {
    boolean useTimings = configuration().settings().boolBy("check-timings", configuration().settings().boolBy("check_timings", true));
    if (DISABLE_LICENSE_CHECK && !GOMME_MODE) {
      appendCheckPart(new Constraint(this));
      appendCheckPart(new SmartSpeed(this));
    }
//    boolean enterprise = (ProtocolMetadata.VERSION_DETAILS & 0x200) != 0;
//    boolean partner = (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;
    try {
//      if (enterprise || partner || DISABLE_LICENSE_CHECK) {
        if (useTimings) {
          appendCheckPart(new Speed(this));
          appendCheckPart(new Sneak(this));
        }
        appendCheckPart(new Snap(this));
        appendCheckPart(new SharpRotation(this));
        appendCheckPart(new BlockRotation(this));
        // appendCheckPart(new SneakAndPlace(this));
//      }
    } catch (Exception | Error e) {
      // classes might be missing
    }
    appendCheckPart(new RotationSpeed(this));
//    appendCheckPart(new PacketOrder(this));
    appendCheckPart(new Facing(this));
    appendCheckPart(new RoundedRotation(this));

    appendPlayerCheckPart(AngleSnap.class);
    appendPlayerCheckPart(RotationFlick.class);
  }

  @BukkitEventSubscription
  public void on(BlockPlaceEvent place) {
    User user = userOf(place.getPlayer());
    decrementer.decrement(user, 0.1);
  }

  private static Boolean legacyConfigLayCache = null;

  public static boolean legacyConfigurationLayout() {
    if (legacyConfigLayCache != null) {
      return legacyConfigLayCache;
    }
    YamlConfiguration settings = IntavePlugin.singletonInstance().settings();
    ConfigurationSection section = settings.getConfigurationSection("check.placementanalysis.cloud-thresholds.on-premise");
    if (section != null) {
      IntaveLogger.logger().info("Using new placementanalysis format");
      return legacyConfigLayCache = false;
    } else {
      IntaveLogger.logger().info("Still using old placementanalysis config format");
      return legacyConfigLayCache = true;
    }
  }

  public void applyPlacementAnalysisDamageCancel(User user, String checkId) {
//    user.nerf(AttackNerfStrategy.CANCEL_FIRST_HIT, checkId);
//    user.nerf(AttackNerfStrategy.DMG_LIGHT, checkId);
  }
}