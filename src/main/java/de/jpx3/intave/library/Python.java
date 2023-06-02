package de.jpx3.intave.library;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.library.python.PythonTask;
import de.jpx3.intave.resource.Resource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Python {
  private static boolean available = false;
  private static String path = null;
  private static final Map<String, PythonTask> OPEN_TASKS = new HashMap<>();

  public static void setup() {
    path = pythonByPath();
    available = isPythonInstalled();
    if (!available) {
      IntaveLogger.logger().error("Unable to find Python installation.");
      IntaveLogger.logger().error("For improved functionality, please install Python 3.8.5 or higher.");
      return;
    }

    // todo build from source packages on separate thread
//    IntaveLogger.logger().info("Using $" + path + " as Python interpreter");
    String[] libraries = new String[]{
      "keras", "matplotlib", "numpy", "pandas", "scikit-learn", "scipy", "seaborn", "statsmodels"
    };
//    installLibraries(path, libraries);
    Set<String> uninstalled = pickUninstalled(path, libraries);

    // print info
    if (uninstalled.size() > 0) {
      IntaveLogger.logger().error("Unable to find the following Python libraries:");
      for (String s : uninstalled) {
        IntaveLogger.logger().error("  - " + s);
      }
      IntaveLogger.logger().error("For improved functionality, please install the above libraries.");
      available = false;
    }
  }

  public static PythonTask taskFromScript(String name, File script) {
    PythonTask task = new PythonTask(path, script);
    OPEN_TASKS.put(name, task);
    task.start();
    return task;
  }

  public static File prepareScript(Resource script) {
    return prepareScript(script, new HashMap<>());
  }

  public static File prepareScript(Resource script, Map<String, ? extends Resource> dataSegments) {
    // copy to temp file
    File file = tempFile();
    try {
      file.createNewFile();
      try (
        FileOutputStream fileOutputStream = new FileOutputStream(file)
      ) {
        Map<Resource, String> resourceCopies = new HashMap<>();
        List<String> lines = script.readLines();
        for (String line : lines) {
          String finalLine = line;
          List<String> collect = dataSegments.keySet().stream()
            .filter(s -> finalLine.contains("{DATA:{" + s + "}}"))
            .collect(Collectors.toList());

          for (String key : collect) {
            Resource resource = dataSegments.get(key);
            String path = resourceCopies.get(resource);
            if (path == null) {
              path = tempFile().getAbsolutePath().replace('\\', '/');
              // copy to temp file
              try (
                InputStream read = resource.read();
                OutputStream write = Files.newOutputStream(Paths.get(path))
              ) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = read.read(buffer)) > 0) {
                  write.write(buffer, 0, length);
                }
              }
              resourceCopies.put(resource, path);
            }
            line = line.replace("{DATA:{" + key + "}}", path);
          }

          fileOutputStream.write((line + System.lineSeparator()).getBytes());
        }
      }
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
    return file;
  }

  public static Map<String, Resource> scriptAssetMapFrom() {
    return new HashMap<>();
  }

  public static Map<String, Resource> scriptAssetMapFrom(
    String name,
    Resource resource
  ) {
    Map<String, Resource> map = new HashMap<>();
    map.put(name, resource);
    return map;
  }

  public static Map<String, Resource> scriptAssetMapFrom(
    String name1,
    Resource resource1,
    String name2,
    Resource resource2
  ) {
    Map<String, Resource> map = new HashMap<>();
    map.put(name1, resource1);
    map.put(name2, resource2);
    return map;
  }

  public static Map<String, Resource> scriptAssetMapFrom(
    String name1,
    Resource resource1,
    String name2,
    Resource resource2,
    String name3,
    Resource resource3
  ) {
    Map<String, Resource> map = new HashMap<>();
    map.put(name1, resource1);
    map.put(name2, resource2);
    map.put(name3, resource3);
    return map;
  }

  private static File tempFile() {
    File file;
    try {
      Class<?> relocator = Class.forName("de.jpx3.relocator.Relocator");
      File otherFile = (File) relocator.getMethod("h").invoke(null);
      File parentFile = otherFile.getParentFile();
      file = File.createTempFile("", ".py", parentFile);
    } catch (Exception exception) {
      try {
        file = Files.createTempFile("intave", ".py").toFile();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return file;
  }

  public static boolean available() {
    return available;
  }

  private static void installLibrary(
    String path, String library
  ) {
    installLibrary(path, library, 0);
  }

  private static void installLibrary(
    String path, String library, int tries
  ) {
    if (path == null) {
      throw new IllegalArgumentException("path cannot be null");
    }
    if (library == null) {
      throw new IllegalArgumentException("library cannot be null");
    }
    if (isLibraryInstalled(path, library)) {
      return;
    }
    IntaveLogger.logger().info("[Python] Installing \"" + library + "\" library..");
    execNow(path + " -m pip install " + library);

    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    if (!isLibraryInstalled(path, library)) {
      if (tries < 3) {
        installLibrary(path, library, tries + 1);
      } else {
        throw new IllegalStateException("Unable to install Python library: " + library);
      }
    }
  }

  private static boolean isLibraryInstalled(
    String path, String library
  ) {
    // show
    String output = execNowWithOutput(path + " -m pip show " + library);
    return output.contains("Name: " + library);
  }

  private static void installLibraries(
    String path, String... libraries
  ) {
    if (path == null) {
      throw new IllegalArgumentException("path cannot be null");
    }
    if (libraries == null) {
      throw new IllegalArgumentException("libraries cannot be null");
    }
    pickUninstalled(path, libraries).forEach(library -> installLibrary(path, library));
  }

  private static Set<String> pickUninstalled(
    String path, String... libraries
  ) {
    String output = execNowWithOutput(path + " -m pip show " + String.join(" ", libraries));
    Set<String> uninstalled = new HashSet<>();
    for (String library : libraries) {
      if (!output.contains("Name: " + library)) {
        uninstalled.add(library);
      }
    }
    return uninstalled;
  }

  private static boolean isPythonInstalled() {
    return path != null;
  }

  private static String pythonByPath() {
    String[] commands = new String[]{"python", "python3", "py", "py3", "lmao"};
    for (String command : commands) {
      if (execNowWithOutput(command + " --version").contains("Python 3")) {
        return command;
      }
    }
    return null;
  }

  private static String execNowWithOutput(
    String command
  ) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    execNow(command, stream);
    return stream.toString();
  }

  private static int execNow(String command) {
    return execNow(command, new OutputStream() {
      private StringBuilder builder = new StringBuilder();

      @Override
      public void write(int b) {
        builder.append((char) b);
        if (builder.toString().endsWith("\n")) {
          IntaveLogger.logger().info("[Python] " + builder.toString().trim());
          builder = new StringBuilder();
        }
      }
    });
  }

  private static int execNow(
    String command,
    OutputStream out
  ) {
    try {
      Process process = Runtime.getRuntime().exec(command);
      InputStream inputStream = process.getInputStream();
      if (out != null) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) > 0) {
          out.write(buffer, 0, read);
        }
        out.close();
      }
      return process.waitFor();
    } catch (IOException exception) {
      return -1;
    } catch (Exception exception) {
      exception.printStackTrace();
      return -1;
    }
  }

  public static Map<String, PythonTask> tasks() {
    return OPEN_TASKS;
  }
}
