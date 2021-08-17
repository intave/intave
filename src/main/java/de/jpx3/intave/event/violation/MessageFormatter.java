package de.jpx3.intave.event.violation;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.placeholder.Placeholders;
import de.jpx3.intave.placeholder.TextContext;
import de.jpx3.intave.placeholder.ViolationPlaceholderContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class MessageFormatter {
  public static String resolveVerboseMessage(Player player, ViolationPlaceholderContext violationPlaceholderContext) {
    String messageLayout = resolveLayout("verbose");
    User user = UserRepository.userOf(player);
    String output = Placeholders.replacePlaceholders(
      messageLayout,
      Placeholders.PLUGIN_CONTEXT,
      Placeholders.SERVER_CONTEXT,
      user.playerAttributeContext(),
      user.identificationContext(),
      violationPlaceholderContext
    );
    output = ChatColor.translateAlternateColorCodes('&', output);
    output = output.trim().replace("  ", " ");
    return output;
  }

  public static String resolveNotifyReplacements(TextContext textContext) {
    String messageLayout = resolveLayout("notify");
    String output = Placeholders.replacePlaceholders(
      messageLayout,
      Placeholders.PLUGIN_CONTEXT,
      Placeholders.SERVER_CONTEXT,
      textContext
    );
    output = ChatColor.translateAlternateColorCodes('&', output);
    output = output.trim().replace("  ", " ");
    return output;
  }

  public static String resolveCommandReplacements(Player player, String command, ViolationPlaceholderContext violationPlaceholderContext) {
    User user = UserRepository.userOf(player);
    String output = Placeholders.replacePlaceholders(
      command,
      Placeholders.PLUGIN_CONTEXT,
      Placeholders.SERVER_CONTEXT,
      user.playerAttributeContext(),
      user.identificationContext(),
      violationPlaceholderContext
    );
    output = ChatColor.translateAlternateColorCodes('&', output);
    output = output.trim().replace("  ", " ");
    return output;
  }

  private static String resolveLayout(String key) {
    return IntavePlugin
      .singletonInstance()
      .configurationService()
      .configuration()
      .getString("layout." + key);
  }
}
