package de.jpx3.intave.diagnostic;

import de.jpx3.intave.IntaveLogger;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class ConsoleOutput {
  public static boolean FAULT_KICKS = true;
  public static boolean TRUSTFACTOR_DEBUG = true;
  public static boolean CLIENT_VERSION_DEBUG = true;
  public static boolean COMMAND_EXECUTION_DEBUG = true;
  public static boolean CLOUD_LOG_IDS = true;
  public static final List<String> CONSOLE_OUTPUT = new ArrayList<>();

  public static void applyFrom(ConfigurationSection section) {
    FAULT_KICKS = loadFrom(section, "fault-kicks", "fault kicks");
    TRUSTFACTOR_DEBUG = loadFrom(section, "trustfactor", "trustfactor changes");
    CLIENT_VERSION_DEBUG = loadFrom(section, "client-version", "client version dumps");
    COMMAND_EXECUTION_DEBUG = loadFrom(section, "command-execution", "command executions");
    CLOUD_LOG_IDS = loadFrom(section, "cloud-log-ids", "cloud log ids");
    printWarnings();
  }

  private static void printWarnings() {
    if (CONSOLE_OUTPUT.isEmpty()) {
      return;
    }
    IntaveLogger.logger().info("Disabled debugs for " + describeListSelection(CONSOLE_OUTPUT));
    CONSOLE_OUTPUT.clear();
  }

  private static String describeListSelection(List<String> elements) {
    int size = elements.size();
    if (size == 0) {
      return "";
    } else if (size == 1) {
      return elements.get(0);
    } else {
      String elementsListed = String.join(", ", elements.subList(0, size - 1));
      String lastElement = elements.get(size - 1);
      return elementsListed + " and " + lastElement;
    }
  }

  private static boolean loadFrom(ConfigurationSection section, String key, String component) {
    boolean value = section == null || section.getBoolean(key, true);
    if (!value) {
      CONSOLE_OUTPUT.add(component);
    }
    return value;
  }
}
