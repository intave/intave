package de.jpx3.intave.klass.locate;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.resource.CompilerStreamFunctionProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class LocateFileCompiler implements CompilerStreamFunctionProvider<Locations> {
  public Locations apply(List<String> lines) {
    List<ClassLocation> classLocations = new ArrayList<>();
    List<FieldLocation> fieldLocations = new ArrayList<>();
    List<MethodLocation> methodLocations = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.isEmpty() || line.startsWith("#") || line.trim().isEmpty()) {
        continue;
      }
      String className;
      try {
        int classNameEndIndex = line.indexOf(" ");
        className = line.substring(0, classNameEndIndex);
        List<String> classLines = new ArrayList<>();
        List<String> fieldLines = new ArrayList<>();
        List<String> methodLines = new ArrayList<>();
        boolean fieldScope = false, methodScope = false;
        while (!"}".equals(line = lines.get(++i))) {
          if (line.isEmpty() || line.startsWith("#") || line.trim().isEmpty()) {
            continue;
          }
          if (line.startsWith("  methods {")) {
            if (fieldScope) {
              throw new IllegalStateException("Method scope entrance whilst field scope still active");
            }
            methodScope = true;
          } else if (line.startsWith("  fields {")) {
            if (fieldScope) {
              throw new IllegalStateException("Field scope entrance whilst method scope still active");
            }
            fieldScope = true;
          } else if (line.startsWith("  }")) {
            fieldScope = methodScope = false;
          } else if (fieldScope) {
            fieldLines.add(line.trim());
          } else if (methodScope) {
            methodLines.add(line.trim());
          } else {
            classLines.add(line.trim());
          }
        }
        classLocations.addAll(classCompile(className, classLines));
        fieldLocations.addAll(fieldCompile(className, fieldLines));
        methodLocations.addAll(methodCompile(className, methodLines));
      } catch (Exception exception) {
        // we don't want to exit the compilation process if it fails
        IntaveLogger.logger().error("Unable to compile line " + i + ": " + line);
        exception.printStackTrace();
      }
    }
    return new Locations(
      new ClassLocations(classLocations),
      new FieldLocations(fieldLocations),
      new MethodLocations(methodLocations)
    );
  }

  private List<MethodLocation> methodCompile(String className, List<String> affectedLines) {
    if (affectedLines.isEmpty()) {
      return Collections.emptyList();
    }
    affectedLines = new ArrayList<>(affectedLines);
    List<MethodLocation> result = new ArrayList<>();
    while (!affectedLines.isEmpty()) {
      String firstLine = affectedLines.remove(0);
      String matcherInput = firstLine.split("->")[0].trim();
      int exit = affectedLines.indexOf("}");
      if (exit < 0) {
        throw new IllegalStateException("Unable to locate next end block in class " + className);
      }
      List<String> linesThisSelector = affectedLines.subList(0, exit + 1);
      result.addAll(methodInnerCompile(className, matcherOf(matcherInput), linesThisSelector));
      affectedLines.subList(0, exit + 1).clear();
    }
    return result;
  }

  private List<MethodLocation> methodInnerCompile(String className, IntegerMatcher matcher, List<String> affectedLines) {
    List<MethodLocation> methodLocations = new ArrayList<>();
    for (String affectedLine : affectedLines) {
      if (affectedLine.endsWith("}")) {
        break;
      }
      String[] split = affectedLine.split("->");
      String from = split[0].trim().replace("\"", "");
      String to = split[1].trim().replace("\"", "");
      methodLocations.add(new MethodLocation(className, from, matcher, to));
    }
    return methodLocations;
  }

  private List<FieldLocation> fieldCompile(String className, List<String> affectedLines) {
    if (affectedLines.isEmpty()) {
      return Collections.emptyList();
    }
    affectedLines = new ArrayList<>(affectedLines);
    List<FieldLocation> result = new ArrayList<>();
    while (!affectedLines.isEmpty()) {
      String firstLine = affectedLines.remove(0);
      String matcherInput = firstLine.split("->")[0].trim();
      int exit = affectedLines.indexOf("}");
      if (exit < 0) {
        throw new IllegalStateException("End block expected in " + className + " of " + affectedLines);
      }
      List<String> linesThisSelector = affectedLines.subList(0, exit + 1);
      result.addAll(fieldInnerCompile(className, matcherOf(matcherInput), linesThisSelector));
      affectedLines.subList(0, exit + 1).clear();
    }
    return result;
  }

  private List<FieldLocation> fieldInnerCompile(String className, IntegerMatcher matcher, List<String> affectedLines) {
    List<FieldLocation> fieldLocations = new ArrayList<>();
    for (String affectedLine : affectedLines) {
      if (affectedLine.endsWith("}")) {
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

  public static LocateFileCompiler create() {
    return new LocateFileCompiler();
  }
}
