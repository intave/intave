package de.jpx3.intave.klass.locate;

import de.jpx3.intave.cleanup.ShutdownTasks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Locate {
  private final static Locations CLASS_AND_FIELD_LOCATIONS =
    LocateFileCompiler.create().fromResourceInJar("/mappings/locate").reduced();
  private final static ClassLocations classLocations = CLASS_AND_FIELD_LOCATIONS.classLocations();
  private final static FieldLocations fieldLocations = CLASS_AND_FIELD_LOCATIONS.fieldLocations();
  private final static MethodLocations methodLocations = CLASS_AND_FIELD_LOCATIONS.methodLocations();
  private final static Map<String, ClassLocation> classLocationCache = new ConcurrentHashMap<>();
  private final static Map<String, FieldLocation> fieldLocationCache = new ConcurrentHashMap<>();
  private final static Map<String, MethodLocation> methodLocationCache = new ConcurrentHashMap<>();

  public static String patchyConvert(String input) {
    input = input.replace("/", ".");
    String output;
    if (input.startsWith("net.minecraft.server.v")) {
      output = classPathByKey(input.split("\\.")[4]);
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
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException("Unsupported class " + name);
      }
    }
  }

  public static String classPathByKey(String name) {
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
    if (classInput.startsWith("net.minecraft.server.v")) {
      outputName = methodNameByKey(classInput.split("\\.")[4], methodName + methodDescription);
    } else if(classInput.startsWith("net.minecraft")) {
      String[] packages = classInput.split("\\.");
      String classKey = packages[packages.length - 1];
      outputName = methodNameByKey(classKey, methodName + methodDescription);
    } else {
      outputName = methodName;
    }
    return outputName;
  }

  public static Method methodByKey(String classKey, String methodKey) {
    String key = classKey + "." + methodKey;
    MethodLocation methodLocation = methodLocationCache.computeIfAbsent(key, s -> methodLookupByKey(classKey, methodKey));
    return methodLocation.access();
  }

  public static String methodNameByKey(String classKey, String methodKey) {
    String key = classKey + "." + methodKey;
    MethodLocation methodLocation = methodLocationCache.computeIfAbsent(key, s -> methodLookupByKey(classKey, methodKey));
    return methodLocation.targetMethodName();
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
    String key = classKey + "." + fieldKey;
    FieldLocation fieldLocation = fieldLocationCache.computeIfAbsent(key, s -> fieldLookupByKey(classKey, fieldKey));
    return fieldLocation.access();
  }

  public static String fieldNameByKey(String classKey, String fieldKey) {
    String key = classKey + "." + fieldKey;
    FieldLocation fieldLocation = fieldLocationCache.computeIfAbsent(key, s -> fieldLookupByKey(classKey, fieldKey));
    return fieldLocation.targetName();
  }

  private static FieldLocation fieldLookupByKey(String classKey, String fieldKey) {
    return fieldLocations
      .filterByClassKey(classKey)
      .filterByFieldKey(fieldKey)
      .stream().findAny()
      .orElseGet(
        () -> FieldLocation.defaultFor(classKey, fieldKey)
      );
  }

  public static void setup() {
    ShutdownTasks.add(Locate::close);
  }

  public static void close() {
    classLocationCache.clear();
  }
}
