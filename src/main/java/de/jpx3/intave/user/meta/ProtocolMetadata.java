package de.jpx3.intave.user.meta;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

@Relocate
public final class ProtocolMetadata {
  // final has been removed to disguise modified integer VERSION_DETAILS
  public static int VER_1_17 = 755; // 1.17
  public static int VER_1_16 = 735; // 1.16
  public static int VER_1_15 = 573; // 1.15
  public static int VER_1_14 = 477; // 1.14
  public static int VER_1_13_2 = 404; // 1.13.2
  public static int VER_1_13 = 393; // 1.13
  public static int VER_1_12 = 335; // 1.12
  public static int VER_1_11_1 = 316;
  public static int VER_1_11 = 315;
  public static int VER_1_10 = 210;
  public static int VER_1_9 = 107; // 1.9
  public static int VERSION_DETAILS = 97; // secret integer for security - DO NOT MODIFY
  public static int VER_1_8 = 47; // 1.8

  public static int VER_INVALID = 1000;

  private String versionString;
  private String clientBrand = "Unknown";
  private int protocolVersion;
  private final User user;
  private int refreshes;

  public ProtocolMetadata(Player player, User user) {
    this.user = user;
    this.refresh(player);
  }

  public void refresh(Player player) {
    this.protocolVersion = player == null ? -1 : ViaVersionAdapter.protocolVersionOf(player);
    this.versionString = versionAsString();
    this.refreshes++;

    if (protocolVersion == -1 || protocolVersion == 0) {
      protocolVersion = VER_INVALID;
    }
  }

  private String versionAsString() {
    // ViaVersion's special cases
    if (protocolVersion == -1)
      return "Unresolved";
    if (protocolVersion == 0)
      return "Unknown";

    if (protocolVersion < 0)
      return "1.0";
    if (protocolVersion <= 47)
      return "1.8.8";
    if (protocolVersion <= 107)
      return "1.9";
    if (protocolVersion <= 108)
      return "1.9.1";
    if (protocolVersion <= 109)
      return "1.9.2";
    if (protocolVersion <= 110)
      return "1.9.3";
    if (protocolVersion <= 210)
      return "1.10.1";
    if (protocolVersion <= 315)
      return "1.11";
    if (protocolVersion <= 316)
      return "1.11.1";
    if (protocolVersion <= 335)
      return "1.12";
    if (protocolVersion <= 338)
      return "1.12.1";
    if (protocolVersion <= 340)
      return "1.12.2";
    if (protocolVersion <= 393)
      return "1.13";
    if (protocolVersion <= 401)
      return "1.13.1";
    if (protocolVersion <= 404)
      return "1.13.2";
    if (protocolVersion <= 477)
      return "1.14";
    if (protocolVersion <= 480)
      return "1.14.1";
    if (protocolVersion <= 485)
      return "1.14.2";
    if (protocolVersion <= 490)
      return "1.14.3";
    if (protocolVersion <= 498)
      return "1.14.4";
    if (protocolVersion <= 573)
      return "1.15";
    if (protocolVersion <= 575)
      return "1.15.1";
    if (protocolVersion <= 578)
      return "1.15.2";
    if (protocolVersion <= 735)
      return "1.16";
    if (protocolVersion <= 736)
      return "1.16.1";
    if (protocolVersion <= 751)
      return "1.16.2";
    if (protocolVersion <= 753)
      return "1.16.3";
    if (protocolVersion <= 754)
      return "1.16.5";
    if (protocolVersion <= 755)
      return "1.17";
    if (protocolVersion <= 756)
      return "1.17.1";
    return "1.17.1";
  }

  public int protocolVersion() {
    return protocolVersion;
  }

  public boolean legacyTeleportAccept() {
    return protocolVersion <= VER_1_8;
  }

  public float cameraSneakOffset() {
    boolean override = user.customClientSupport().isLegacySneakHeight();
    if (protocolVersion >= VER_1_13_2 && !override) {
      return 0.35f;
    } else {
      return 0.08f;
    }
  }

  @Deprecated
  public float hitBoxHeightWhenSneaking() {
    if (protocolVersion >= VER_1_13_2) {
      return 1.5F;
    } else if (protocolVersion >= VER_1_9) {
      return 1.65F;
    }
    return 1.8F;
  }

  public String clientBrand() {
    return clientBrand;
  }

  public void setClientBrand(String clientBrand) {
    this.clientBrand = clientBrand;
  }

  public boolean flyingPacketStream() {
    return protocolVersion <= VER_1_8 && !clientVersionOlderThanServerVersion();
  }

  public boolean supportsInventoryAchievementPacket() {
    return protocolVersion <= VER_1_11_1 && !clientVersionOlderThanServerVersion();
  }

  public boolean applyModernCollider() {
    return protocolVersion >= VER_1_14;
  }

  public boolean swimmingMechanics() {
    return protocolVersion >= VER_1_13;
  }

  public boolean canUseElytra() {
    return protocolVersion >= VER_1_9;
  }

  public boolean affectedByLevitation() {
    return protocolVersion >= VER_1_12;
  }

  public boolean roundEnvironmentNumbers() {
    return protocolVersion < VER_1_14;
  }

  public boolean sprintWhenSneaking() {
    return protocolVersion >= VER_1_14;
  }

  public boolean sprintWhenHandActive() {
    return protocolVersion >= VER_1_9;
  }

  public boolean delayedSneak() {
    return protocolVersion >= VER_1_15;
  }

  public boolean alternativeSneak() {
    return protocolVersion < VER_1_15 && protocolVersion >= VER_1_14;
  }

  public boolean motionResetOnCollision() {
    return protocolVersion < VER_1_14;
  }

  public boolean cavesAndCliffsUpdate() {
    return protocolVersion >= VER_1_17;
  }

  public boolean beeUpdate() {
    return protocolVersion >= VER_1_15;
  }

  public boolean waterUpdate() {
    return protocolVersion >= VER_1_13;
  }

  public boolean combatUpdate() {
    return protocolVersion >= VER_1_9;
  }

  private Boolean behind;

  public boolean clientVersionOlderThanServerVersion() {
    if (behind == null || refreshes < 2) {
      MinecraftVersion server = MinecraftVersion.getCurrentVersion();
      MinecraftVersion client = new MinecraftVersion(versionAsString());
      behind = !client.isAtLeast(server);
    }
    return behind;
  }

  public String versionString() {
    return versionString;
  }
}