/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.user.meta;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.entity.position.RotationTarget;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.version.ProtocolVersionConverter;
import org.bukkit.entity.Player;

import java.util.*;

import static de.jpx3.intave.entity.position.RotationTarget.*;

public final class ProtocolMetadata {
  public static int VER_26_2 = 776; // 26.2
  public static int VER_26_1_1 = 775; // 26.1 - 26.1.1
  public static int VER_1_21_11 = 774; // 1.21.11
  public static int VER_1_21_9 = 773; // 1.21.9 - 1.21.10
  public static int VER_1_21_7 = 772; // 1.21.7 - 1.21.8
  public static int VER_1_21_6 = 771; // 1.21.6
  public static int VER_1_21_5 = 770; // 1.21.5
  public static int VER_1_21_4 = 769; // 1.21.4
  public static int VER_1_21_2 = 768; // 1.21.2 - 1.21.3
  public static int VER_1_21 = 767; // 1.21 - 1.21.1
  public static int VER_1_20_5 = 766; // 1.20.5 - 1.20.6
  public static int VER_1_20_3 = 765; // 1.20.3 - 1.20.4
  public static int VER_1_20_2 = 764; // 1.20.2
  public static int VER_1_20_1 = 763; // 1.20 - 1.20.1
  public static int VER_1_19_4 = 762; // 1.19.4
  public static int VER_1_19_3 = 761; // 1.19.3
  public static int VER_1_19_2 = 760; // 1.19.1 - 1.19.2
  public static int VER_1_18_2 = 758; // 1.18.2
  public static int VER_1_17 = 755; // 1.17
  public static int VER_1_16 = 735; // 1.16
  public static int VER_1_15 = 573; // 1.15
  public static int VER_1_14_4 = 498; // 1.14.4
  public static int VER_1_14 = 477; // 1.14
  public static int VER_1_13_2 = 404; // 1.13.2
  public static int VER_1_13 = 393; // 1.13
  public static int VER_1_12 = 335; // 1.12
  public static int VER_1_11_1 = 316; // 1.11.1 - 1.11.2
  public static int VER_1_11 = 315; // 1.11
  public static int VER_1_10 = 210; // 1.10
  public static int VER_1_9 = 107; // 1.9
  public static int VERSION_DETAILS = 97; // secret integer for security - DO NOT MODIFY
  public static int MARKED_FOR_PLAYER_REPORT = 78; // secret integer for security - DO NOT MODIFY
  public static int VER_1_8 = 47; // 1.8

  public static int VER_INVALID = 1000;

  private MinecraftVersion minecraftVersion;
  private String versionString;
  private String clientBrand = "Unknown";
  private String locale = "en_US";
  private int protocolVersion;
  private final User user;
  private int refreshes;

  public Set<UUID> shownPlayers = new HashSet<>();
  public final Map<String, String> debugStates = new HashMap<>();
  public Position lastEntityPosition;
  public int lastEntityId;

  public ProtocolMetadata(Player player, User user) {
    this.user = user;
    this.refresh(player);
  }

  public ProtocolMetadata(User user, int protocolVersion) {
    this.user = user;
    this.setProtocolVersion(protocolVersion);
  }

  public void refresh(Player player) {
    setProtocolVersion(player == null ? -1 : ViaVersionAdapter.protocolVersionOf(player));
    this.refreshes++;
  }

  private String versionAsString(int protocolVersion) {
    return ProtocolVersionConverter.versionByProtocolVersion(protocolVersion);
  }

  public int protocolVersion() {
    return protocolVersion;
  }

  public void setProtocolVersion(int protocolVersion) {
    String versionString = versionAsString(protocolVersion);
    if (protocolVersion <= 0) {
      protocolVersion = VER_INVALID;
      minecraftVersion = MinecraftVersions.VER1_19_1;
    } else {
      minecraftVersion = new MinecraftVersion(versionString);
      MinecraftVersion server = MinecraftVersion.current();
      MinecraftVersion client = new MinecraftVersion(versionString);
      behind = !client.isAtLeast(server);
    }
    this.protocolVersion = protocolVersion;
    this.versionString = versionString;
  }

  public boolean legacyTeleportAccept() {
    return protocolVersion <= VER_1_8;
  }

  public float cameraSneakOffset() {
    boolean legacySneakHeight = user.customClientSupport().isLegacySneakHeight();
    if (protocolVersion >= VER_1_13_2 && !legacySneakHeight) {
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

  public boolean emptyFlyingPacketsAreExplicitlySent() {
    // flying packets are guaranteed in 1.8 and below, removed in 1.9
    // but if the server is 1.9+, via version/backwards will drop them even for 1.8 clients
    return protocolVersion <= VER_1_8 && !MinecraftVersions.VER1_9_0.atOrAbove();
  }

  public boolean supportsInventoryAchievementPacket() {
    return protocolVersion <= VER_1_11_1 && !outdatedClient();
  }

  public boolean applyModernCollider() {
    return protocolVersion >= VER_1_14;
  }

  public boolean swimmingMechanics() {
    return protocolVersion >= VER_1_13;
  }

  public double fluidOnEyesOffset() {
    return protocolVersion >= VER_1_21 ? 0.0D : (double) 0.11111111F;
  }

  public boolean fluidSurfaceIncludesEyes() {
    return protocolVersion >= VER_26_1_1;
  }

  public boolean fluidHeightBasedLavaMovement() {
    return protocolVersion >= VER_1_16;
  }

  public boolean stagesEyeFluidState() {
    return protocolVersion >= VER_1_16;
  }

  public boolean fluidHeightUsesDoublePrecision() {
    return protocolVersion >= VER_26_1_1;
  }

  public boolean refreshesFluidStateAfterMove() {
    return protocolVersion >= VER_26_1_1;
  }

  public boolean canUseElytra() {
    return protocolVersion >= VER_1_9 && MinecraftVersions.VER1_9_0.atOrAbove();
  }

  public boolean clientsideElytra() {
    return canUseElytra() && protocolVersion < VER_1_15;
  }

  public boolean serversideElytra() {
    return canUseElytra() && protocolVersion >= VER_1_15;
  }

  public boolean affectedByLevitation() {
    return protocolVersion >= VER_1_12;
  }

  public boolean roundEnvironmentNumbers() {
    return protocolVersion < VER_1_14;
  }

  public boolean canSprintWhileSneaking() {
    return protocolVersion >= VER_1_14;
  }

  public boolean sprintWhenHandActive() {
    return protocolVersion >= VER_1_9;
  }

  public boolean isPreMinecraft8() {
    return protocolVersion < VER_1_8;
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

  public boolean fireworkBoostTicksAfterPlayer() {
    return protocolVersion >= VER_1_17;
  }

  public boolean useItemMovementPacket() {
    return protocolVersion >= VER_1_17 && protocolVersion <= VER_1_21_5;
  }

  public boolean maskedMotionPossible() {
    return protocolVersion >= VER_1_14 && protocolVersion <= VER_1_17;
  }

  public boolean beeUpdate() {
    return protocolVersion >= VER_1_15;
  }

  public boolean sendsFacings() {
    return protocolVersion <= VER_1_11_1;
  }

  public boolean aquaticUpdate() {
    return protocolVersion >= VER_1_13;
  }

  public boolean combatUpdate() {
    return protocolVersion >= VER_1_9;
  }

  public boolean trailsAndTailsUpdate() {
    return protocolVersion >= VER_1_20_1;
  }

  public boolean supportsInteractionRangeAttributes() {
    return protocolVersion >= VER_1_20_5;
  }

  public boolean supportsGravityAttribute() {
    return protocolVersion >= VER_1_20_5;
  }

  public boolean supportsStepHeightAttribute() {
    return protocolVersion >= VER_1_20_5;
  }

  public boolean supportsMovementEfficiencyAttribute() {
    return protocolVersion >= VER_1_21;
  }

  public boolean supportsWaterMovementEfficiencyAttribute() {
    return protocolVersion >= VER_1_21;
  }

  public boolean supportsMovementAttributes() {
    return protocolVersion >= VER_26_2;
  }

  public boolean supportsSulfurGeysers() {
    return protocolVersion >= VER_26_2;
  }

  public boolean clientSpeculativeBlocks() {
    return protocolVersion >= VER_1_19_2;
  }

  public boolean selfAcknowledgePlacements() {
    return protocolVersion >= VER_1_19_2 && !MinecraftVersions.VER1_19_2.atOrAbove();
  }

  public boolean supportsPacketBundles() {
    return protocolVersion >= VER_1_19_4;
  }

  public double flyingPacketUncertaintyRadius() {
    if (protocolVersion >= VER_1_18_2) {
      return 0.0002 * 0.0002;
    } else {
      return 0.03;
    }
  }

  public boolean flyingPacketsCausePositionUncertainty() {
    return protocolVersion < VER_1_18_2;
  }

	public boolean newMotionClampLogic() {
		return protocolVersion >= VER_1_21_5;
	}

	public boolean bubbleColumnSurfaceUsesCollisionAndFluid() {
		return protocolVersion >= VER_1_21_5;
	}

	public boolean powderSnowInsideShapeUsesCollisionContext() {
		return protocolVersion >= VER_1_21_5;
	}

  public boolean newBlockEntityIntersectionLogic() {
    return protocolVersion >= VER_1_21_2;
  }

  public boolean oppositeBlockVectorBehavior() {
    return protocolVersion >= VER_1_14;
  }

  public boolean noPingMask() {
    return protocolVersion <= VER_1_17 && MinecraftVersions.VER1_17_0.atOrAbove();
  }

  public boolean sneakAsVehicleSteer() {
    return MinecraftVersions.VER1_21.atOrAbove();
  }

  public boolean sendsClientTickEnd() {
    return protocolVersion >= VER_1_21_2 && MinecraftVersions.VER1_21_2.atOrAbove();
  }

  public int entityPositionScale() {
    if (MinecraftVersions.VER1_9_0.below()) {
      return 32;
    } else {
      return 4096;
    }
  }

  public RotationTarget entityRotationTarget() {
    if (protocolVersion <= VER_1_14_4) {
      return PACKET_BASE;
    } else if (protocolVersion <= VER_1_20_1) {
      return CURRENT_POSITION;
    } else {
      return INTERPOLATION_TARGET;
    }
  }

  public boolean supportsEntityPositionSync() {
    return MinecraftVersions.VER1_21_2.atOrAbove();
  }

  public boolean compensateEntitySnapDisplacement() {
    return protocolVersion >= VER_1_21_5;
  }

  public boolean deduplicatesEntityTargets() {
    return protocolVersion >= VER_1_21_9;
  }

  public boolean roundEntityEncoding() {
    return protocolVersion >= VER_1_19_3;
  }

  public boolean multipliesEntityInterpolation() {
    return protocolVersion >= VER_1_20_2;
  }

  public boolean sendsInputs() {
    return protocolVersion >= VER_1_21_2 && MinecraftVersions.VER1_21_2.atOrAbove();
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String locale() {
    return locale;
  }

  private Boolean behind;

  public boolean outdatedClient() {
    if (behind == null || refreshes < 2) {
      MinecraftVersion server = MinecraftVersion.current();
      MinecraftVersion client;
      try {
        client = new MinecraftVersion(versionAsString(protocolVersion));
      } catch (Exception exception) {
        client = MinecraftVersions.VER1_19_4;
      }
      behind = !client.isAtLeast(server);
    }
    return behind;
  }

  public String versionString() {
    return versionString;
  }

  public MinecraftVersion minecraftVersion() {
    return minecraftVersion;
  }

  public boolean swordBlockingPossible() {
    return protocolVersion < VER_1_9 || (
      protocolVersion >= VER_1_21_5 && !MinecraftVersions.VER1_9_0.atOrAbove()
    );
  }

  public boolean viaVersionShieldBlockReplacement() {
    return protocolVersion >= VER_1_9 && protocolVersion < VER_1_21_5 && !MinecraftVersions.VER1_9_0.atOrAbove();
  }
}
