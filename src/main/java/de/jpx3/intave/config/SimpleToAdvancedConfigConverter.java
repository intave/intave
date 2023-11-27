package de.jpx3.intave.config;

import de.jpx3.intave.resource.BulkLineCollector;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class SimpleToAdvancedConfigConverter {
  private final Resource simpleConfigResource;
  private final Resource advancedConfigResource;
  private final ConfigTransferVariables ctvs;

  public SimpleToAdvancedConfigConverter(
    Resource simpleConfigResource,
    Resource advancedConfigResource,
    Resource ctvsStream
  ) {
    this.simpleConfigResource = simpleConfigResource;
    this.advancedConfigResource = advancedConfigResource;
    this.ctvs = ctvsStream.collectLines(ConfigTransferVariables.collector());
  }

  public void convert() {
    // easy solution, but breaks comments
    YamlConfiguration simpleConfig = configFromStream(simpleConfigResource.read());
//    YamlConfiguration advancedConfig = configFromStream(advancedConfigResource.read());
//
//    for (Map.Entry<String, String> replacements : ctvs.variables().entrySet()) {
//      String key = replacements.getKey();
//      String value = replacements.getValue();
//      if (simpleConfig.contains(key)) {
//        advancedConfig.set(value, simpleConfig.get(key));
//      }
//    }
//
//    for (Map.Entry<String, String> replacements : ctvs.placeholders().entrySet()) {
//      String key = replacements.getKey();
//      String value = replacements.getValue();
//      for (String advancedConfigKey : advancedConfig.getKeys(true)) {
//        advancedConfig.getStringList(advancedConfigKey)
//          .replaceAll(s -> s.replace(key, value));
//      }
//    }
//    // save advanced config
//    advancedConfigResource.write(advancedConfig.saveToString());

    Map<Integer, String> keys = new HashMap<>();
    advancedConfigResource.replaceLines(s -> {
      if (s.startsWith("#")) {
        return Collections.singletonList(s);
      } else if (s.endsWith(":")) {
        String key = s.substring(0, s.length() - 1);
        int height = 0;
        for (int i = 0; i < key.length(); i+=2) {
          if (key.charAt(i) == ' ') {
            height++;
          } else {
            break;
          }
        }
        key = key.trim();
        if (key.startsWith("\"") && key.endsWith("\"")) {
          key = key.substring(1, key.length() - 1);
        }
        if (key.startsWith("'") && key.endsWith("'")) {
          key = key.substring(1, key.length() - 1);
        }
        keys.put(height, key);
        return Collections.singletonList(s);
      } else if (s.trim().startsWith("-")) {
        int height = 0;
        for (int i = 0; i < s.length(); i+=2) {
          if (s.charAt(i) == ' ') {
            height++;
          } else {
            break;
          }
        }
        Pattern pattern = Pattern.compile("\\$[a-zA-Z0-9_\\-]+\\$");
        Matcher matcher = pattern.matcher(s);
        List<String> replacements = new ArrayList<>();
        while (matcher.find()) {
          String placeholder = matcher.group();
          for (Map.Entry<String, String> placeholders : ctvs.placeholders().entrySet()) {
            String key = placeholders.getKey();
            String value = placeholders.getValue();
            if (placeholder.equals(value)) {
              replacements.addAll(simpleConfig.getStringList(key));
            }
          }
        }
        StringBuilder indentation = new StringBuilder();
        for (int i = 0; i < height; i++) {
          indentation.append("  ");
        }
        if (replacements.isEmpty()) {
          return Collections.singletonList(s);
        } else {
          return replacements.stream()
            .map(this::checkQuotes)
            .map(replacement -> indentation + "- " + replacement)
            .collect(Collectors.toList());
        }
      } else if (s.contains(":")) {
        String key = s.split(":")[0];
        int height = 0;
        for (int i = 0; i < key.length(); i+=2) {
          if (key.charAt(i) == ' ') {
            height++;
          } else {
            break;
          }
        }
        StringBuilder fullPath = new StringBuilder();
        for (int i = 0; i < height; i++) {
          fullPath.append(keys.get(i)).append(".");
        }
        fullPath.append(key.trim());
        String replacement = null;
        for (Map.Entry<String, String> variables : ctvs.variables().entrySet()) {
          String simplePath = variables.getKey();
          String advancedPath = variables.getValue();
          if (fullPath.toString().equals(advancedPath)) {
            replacement = simpleConfig.getString(simplePath);
            break;
          }
        }
        StringBuilder indentation = new StringBuilder();
        for (int i = 0; i < height; i++) {
          indentation.append("  ");
        }
        if (replacement == null) {
          return Collections.singletonList(s);
        } else {
          return Collections.singletonList(indentation + key.trim() + ": " + checkQuotes(replacement));
        }
      } else {
        return Collections.singletonList(s);
      }
    });
  }

  private String checkQuotes(String input) {
    // check if number
    try {
      Double.parseDouble(input);
      return input;
    } catch (NumberFormatException ignored) {
    }
    // check if boolean
    if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("false")) {
      return input;
    }
    // check if string
    if (input.startsWith("\"") && input.endsWith("\"")) {
      return input;
    }
    // check if list
    if (input.startsWith("[") && input.endsWith("]")) {
      return input;
    }
    // check if null
    if (input.equalsIgnoreCase("null")) {
      return input;
    }
    return "\"" + input + "\"";
  }

  private YamlConfiguration configFromStream(InputStream stream) {
    return YamlConfiguration.loadConfiguration(new InputStreamReader(stream));
  }

  public static class ConfigTransferVariables {
    private final Map<String, String> variableMap = new HashMap<>();
    private final Map<String, String> placeholderMap = new HashMap<>();

    private void compile(List<String> lines) {
      boolean variableMode = false;
      boolean placeholderMode = false;
      for (String line : lines) {
        if (line.startsWith("#") || line.isEmpty()) {
          continue;
        }
        if (line.startsWith("+")) {
          line = line.toLowerCase();
          if (line.startsWith("+variables")) {
            variableMode = true;
            placeholderMode = false;
          } else if (line.startsWith("+placeholders")) {
            variableMode = false;
            placeholderMode = true;
          } else {
            System.out.println("Invalid line format: " + line);
            Thread.dumpStack();
          }
        } else {
          String[] split = line.split(" -> ");
          if (split.length != 2) {
            System.out.println("Invalid line format: " + line);
            Thread.dumpStack();
            continue;
          }
          String key = split[0].trim();
          String value = split[1].trim();
          if (variableMode) {
            variableMap.put(key, value);
          } else if (placeholderMode) {
            placeholderMap.put(key, value);
          } else {
            System.out.println("Invalid line format: " + line);
            Thread.dumpStack();
          }
        }
      }
    }

    public Map<String, String> variables() {
      return variableMap;
    }

    public Map<String, String> placeholders() {
      return placeholderMap;
    }

    private static final Collector<String, ?, ConfigTransferVariables> LINE_COLLECTOR =
      BulkLineCollector.withFinisher(strings -> {
        ConfigTransferVariables variables = new ConfigTransferVariables();
        variables.compile(strings);
        return variables;
      });

    public static Collector<String, ?, ConfigTransferVariables> collector() {
      return LINE_COLLECTOR;
    }
  }

  public static void main(String[] args) {
    Resource simpleConfig = Resources.resourceFromJarOrBuild("config.yml");
    Resource advancedConfig = Resources.resourceFromJarOrBuild("advanced.yml");
    Resource ctvs = Resources.resourceFromJarOrBuild("ctvs.mx");

    Resource cache = Resources.memoryResource();
    cache.write(advancedConfig);

    SimpleToAdvancedConfigConverter converter = new SimpleToAdvancedConfigConverter(simpleConfig, cache, ctvs);
    converter.convert();

    for (String readLine : cache.readLines()) {
      System.out.println(readLine);
    }
  }
}
