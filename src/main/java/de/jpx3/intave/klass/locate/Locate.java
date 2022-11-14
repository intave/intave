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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class Locate {
  private static final Resource LOCATE_RESOURCE =
    IntaveControl.USE_DEBUG_LOCATE_RESOURCE ?
      Resources.resourceFromFile(new File(IntavePlugin.singletonInstance().dataFolder(), "locate")) :
      Resources.localServiceCacheResource("locate/" + IntavePlugin.version(), "locate", TimeUnit.DAYS.toMillis(14));
  private static final Locations CLASS_AND_FIELD_LOCATIONS = LocateFileCompiler.create().fromResource(LOCATE_RESOURCE).reduced();
  private static final ClassLocations classLocations = CLASS_AND_FIELD_LOCATIONS.classLocations();
  private static final FieldLocations fieldLocations = CLASS_AND_FIELD_LOCATIONS.fieldLocations();
  private static final MethodLocations methodLocations = CLASS_AND_FIELD_LOCATIONS.methodLocations();
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
    if (classInput.startsWith("net.minecraft.server.v")) {
      outputName = methodNameByKey(classInput.split("\\.")[4], methodName + methodDescription);
    } else if (classInput.startsWith("net.minecraft")) {
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

  private static String methodNameByKey(String classKey, String methodKey) {
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

  private static String fieldNameByKey(String classKey, String fieldKey) {
    String key = classKey + "." + fieldKey;
    FieldLocation fieldLocation = fieldLocationCache.computeIfAbsent(key, s -> fieldLookupByKey(classKey, fieldKey));
    return fieldLocation.targetName();
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
