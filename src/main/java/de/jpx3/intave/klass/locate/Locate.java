package de.jpx3.intave.klass.locate;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class Locate {
  private static final Resource LOCATE_RESOURCE = Resources.resourceFromJarOrBuild("locate");
  private static final Locations LOCATIONS = LOCATE_RESOURCE.collectLines(LocateFileCompiler.resourceCollector()).reduced();
  private static final ClassLocations classLocations = LOCATIONS.classLocations();
  private static final FieldLocations fieldLocations = LOCATIONS.fieldLocations();
  private static final MethodLocations methodLocations = LOCATIONS.methodLocations();
  private static final Map<String, ClassLocation> classLocationCache = new ConcurrentHashMap<>();
  private static final Map<String, FieldLocation> fieldLocationCache = new ConcurrentHashMap<>();
  private static final Map<String, MethodLocation> methodLocationCache = new ConcurrentHashMap<>();

//  static {
//    classLocations.forEach(System.out::println);
//  }

  public static String patchyConvert(String input) {
    input = input.replace("/", ".");
    String output;
    if (input.startsWith("net.minecraft.server.v")) {
      output = classPathByKey(input.split("\\.")[4]);
    } else if (input.startsWith("net.minecraft")) {
      String[] split = input.split("\\.");
      String searchTarget = split[split.length - 1];
      Optional<ClassLocation> search = classLocations.filterByKey(searchTarget).reduceToCurrentVersion().findAny();
      output = search.map(ClassLocation::compiledLocation).orElse(searchTarget);
    } else {
      output = input;
    }
    return output.replace(".", "/");
  }

  public static Class<?> tryConvertByClassNameLookup(String name) {
    if (name.startsWith("net.minecraft.server.v")) {
      return classByKey(name.split("\\.")[4]);
    } else {
      try {
        return Class.forName(name);
      } catch (ClassNotFoundException exception) {
        throw new IllegalArgumentException("Unsupported class " + name);
      }
    }
  }

  private static String classPathByKey(String name) {
    return classLocationByKey(name).compiledLocation();
  }

  public static Class<?> classByKey(String name) {
    return classLocationByKey(name).access();
  }

  private static ClassLocation classLocationByKey(String key) {
    return classLocationCache.computeIfAbsent(key, Locate::classLocationLookupByKey);
  }

  private static ClassLocation classLocationLookupByKey(String key) {
    return classLocations
      .filterByKey(key)
      .findAnyOrDefault(() -> ClassLocation.defaultFor(key));
  }

  public static String patchyMethodCovert(String classInput, String methodName, String methodDescription) {
    classInput = classInput.replace("/", ".");
    String outputName;
    String methodKey = methodName + methodDescription;
    if (classInput.startsWith("net.minecraft.server.v")) {
      String classKey = classInput.split("\\.")[4];
      outputName = methodNameByKey(classKey, methodKey);
    } else if (classInput.startsWith("net.minecraft")) {
      String[] packages = classInput.split("\\.");
      String classKey = packages[packages.length - 1];
      outputName = methodNameByKey(classKey, methodKey);
    } else {
      outputName = methodName;
    }
    return outputName;
  }

  public static String patchyDescConvert(String classInput, String methodName, String methodDescription) {
    classInput = classInput.replace("/", ".");
    String outputDesc;
    String methodKey = methodName + methodDescription;
    if (classInput.startsWith("net.minecraft.server.v")) {
      String classKey = classInput.split("\\.")[4];
      outputDesc = descByKey(classKey, methodKey, methodDescription);
    } else if (classInput.startsWith("net.minecraft")) {
      String[] packages = classInput.split("\\.");
      String classKey = packages[packages.length - 1];
      outputDesc = descByKey(classKey, methodKey, methodDescription);
    } else {
      outputDesc = descByKey(classInput, methodKey, methodDescription);
    }
    return outputDesc;
  }

  public static Method methodByKey(String classKey, String methodKey) {
    String key = classKey + "." + methodKey;
    MethodLocation methodLocation = methodLocationCache.computeIfAbsent(key, s -> methodLookupByKey(classKey, methodKey));
    return methodLocation.access();
  }

  private static String methodNameByKey(String classKey, String methodKey) {
    String key = classKey + "." + methodKey;
    MethodLocation methodLocation = methodLocationCache.computeIfAbsent(key, s -> methodLookupByKey(classKey, methodKey));
    return methodLocation.targetMethodName();
  }

  private static String descByKey(String classKey, String methodKey, String orig) {
    String key = classKey + "." + methodKey;
    MethodLocation methodLocation = methodLocationCache.computeIfAbsent(key, s -> methodLookupByKey(classKey, methodKey));
    if (methodLocation.key().equals(methodLocation.target())) {
      return orig;
    }
    return methodLocation.targetMethodDesc();
  }

  private static MethodLocation methodLookupByKey(String classKey, String methodKey) {
    return methodLocations
      .filterByClassKey(classKey)
      .filterByMethodKey(methodKey)
      .findAnyOrDefault(() -> MethodLocation.defaultFor(classKey, methodKey));
  }

  public static String patchyFieldCovert(String classInput, String fieldKey) {
    classInput = classInput.replace("/", ".");
    String output;
    if (classInput.startsWith("net.minecraft.server.v")) {
      output = fieldNameByKey(classInput.split("\\.")[4], fieldKey);
    } else {
      output = fieldKey;
    }
    return output;
  }

  public static Field fieldByKey(String classKey, String fieldKey) {
    return fieldLocationCache.computeIfAbsent(
      classKey + "." + fieldKey,
      x -> fieldLookupByKey(classKey, fieldKey)
    ).access();
  }

  private static String fieldNameByKey(String classKey, String fieldKey) {
//    String key = classKey + "." + fieldKey;
//    FieldLocation fieldLocation = fieldLocationCache.computeIfAbsent(key, s -> fieldLookupByKey(classKey, fieldKey));
//    return fieldLocation.targetName();
    return fieldLocationCache.computeIfAbsent(
      classKey + "." + fieldKey,
      x -> fieldLookupByKey(classKey, fieldKey)
    ).targetName();
  }

  private static FieldLocation fieldLookupByKey(String classKey, String fieldKey) {
    return fieldLocations
      .filterByClassKey(classKey)
      .filterByFieldKey(fieldKey)
      .stream().findAny()
      .orElseGet(() -> FieldLocation.defaultFor(classKey, fieldKey));
  }

  public static void setup() {
    ShutdownTasks.add(Locate::close);
  }

  public static void close() {
    classLocationCache.clear();
    methodLocationCache.clear();
    fieldLocationCache.clear();
  }

  // for tests:

  static ClassLocations classLocations() {
    return classLocations;
  }

  static MethodLocations methodLocations() {
    return methodLocations;
  }

  static FieldLocations fieldLocations() {
    return fieldLocations;
  }
}
