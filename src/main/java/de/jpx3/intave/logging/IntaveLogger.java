package de.jpx3.intave.logging;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.tools.AccessHelper;
import org.bukkit.ChatColor;

import java.io.*;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class IntaveLogger {

  public static boolean FILE_OUTPUT = true;
  public static boolean CONSOLE_OUTPUT;

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
    outputStreams.add(System.out);
    setup();
  }

  public void info(String infoMessage) {
    globalPrintLn("[Intave] " + infoMessage);
    logToFile("(INF) " + infoMessage);
  }

  public void error(String errorMessage) {
    globalPrintLn("[Intave] ERROR: " + errorMessage);
    logToFile("(ERR) " + errorMessage);
  }

  public void violation(String violation) {
    if(CONSOLE_OUTPUT) {
      globalPrintLn("[Intave] Violation: " + violation);
    }
    logToFile("(DET) " + violation);
  }

  public void commandExecution(String command) {
    command = ChatColor.stripColor(command);
    logToFile("(EXE) " + command);
  }

  public void exception(Throwable throwable) {
    globalPrintLn("[Intave] Caught an "+throwable.getClass().getSimpleName()+" exception");
    for (PrintStream outputStream : outputStreams) {
      throwable.printStackTrace(outputStream);
    }
  }

  public void globalPrintLn(Object object) {
    globalPrintLn(object.toString());
  }

  public void globalPrintLn(String message) {
    for (PrintStream outputStream : outputStreams) {
      outputStream.println(message);
    }
  }

  public void addOutputStream(PrintStream outputStream) {
    outputStreams.add(outputStream);
  }

  public void removeOutputStream(PrintStream outputStream) {
    outputStreams.remove(outputStream);
  }

  private final static DateTimeFormatter MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH.mm.ss.SSS");

  private synchronized void logToFile(String message) {
    if(!FILE_OUTPUT) {
      return;
    }
    try {
      if (!plugin.getDataFolder().exists()) {
        return;
      }
      boolean compressLogsLater = false;
      if(activeFileName != null) {
        if(AccessHelper.now() - lastNameCheck > 10000) {
          if(!activeFileName.equalsIgnoreCase(activeFileName())) {
            setup();
            activeFileName = activeFileName();
            compressLogsLater = true;
          }
          lastNameCheck = AccessHelper.now();
        }
      }

      message = message
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("  ", " ");

      String timestamp = "[" + LocalDateTime.now().format(MESSAGE_DATE_FORMATTER) + "] ";
      String clearMessage = ChatColor.stripColor(message);

      BackgroundExecutor.execute(() -> {
        printWriter.println(timestamp + clearMessage);
        printWriter.flush();
      });

      if(compressLogsLater) {
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
      if(printWriter != null) {
        printWriter.close();
      }
      this.printWriter = new PrintWriter(new FileWriter(activeFile, true));
    } catch (IOException e) {
      e.printStackTrace();
    }
    BackgroundExecutor.execute(this::performCompression);
  }

  public void shutdown() {
    if(printWriter != null) {
      printWriter.close();
    }
  }

  public synchronized void performCompression() {
    long start = AccessHelper.now();

    File[] pendingFiles = pendingLogFiles();
    int compressed = 0;
    if(pendingFiles != null) {
      for (File pendingFile : pendingFiles) {
        File archiveFile = archiveFileOf(pendingFile);
        if(pendingFile.exists() && !archiveFile.exists()) {
          try {
            archiver.archiveAndDeleteFile(pendingFile, archiveFile);
            compressed++;
          } catch (RuntimeException exception) {
            exception.printStackTrace();
          }
        }
      }
    }

    long duration = AccessHelper.now() - start;

    if(compressed > 0) {
      if(compressed == 1) {
        info("Compressed last log file (took " + duration + "ms)");
      } else {
        info("Compressed " + compressed + " log files (took " + duration + "ms)");
      }
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
