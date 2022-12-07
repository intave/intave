package de.jpx3.intave.diagnostic.message;

import com.google.common.collect.ImmutableMap;
import org.bukkit.ChatColor;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class HardcodeConfigurationResolver implements ConfigurationResolver {
  @Override
  public OutputConfiguration of(UUID owner) {
    switch (owner.toString()) {
      case "5ee6db6d-6751-4081-9cbf-28eb0f6cc055":
        return richysConfiguration();
      case "4669e155-946a-4aeb-a15b-aeb1123509c8":
      case "975b9c57-1c0e-4a50-bb2d-7650b6c51b3a":
        return lennoxConfiguration(owner);

      default:
        return defaultConfiguration();
    }
  }

  private OutputConfiguration defaultConfiguration() {
    return richysConfiguration();
  }

  private OutputConfiguration richysConfiguration() {
    UUID owner = UUID.fromString("5ee6db6d-6751-4081-9cbf-28eb0f6cc055");
    EnumSet<MessageCategory> categories = EnumSet.allOf(MessageCategory.class);
    categories.remove(MessageCategory.SIMFUL);
    categories.remove(MessageCategory.TRUSTSET);
    categories.remove(MessageCategory.SIMFLT);

    Map<MessageCategory, ChatColor> colors = new HashMap<>();
    colors.put(MessageCategory.ATLALI, ChatColor.RED);
    colors.put(MessageCategory.ATRAFLT, ChatColor.RED);
    colors.put(MessageCategory.HERAN, ChatColor.RED);
    colors.put(MessageCategory.SIMFLT, ChatColor.DARK_GRAY);
    colors.put(MessageCategory.SIMFUL, ChatColor.GRAY);
    colors.put(MessageCategory.PKBF, ChatColor.GRAY);
    colors.put(MessageCategory.MKLG, ChatColor.DARK_PURPLE);
    colors.put(MessageCategory.PKDL, ChatColor.GRAY);
    colors.put(MessageCategory.TRUSTSET, ChatColor.GRAY);

    return OutputConfiguration.builder()
      .setOwner(owner)
      .setMinimumSeverity(MessageSeverity.LOW)
      .setPrefixDetail(PrefixDetail.REDUCED_NO_PREFIX)
      .setActiveCategories(categories)
      .setOutputColors(colors)
      .setCategoryConstraints(
        ImmutableMap.of(
          MessageCategory.ATLALI, player -> true,
          MessageCategory.ATRAFLT, player -> true,
          MessageCategory.HERAN, player -> true,
          MessageCategory.MKLG, player -> true,
          MessageCategory.SIMFLT, player -> player.getUniqueId().equals(owner)))
      .defaultDetailSelect(MessageDetail.FULL)
      .build();
  }

  private OutputConfiguration lennoxConfiguration(UUID owner) {
    EnumSet<MessageCategory> categories = EnumSet.allOf(MessageCategory.class);
    categories.remove(MessageCategory.SIMFUL);

    Map<MessageCategory, ChatColor> colors = new HashMap<>();
    colors.put(MessageCategory.ATLALI, ChatColor.YELLOW);
    colors.put(MessageCategory.ATRAFLT, ChatColor.YELLOW);
    colors.put(MessageCategory.HERAN, ChatColor.RED);
    colors.put(MessageCategory.SIMFLT, ChatColor.DARK_GRAY);
    colors.put(MessageCategory.SIMFUL, ChatColor.DARK_GRAY);
    colors.put(MessageCategory.PKBF, ChatColor.GRAY);
    colors.put(MessageCategory.MKLG, ChatColor.GRAY);
    colors.put(MessageCategory.PKDL, ChatColor.GRAY);
    colors.put(MessageCategory.TRUSTSET, ChatColor.GOLD);

    return OutputConfiguration.builder()
      .setOwner(owner)
      .setMinimumSeverity(MessageSeverity.LOW)
      .setPrefixDetail(PrefixDetail.REDUCED)
      .setActiveCategories(categories)
      .setOutputColors(colors)
      .setCategoryConstraints(
        ImmutableMap.of(
          MessageCategory.ATLALI, player -> true,
          MessageCategory.ATRAFLT, player -> true,
          MessageCategory.HERAN, player -> true,
          MessageCategory.MKLG, player -> true,
          MessageCategory.SIMFLT, player -> player.getUniqueId().equals(owner)))
      .defaultDetailSelect(MessageDetail.FULL)
      .build();
  }
}
