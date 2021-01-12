package de.jpx3.intave.access;

import de.jpx3.intave.tools.MathHelper;
import org.bukkit.ChatColor;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public enum TrustFactor implements Comparable<TrustFactor> {
  BYPASS(1000, ChatColor.WHITE, "intave.bypass"), // pocketmc
  DARK_GREEN(2, ChatColor.DARK_GREEN, "intave.trust.darkgreen"), // badlion
  GREEN(1, ChatColor.GREEN, "intave.trust.green"), // labymod / playtime
  YELLOW(0, ChatColor.YELLOW, "intave.trust.yellow"), // default
  ORANGE(-1, ChatColor.GOLD, "intave.trust.orange"), // once banned
  RED(-2, ChatColor.RED, "intave.trust.red"), // recently banned
  DARK_RED(-3, ChatColor.DARK_RED, "intave.trust.darkred") // invis installed / alt account

  ;

  final int factor;
  final ChatColor chatColor;
  final String permission;

  TrustFactor(int factor, ChatColor chatColor, String permission) {
    this.factor = factor;
    this.chatColor = chatColor;
    this.permission = permission;
  }

  public TrustFactor safer() {
    TrustFactor[] values = values();
    return values[MathHelper.minmax(0, ordinal() - 1, values.length)];
  }

  public TrustFactor unsafer() {
    if(this == BYPASS) {
      return BYPASS;
    }
    TrustFactor[] values = values();
    return values[MathHelper.minmax(0, ordinal() + 1, values.length)];
  }

  public int factor() {
    return factor;
  }

  public ChatColor chatColor() {
    return chatColor;
  }

  public String permission() {
    return permission;
  }
}
