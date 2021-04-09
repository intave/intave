package de.jpx3.intave.user;

import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.tools.annotate.Relocate;
import org.bukkit.entity.Player;

@Relocate
public final class UserMetaClientData {
  // final has been removed to disguise modified integer VERSION_DETAILS
  public static int PROTOCOL_VERSION_NETHER_UPDATE = 735; // 1.16
  public static int PROTOCOL_VERSION_BEE_UPDATE = 573; // 1.15
  public static int PROTOCOL_VERSION_VILLAGE_UPDATE = 477; // 1.14
  public static int SOMETHING_BETWEEN = 404; // 1.13.2
  public static int PROTOCOL_VERSION_AQUATIC_UPDATE = 393; // 1.13
  public static int PROTOCOL_VERSION_COLOR_UPDATE = 335; // 1.12
  public static int PROTOCOL_VERSION_COMBAT_UPDATE = 107; // 1.9
  public static int VERSION_DETAILS = 97; // secret integer for security - DO NOT MODIFY
  public static int PROTOCOL_VERSION_BOUNTIFUL_UPDATE = 47; // 1.8
  private final int protocolVersion;

  public UserMetaClientData(Player player) {
    this.protocolVersion = player == null ? -1 : ViaVersionAdapter.protocolVersionOf(player);
  }

  public int protocolVersion() {
    return protocolVersion;
  }

  public float cameraSneakOffset() {
    return protocolVersion >= SOMETHING_BETWEEN ? 0.35f : 0.08f;
  }

  public float hitBoxHeightWhenSneaking() {
    if (protocolVersion >= SOMETHING_BETWEEN) {
      return 1.5F;
    } else if(protocolVersion >= PROTOCOL_VERSION_COMBAT_UPDATE) {
      return 1.65F;
    }
    return 1.8F;
  }

  public boolean flyingPacketStream() {
    return protocolVersion <= PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
  }

  public boolean inventoryAchievementPacket() {
    return protocolVersion <= PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
  }

  public boolean applyNewEntityCollisions() {
    // >= 1.14
    return protocolVersion >= PROTOCOL_VERSION_VILLAGE_UPDATE;
  }

  public boolean roundEnvironmentNumbers() {
    // < 1.14
    return protocolVersion < PROTOCOL_VERSION_VILLAGE_UPDATE;
  }

  public boolean sprintWhenSneaking() {
    // >= 1.14
    return protocolVersion >= PROTOCOL_VERSION_VILLAGE_UPDATE;
  }

  public boolean sprintWhenHandActive() {
    // >= 1.9
    return protocolVersion >= PROTOCOL_VERSION_COMBAT_UPDATE;
  }

  public boolean delayedSneak() {
    // 1.15
    return protocolVersion >= PROTOCOL_VERSION_BEE_UPDATE;
  }

  public boolean alternativeSneak() {
    // < 1.15 && >= 1.14
    return protocolVersion < PROTOCOL_VERSION_BEE_UPDATE && protocolVersion >= PROTOCOL_VERSION_VILLAGE_UPDATE;
  }

  public boolean motionResetOnCollision() {
    // 1.14
    return protocolVersion < PROTOCOL_VERSION_VILLAGE_UPDATE;
  }

  public boolean waterUpdate() {
    // >= 1.13
    return protocolVersion >= PROTOCOL_VERSION_AQUATIC_UPDATE;
  }

  public boolean combatUpdate() {
    return protocolVersion >= PROTOCOL_VERSION_COMBAT_UPDATE;
  }

  public String versionAsString() {
    if (protocolVersion <= 47)
      return "1.8.x";
    if (protocolVersion <= 107)
      return "1.9.0";
    if (protocolVersion <= 108)
      return "1.9.1";
    if (protocolVersion <= 109)
      return "1.9.2";
    if (protocolVersion <= 110)
      return "1.9.3";
    if (protocolVersion <= 210)
      return "1.10.x";
    if (protocolVersion <= 315)
      return "1.11.0";
    if (protocolVersion <= 316)
      return "1.11.1/2";
    if (protocolVersion <= 335)
      return "1.12.0";
    if (protocolVersion <= 338)
      return "1.12.1";
    if (protocolVersion <= 340)
      return "1.12.2";
    if (protocolVersion <= 393)
      return "1.13.0";
    if (protocolVersion <= 401)
      return "1.13.1";
    if (protocolVersion <= 404)
      return "1.13.2";
    if (protocolVersion <= 477)
      return "1.14.0";
    if (protocolVersion <= 480)
      return "1.14.1";
    if (protocolVersion <= 485)
      return "1.14.2";
    if (protocolVersion <= 490)
      return "1.14.3";
    if (protocolVersion <= 498)
      return "1.14.4";
    if (protocolVersion <= 573)
      return "1.15.0";
    if (protocolVersion <= 575)
      return "1.15.1";
    if (protocolVersion <= 578)
      return "1.15.2";
    if (protocolVersion <= 735)
      return "1.16.0";
    if (protocolVersion <= 736)
      return "1.16.1";
    if (protocolVersion <= 751)
      return "1.16.2";
    if (protocolVersion <= 753)
      return "1.16.3";
    if (protocolVersion <= 754)
      return "1.16.4/5";

    return "NA";
  }
}