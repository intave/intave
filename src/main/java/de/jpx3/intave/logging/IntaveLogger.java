package de.jpx3.intave.logging;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.tools.AccessHelper;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class IntaveLogger {
  private final static String LOG_PATH = "plugins" + File.separator + "Intave" + File.separator + "logs";
  private final IntavePlugin plugin;
  private final FileArchiver archiver;
  private long lastNameCheck;
  private PrintWriter printWriter;
  private String activeFileName;

  private final ExecutorService compressionService = Executors.newSingleThreadExecutor();

  public IntaveLogger(IntavePlugin plugin) {
    this.plugin = plugin;
    this.archiver = new FileArchiver();
    setup();
  }

  public void info(String s) {
    System.out.println("[Intave] " + s);
    logToFile("(INF) " + s);
  }

  public void error(String s) {
    System.out.println("[Intave] ERROR: " + s);
    logToFile("(ERR) " + s);
  }

  public void exception(Throwable throwable) {
    System.out.println("[Intave] Caught an "+throwable.getClass().getSimpleName()+" exception");
    throwable.printStackTrace();
  }

  private final static DateTimeFormatter MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH.mm.ss.SSS");

  private synchronized void logToFile(String message) {
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
      printWriter.println(timestamp + clearMessage);
      printWriter.flush();

      if(compressLogsLater) {
        compressionService.execute(this::performCompression);
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
}
