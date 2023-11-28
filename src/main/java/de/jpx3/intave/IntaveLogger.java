package de.jpx3.intave;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.cleanup.StartupTasks;
import de.jpx3.intave.diagnostic.ConsoleOutput;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.resource.FileArchiver;
import de.jpx3.intave.version.JavaVersion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.PluginLogger;

import java.io.*;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static de.jpx3.intave.IntaveLogger.FileLoggingState.UNRESOLVED;

public final class IntaveLogger extends PluginLogger {
  public static FileLoggingState FILE_OUTPUT = UNRESOLVED;
  public static final boolean VIOLATION_CONSOLE_OUTPUT = IntaveControl.GOMME_MODE;
  public static boolean DISABLE_COLOR_OUTPUT = IntaveControl.GOMME_MODE || JavaVersion.current() > 8;

  private static final String LOG_PATH = "plugins" + File.separator + "Intave" + File.separator + "logs";
  private final IntavePlugin plugin;
  private final List<PrintStream> outputStreams = new CopyOnWriteArrayList<>();
  private static IntaveLogger singletonInstance;
  private long lastNameCheck;
  private PrintWriter printWriter;
  private String activeFileName;

  public IntaveLogger(IntavePlugin plugin) {
    super(plugin);
    singletonInstance = this;
    this.plugin = plugin;
//    outputStreams.add(System.out);

    StartupTasks.add(() -> {
      boolean enabled = plugin.settings().getBoolean("logging.file-log", true);
      FILE_OUTPUT = FileLoggingState.fromBoolean(enabled);
      if (enabled) {
        setup();
      }
    });
  }

  public void checkColorAvailability() {
    if (!ProtocolLibraryAdapter.protocolLibAvailable()) {
      return;
    }
    if (JavaVersion.current() > 8 && MinecraftVersions.VER1_16_2.atOrAbove() && !IntaveControl.GOMME_MODE) {
      DISABLE_COLOR_OUTPUT = false;
    }
  }

  @Override
  public void log(LogRecord logRecord) {
    Level level = logRecord.getLevel();
    int levelInt = level.intValue();
    String message = logRecord.getMessage();
    if (levelInt == Integer.MAX_VALUE) {
      return;
    }
    if (levelInt >= Level.SEVERE.intValue()) {
      error(message);
    } else if (levelInt >= Level.WARNING.intValue()) {
      warn(message);
    } else {
      info(message);
    }
  }

  public void info(String infoMessage) {
    String message = IntavePlugin.prefix() + infoMessage;
    for (PrintStream outputStream : outputStreams) {
      outputStream.print(ChatColor.stripColor(message));
    }
    if (DISABLE_COLOR_OUTPUT) {
      Bukkit.getLogger().info(ChatColor.stripColor(message));
    } else {
      Bukkit.getConsoleSender().sendMessage(message);
    }
    logToFile("(INF) " + infoMessage);
  }

  public void error(String message) {
    String fullMessage = IntavePlugin.prefix() + ChatColor.DARK_RED + ChatColor.BOLD + "ERROR" + IntavePlugin.defaultColor() + ": " + ChatColor.RED + message;
    for (PrintStream outputStream : outputStreams) {
      outputStream.print(ChatColor.stripColor(fullMessage));
    }
    if (DISABLE_COLOR_OUTPUT) {
      Bukkit.getLogger().warning(ChatColor.stripColor(fullMessage));
    } else {
      Bukkit.getConsoleSender().sendMessage(fullMessage);
    }
    logToFile("(ERR) " + message);
  }

  public void warn(String message) {
    String fullMessage = IntavePlugin.prefix() + ChatColor.YELLOW + ChatColor.BOLD + "WARNING" + IntavePlugin.defaultColor() + ": " + ChatColor.RED + message;
    for (PrintStream outputStream : outputStreams) {
      outputStream.print(ChatColor.stripColor(fullMessage));
    }
    if (DISABLE_COLOR_OUTPUT) {
      Bukkit.getLogger().warning(ChatColor.stripColor(fullMessage));
    } else {
      Bukkit.getConsoleSender().sendMessage(fullMessage);
    }
    logToFile("(WARN) " + message);
  }
  public void violation(String violation) {
    if (VIOLATION_CONSOLE_OUTPUT) {
      printLine("[Intave] Violation: " + violation);
    }
    logToFile("(DET) " + violation);
  }

  public void commandExecution(String command) {
    if (ConsoleOutput.COMMAND_EXECUTION_DEBUG) {
      command = ChatColor.stripColor(command);
      printLine("[Intave] Issued server command /" + command);
      logToFile("(EXE) " + command);
    }
  }

  @Deprecated
  public void exception(Throwable throwable) {
    printLine("[Intave] Caught an " + throwable.getClass().getSimpleName() + " exception");
    for (PrintStream outputStream : outputStreams) {
      throwable.printStackTrace(outputStream);
    }
  }

  public void printLine(Object object) {
    printLine(object.toString());
  }

  public void printLine(String message) {
    for (PrintStream outputStream : outputStreams) {
      outputStream.print(message);
    }
    Bukkit.getLogger().info(message);
  }

  public void addOutputStream(PrintStream outputStream) {
    outputStreams.add(outputStream);
  }

  public void removeOutputStream(PrintStream outputStream) {
    outputStreams.remove(outputStream);
  }

  private static final DateTimeFormatter MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH.mm.ss.SSS");
  private static final List<String> PENDING_LOG_ENTRIES = new ArrayList<>();

  private synchronized void logToFile(String message) {
    if (!plugin.dataFolder().exists()) {
      return;
    }

    switch (FILE_OUTPUT) {
      case UNRESOLVED:
        PENDING_LOG_ENTRIES.add(message);
        return;
      case DISABLED:
        PENDING_LOG_ENTRIES.clear();
        return;
      case ENABLED:
        if (PENDING_LOG_ENTRIES.size() > 0) {
          String[] messages = PENDING_LOG_ENTRIES.toArray(new String[0]);
          PENDING_LOG_ENTRIES.clear();
          for (String pendingMessage : messages) {
            logToFile(pendingMessage);
          }
        }
        break;
    }

    try {
      boolean compressLogsLater = false;
      if (activeFileName != null) {
        if (System.currentTimeMillis() - lastNameCheck > 10000) {
          if (!activeFileName.equalsIgnoreCase(activeFileName())) {
            setup();
            activeFileName = activeFileName();
            compressLogsLater = true;
          }
          lastNameCheck = System.currentTimeMillis();
        }
      }

      message = message.replace("\n", "\\n").replace("\r", "\\r");

      String timestamp = "[" + LocalDateTime.now().format(MESSAGE_DATE_FORMATTER) + "] ";
      String clearMessage = ChatColor.stripColor(message);

      boolean finalCompressLogsLater = compressLogsLater;
      BackgroundExecutors.execute(() -> {
        printWriter.println(timestamp + clearMessage);
        printWriter.flush();

        if (finalCompressLogsLater) {
          BackgroundExecutors.executeWhenever(this::performCompression);
        }
      });

    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void setup() {
    this.activeFileName = activeFileName();

    try {
      File activeFile = activeFile();
      if (!activeFile.exists()) {
        activeFile.getParentFile().mkdirs();
        activeFile.createNewFile();
      }
      if (printWriter != null) {
        printWriter.close();
      }
      this.printWriter = new PrintWriter(new BufferedWriter(new FileWriter(activeFile, true)));
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create log file " + activeFileName, exception);
    }
  }

  public void shutdown() {
    if (printWriter != null) {
      printWriter.close();
    }
  }

  public synchronized void performCompression() {
    File[] pendingFiles = pendingLogFiles();
    if (pendingFiles == null || pendingFiles.length == 0) {
      return;
    }
    Map<File, File> filesToArchive = new HashMap<>();
    for (File pendingFile : pendingFiles) {
      File archiveFile = archiveFileOf(pendingFile);
      if (pendingFile.exists() && !archiveFile.exists()) {
        filesToArchive.put(pendingFile, archiveFile);
      }
    }

    for (Map.Entry<File, File> file : filesToArchive.entrySet()) {
      File originalFile = file.getKey();
      File archiveFile = file.getValue();
//      BackgroundExecutor.execute(() -> {
      if (originalFile.exists() && !archiveFile.exists()) {
        FileArchiver.archiveAndDeleteFile(originalFile, archiveFile);
        info("Compressed \"" + originalFile + "\"");
      }
//      });
    }
  }

  private boolean useFileLogs() {
    return true;
  }

  private File archiveFileOf(File fileToCompress) {
    String pendingFileName = fileToCompress.getName();
    String archiveName = pendingFileName.substring(0, pendingFileName.lastIndexOf('.')) + ".zip";
    return new File(fileToCompress.getParent(), archiveName);
  }

  private File[] pendingLogFiles() {
    File folder = new File(LOG_PATH);
    return folder.listFiles((dir, name) -> checkIfCompressionNeeded(name));
  }

  private boolean checkIfCompressionNeeded(String fileName) {
    if (fileName.equalsIgnoreCase(activeFileName())) {
      return false;
    }
    return fileName.startsWith("intave") && fileName.endsWith(".log");
  }

  private File activeFile() {
    return new File(LOG_PATH, activeFileName);
  }

  private static final ThreadLocal<Format> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy_MM_dd"));

  private String activeFileName() {
    String timestamp = dateFormat.get().format(System.currentTimeMillis());
    return "intave" + timestamp + ".log";
  }

  public static IntaveLogger logger() {
    return singletonInstance;
  }

  enum FileLoggingState {
    UNRESOLVED,
    ENABLED,
    DISABLED;

    private static FileLoggingState fromBoolean(boolean enabled) {
      return enabled ? ENABLED : DISABLED;
    }
  }
}
