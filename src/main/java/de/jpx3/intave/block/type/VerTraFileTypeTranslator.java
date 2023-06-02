package de.jpx3.intave.block.type;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.ImmutableSet;
import de.jpx3.intave.access.IntaveResourceCompilationException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.resource.LineCollector;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collector;

final class VerTraFileTypeTranslator {
  private static final Pattern SELECTOR_REGEX_PATTERN = Pattern.compile("^from ([0-9]+(\\.[0-9]+)+) down to ([0-9]+(\\.[0-9]+)+) interpret$", Pattern.CASE_INSENSITIVE);

  public static TypeTranslations apply(List<String> lines) {
    lines.removeIf(String::isEmpty);
    lines.removeIf(line -> line.startsWith("#"));
    MinecraftVersion fromVersion = null;
    MinecraftVersion toVersion = null;
    List<TypeTranslation> translations = new ArrayList<>();
    for (String line : lines) {
      boolean mapping = line.startsWith("  ");
      try {
        if (mapping) {
          if (fromVersion == null) {
            throw new IntaveResourceCompilationException("Mapping entered without selector");
          }
          String[] split = line.trim().split(" as ");
          String fromTypeName = split[0], toTypeName = split[1];
          Material fromType = searchMaterial(fromTypeName);
          Material toType = searchMaterial(toTypeName);
          if (fromType != null && toType != null) {
            translations.add(new TypeTranslation(fromVersion, toVersion, fromType, toType));
          }
        } else {
          // selector
          if (!SELECTOR_REGEX_PATTERN.matcher(line).matches()) {
            throw new IntaveResourceCompilationException("Invalid selector pattern");
          }
          int fromVersionStartIndex = afterIndex(line, "from ");
          int fromVersionEndIndex = line.indexOf(" ", fromVersionStartIndex);
          fromVersion = new MinecraftVersion(line.substring(fromVersionStartIndex, fromVersionEndIndex));
          int toVersionStartIndex = afterIndex(line, "to ");
          int toVersionEndIndex = line.indexOf(" ", toVersionStartIndex);
          toVersion = new MinecraftVersion(line.substring(toVersionStartIndex, toVersionEndIndex));
        }
      } catch (IntaveResourceCompilationException exception) {
        throw new IntaveResourceCompilationException("Failed to compile line " + line + ": " + exception.getMessage());
      }
    }
    return TypeTranslations.ofCollection(translations);
  }

  public static Collector<String, ?, TypeTranslations> lineCollector() {
    return new Collector<String, List<TypeTranslation>, TypeTranslations>() {
      MinecraftVersion fromVersion = null;
      MinecraftVersion toVersion = null;

      @Override
      public Supplier<List<TypeTranslation>> supplier() {
        return ArrayList::new;
      }

      @Override
      public BiConsumer<List<TypeTranslation>, String> accumulator() {
        return (translations, line) -> {
          if (line == null || line.isEmpty() || line.startsWith("#")) {
            return;
          }
          boolean mapping = line.startsWith("  ");
          try {
            if (mapping) {
              if (fromVersion == null) {
                throw new IntaveResourceCompilationException("Mapping entered without selector");
              }
              String[] split = line.trim().split(" as ");
              String fromTypeName = split[0], toTypeName = split[1];
              Material fromType = searchMaterial(fromTypeName);
              Material toType = searchMaterial(toTypeName);
              if (fromType != null && toType != null) {
                translations.add(new TypeTranslation(fromVersion, toVersion, fromType, toType));
              }
            } else {
              // selector
              if (!SELECTOR_REGEX_PATTERN.matcher(line).matches()) {
                throw new IntaveResourceCompilationException("Invalid selector pattern");
              }
              int fromVersionStartIndex = afterIndex(line, "from ");
              int fromVersionEndIndex = line.indexOf(" ", fromVersionStartIndex);
              fromVersion = new MinecraftVersion(line.substring(fromVersionStartIndex, fromVersionEndIndex));
              int toVersionStartIndex = afterIndex(line, "to ");
              int toVersionEndIndex = line.indexOf(" ", toVersionStartIndex);
              toVersion = new MinecraftVersion(line.substring(toVersionStartIndex, toVersionEndIndex));
            }

          } catch (IntaveResourceCompilationException exception) {
            throw new IntaveResourceCompilationException("Failed to compile line " + line + ": " + exception.getMessage());
          }
        };
      }

      @Override
      public BinaryOperator<List<TypeTranslation>> combiner() {
        return (a, b) -> {
          a.addAll(b);
          return a;
        };
      }

      @Override
      public Function<List<TypeTranslation>, TypeTranslations> finisher() {
        return TypeTranslations::ofCollection;
      }

      @Override
      public Set<Characteristics> characteristics() {
        return ImmutableSet.of();
      }
    };
  }

  private static Material searchMaterial(String name) {
    Material search = Material.matchMaterial(name);
    if (search == null) {
      search = Material.getMaterial(name);
      if (search == null && MinecraftVersions.VER1_14_0.atOrAbove()) {
        search = Material.matchMaterial("LEGACY_" + name);
        if (search == null) {
          search = Material.getMaterial("LEGACY_" + name);
        }
      }
    }
    return search;
  }

  private static int afterIndex(String haystack, String needle) {
    return haystack.indexOf(needle) + needle.length();
  }

  private static final Collector<String, ?, TypeTranslations> RESOURCE_COLLECTOR = LineCollector.withFinisher(VerTraFileTypeTranslator::apply);

  public static Collector<String, ?, TypeTranslations> bulkLineCollector() {
    return RESOURCE_COLLECTOR;
  }
}
