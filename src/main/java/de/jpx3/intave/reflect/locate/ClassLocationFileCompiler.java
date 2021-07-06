package de.jpx3.intave.reflect.locate;

import de.jpx3.intave.world.blockaccess.CompilerStreamFunctionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ClassLocationFileCompiler implements CompilerStreamFunctionProvider<ClassLocations> {
  public ClassLocations apply(List<String> lines) {
    List<ClassLocation> classLocations = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      int classNameEndIndex = line.indexOf(" ");
      String className = line.substring(0, classNameEndIndex);
      List<String> affectedLines = new ArrayList<>();
      while (!(line = lines.get(++i)).equals("}")) {
        affectedLines.add(line.trim());
      }
      classLocations.addAll(compile(className, affectedLines));
    }
    return new ClassLocations(classLocations);
  }

  private List<ClassLocation> compile(String className, List<String> affectedLines) {
    return affectedLines.stream().map(line -> compile(className, line)).collect(Collectors.toList());
  }

  private ClassLocation compile(String className, String line) {
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
        return new IntegerMatchRange(Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]));
      }
      return new IntegerMatchValue(Integer.parseInt(input));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to resolve matcher from input " + input);
    }
  }
}
