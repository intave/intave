package de.jpx3.intave.access.player.trust;

import de.jpx3.intave.math.MathHelper;
import org.bukkit.ChatColor;

public enum TrustFactor implements Comparable<TrustFactor> {
  BYPASS(1000, ChatColor.WHITE, "intave.bypass"),
  GREEN(2, ChatColor.GREEN, "intave.trust.green"),
  YELLOW(1, ChatColor.YELLOW, "intave.trust.yellow"),
  ORANGE(0, ChatColor.GOLD, "intave.trust.orange"),
  RED(-1, ChatColor.RED, "intave.trust.red"),
  DARK_RED(-2, ChatColor.DARK_RED, "intave.trust.darkred");

  final int factor;
  final ChatColor chatColor;
  final String permission;

  TrustFactor(int factor, ChatColor chatColor, String permission) {
    this.factor = factor;
    this.chatColor = chatColor;
    this.permission = permission;
  }

  public TrustFactor safer() {
    if (this == GREEN) {
      return GREEN;
    }
    TrustFactor[] values = values();
    return values[MathHelper.minmax(0, ordinal() - 1, values.length - 1)];
  }

  /**
   * The next level down, saturating at {@link #DARK_RED}.
   * <p>
   * The clamp is against {@code length - 1} because it indexes the array: with
   * {@code length} it read one past the end, so calling this on DARK_RED threw
   * {@link ArrayIndexOutOfBoundsException} — and since the one caller
   * ({@code StorageTrustfactorResolver}) lowers the level once per past violation inside a
   * {@code catch (Exception)} that falls back to the default, the automatic trust factor
   * silently reverted to the default for exactly the players with the most violations.
   */
  public TrustFactor unsafer() {
    if (this == BYPASS) {
      return BYPASS;
    }
    TrustFactor[] values = values();
    return values[MathHelper.minmax(0, ordinal() + 1, values.length - 1)];
  }

  public boolean atLeast(TrustFactor trustFactor) {
    return factor() >= trustFactor.factor();
  }

  public boolean atOrBelow(TrustFactor trustFactor) {
    return factor() <= trustFactor.factor();
  }

  public int factor() {
    return factor;
  }

  public String baseName() {
    return name().toLowerCase().replace("_", "");
  }

  public String coloredBaseName() {
    return chatColor() + baseName();
  }

  public ChatColor chatColor() {
    return chatColor;
  }

  public String permission() {
    return permission;
  }
}
