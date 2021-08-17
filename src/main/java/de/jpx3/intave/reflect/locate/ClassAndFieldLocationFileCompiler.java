package de.jpx3.intave.reflect.locate;

import de.jpx3.intave.resource.CompilerStreamFunctionProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ClassAndFieldLocationFileCompiler implements CompilerStreamFunctionProvider<ClassAndFieldLocations> {
  public ClassAndFieldLocations apply(List<String> lines) {
    lines.removeIf(String::isEmpty);
    lines.removeIf(string -> string.trim().isEmpty());
    List<ClassLocation> classLocations = new ArrayList<>();
    List<FieldLocation> fieldLocations = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      String className;
      try {
        int classNameEndIndex = line.indexOf(" ");
        className = line.substring(0, classNameEndIndex);
        List<String> classLines = new ArrayList<>(), fieldLines = new ArrayList<>();
        boolean fieldScope = false;
        while (!(line = lines.get(++i)).equals("}")) {
          if (line.contains("fields {")) {
            fieldScope = true;
          } else if (fieldScope) {
            fieldLines.add(line.trim());
          } else {
            classLines.add(line.trim());
          }
        }
        classLocations.addAll(classCompile(className, classLines));
        fieldLocations.addAll(fieldCompile(className, fieldLines));
      } catch (Exception exception) {
        throw new IllegalStateException("Unable to compile line " + i + ": " + line, exception);
      }
    }
    return new ClassAndFieldLocations(new ClassLocations(classLocations), new FieldLocations(fieldLocations));
  }

  private List<FieldLocation> fieldCompile(String className, List<String> affectedLines) {
    if (affectedLines.isEmpty()) {
      return Collections.emptyList();
    }
    String firstLine = affectedLines.get(0);
    String matcherInput = firstLine.split("->")[0].trim();
    IntegerMatcher matcher = matcherOf(matcherInput);
    return fieldInnerCompile(className, matcher, affectedLines.subList(1, affectedLines.size()));
  }

  private List<FieldLocation> fieldInnerCompile(String className, IntegerMatcher matcher, List<String> affectedLines) {
    List<FieldLocation> fieldLocations = new ArrayList<>();
    for (String affectedLine : affectedLines) {
      if(affectedLine.endsWith("}")) {
        break;
      }
      String[] split = affectedLine.split("->");
      String from = split[0].trim().replace("\"", "");
      String to = split[1].trim().replace("\"", "");
      fieldLocations.add(new FieldLocation(className, from, matcher, to));
    }
    return fieldLocations;
  }

  private List<ClassLocation> classCompile(String className, List<String> affectedLines) {
    return affectedLines
      .stream().map(line -> classCompile(className, line))
      .collect(Collectors.toList());
  }

  private ClassLocation classCompile(String className, String line) {
    String[] parts = line.split("->");
    String matcherInput = parts[0].trim();
    String versionDefinition = parts[1].trim().replace("/", ".");
    if (versionDefinition.equals("[nms-default]")) {
      versionDefinition = "net.minecraft.server.{version}." + className;
    } else {
      versionDefinition = versionDefinition.replace("\"", "");
    }
    IntegerMatcher versionMatcher = matcherOf(matcherInput);
    return new ClassLocation(className, versionMatcher, versionDefinition);
  }

  private IntegerMatcher matcherOf(String input) {
    try {
      if (input.startsWith("[")) {
        if (!input.endsWith("]")) {
          throw new IllegalStateException();
        }
        String[] numbers = input.substring(1, input.length() - 1).split("-");
        return IntegerMatcher.between(Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]));
      }
      return IntegerMatcher.exact(Integer.parseInt(input));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to resolve matcher from input " + input);
    }
  }
}
