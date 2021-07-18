package de.jpx3.intave.logging;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.JavaVersion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.*;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class IntaveLogger {

  public static boolean FILE_OUTPUT = true;
  public static final boolean VIOLATION_CONSOLE_OUTPUT = IntaveControl.GOMME_MODE;
  public static boolean DISABLE_COLOR_OUTPUT = IntaveControl.GOMME_MODE || JavaVersion.current() > 8;

  private final static String LOG_PATH = "plugins" + File.separator + "Intave" + File.separator + "logs";
  private final IntavePlugin plugin;
  private final FileArchiver archiver;
  private final List<PrintStream> outputStreams = new CopyOnWriteArrayList<>();
  private static IntaveLogger singletonInstance;
  private long lastNameCheck;
  private PrintWriter printWriter;
  private String activeFileName;

  public IntaveLogger(IntavePlugin plugin) {
    singletonInstance = this;
    this.plugin = plugin;
    this.archiver = new FileArchiver();
//    outputStreams.add(System.out);
    setup();
  }

  public void checkColorAvailability() {
    if (!ProtocolLibraryAdapter.protocolLibAlreadyAvailable()) {
      return;
    }
    if (JavaVersion.current() > 8 && MinecraftVersions.VER1_16_2.atOrAbove() && !IntaveControl.GOMME_MODE) {
      DISABLE_COLOR_OUTPUT = false;
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

  public void error(String errorMessage) {
    String message = IntavePlugin.prefix() + ChatColor.RED + "ERROR" + IntavePlugin.defaultColor() + ": " + errorMessage;
    for (PrintStream outputStream : outputStreams) {
      outputStream.print(ChatColor.stripColor(message));
    }
    if (DISABLE_COLOR_OUTPUT) {
      Bukkit.getLogger().warning(ChatColor.stripColor(message));
    } else {
      Bukkit.getConsoleSender().sendMessage(message);
    }
    logToFile("(ERR) " + errorMessage);
  }

  public void warn(String errorMessage) {
    String message = IntavePlugin.prefix() + ChatColor.RED + "WARN" + IntavePlugin.defaultColor() + ": " + errorMessage;
    for (PrintStream outputStream : outputStreams) {
      outputStream.print(ChatColor.stripColor(message));
    }
    if (DISABLE_COLOR_OUTPUT) {
      Bukkit.getLogger().warning(ChatColor.stripColor(message));
    } else {
      Bukkit.getConsoleSender().sendMessage(message);
    }
    logToFile("(WARN) " + errorMessage);
  }

  public void violation(String violation) {
    if (VIOLATION_CONSOLE_OUTPUT) {
      pushPrintln("[Intave] Violation: " + violation);
    }
    logToFile("(DET) " + violation);
  }

  public void commandExecution(String command) {
    pushPrintln("[Intave] Issued server command /" + ChatColor.stripColor(command));
    command = ChatColor.stripColor(command);
    logToFile("(EXE) " + command);
  }

  public void exception(Throwable throwable) {
    pushPrintln("[Intave] Caught an "+throwable.getClass().getSimpleName()+" exception");
    for (PrintStream outputStream : outputStreams) {
      throwable.printStackTrace(outputStream);
    }
  }

  public void pushPrintln(Object object) {
    pushPrintln(object.toString());
  }

  public void pushPrintln(String message) {
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

  private final static DateTimeFormatter MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH.mm.ss.SSS");

  private synchronized void logToFile(String message) {
    if (!FILE_OUTPUT || !plugin.getDataFolder().exists()) {
      return;
    }

    try {
      boolean compressLogsLater = false;
      if (activeFileName != null) {
        if (AccessHelper.now() - lastNameCheck > 10000) {
          if (!activeFileName.equalsIgnoreCase(activeFileName())) {
            setup();
            activeFileName = activeFileName();
            compressLogsLater = true;
          }
          lastNameCheck = AccessHelper.now();
        }
      }

      message = message.replace("\n", "\\n").replace("\r", "\\r");

      String timestamp = "[" + LocalDateTime.now().format(MESSAGE_DATE_FORMATTER) + "] ";
      String clearMessage = ChatColor.stripColor(message);

      BackgroundExecutor.execute(() -> {
        printWriter.println(timestamp + clearMessage);
        printWriter.flush();
      });

      if (compressLogsLater) {
        BackgroundExecutor.execute(this::performCompression);
      }
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
      this.printWriter = new PrintWriter(new FileWriter(activeFile, true));
    } catch (IOException e) {
      e.printStackTrace();
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
          archiver.archiveAndDeleteFile(originalFile, archiveFile);
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

  private final static ThreadLocal<Format> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy_MM_dd"));

  private String activeFileName() {
    String timestamp = dateFormat.get().format(AccessHelper.now());
    return "intave" + timestamp + ".log";
  }

  public static IntaveLogger logger() {
    return singletonInstance;
  }
}
