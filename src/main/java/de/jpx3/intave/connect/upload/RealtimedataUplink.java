package de.jpx3.intave.connect.upload;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.connect.IntaveDomains;
import de.jpx3.intave.security.HWIDVerification;
import de.jpx3.intave.security.LicenseAccess;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class RealtimedataUplink {
  public static Set<String> enabledTypes = new HashSet<>();
  private static final Map<String, UplinkStore> stores = new HashMap<>();

  static class UplinkStore {
    private final File file;
    private final FileWriter writer;

    public UplinkStore(String type) {
      file = new File(dataFolder(), type + ".json");
      try {
        this.writer = new FileWriter(file);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      write("[");
    }

    public void write(String data) {
      try {
        writer.write(data);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public void close() {
      try {
        writer.write("]");
        writer.close();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public File file() {
      return file;
    }
  }

  public static void setType(String type) {
    enabledTypes.add(type);
  }

  public static boolean isEnabled(String type) {
    return enabledTypes.contains(type);
  }

  public static void writeDataLine(
    String type, String data
  ) {
    if (!enabledTypes.contains(type)) {
      return;
    }
    stores.computeIfAbsent(type, UplinkStore::new)
      .write(data + ",");
  }

  static {
    ShutdownTasks.add(() -> {
      stores.forEach((s, uplinkStore) -> {
        uplinkStore.close();
        File file = uplinkStore.file;
        if (file.length() > 0) {
          upload(file, s);
        }
      });
    });
  }

  private static void upload(
    File file, String type
  ) {
    try {
      URL url = new URL(IntaveDomains.primaryServiceDomain() + "/rtd/upload.php");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestProperty("Content-Type", "application/zip");
      connection.setRequestProperty("Identifier", LicenseAccess.rawLicense());
      connection.setRequestProperty("Hardware", HWIDVerification.publicHardwareIdentifier());
      connection.setRequestProperty("Type", type);
      connection.setRequestProperty("User-Agent", "Intave/" + IntavePlugin.fullVersion());

      // upload file content
      connection.setDoOutput(true);
      try (
        FileInputStream fis = new FileInputStream(file);
        OutputStream outputStream = connection.getOutputStream();
      ) {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = fis.read(buffer)) != -1) {
          outputStream.write(buffer, 0, read);
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      // get response
      int responseCode = connection.getResponseCode();
      if (responseCode != 200) {
        throw new RuntimeException("Failed to upload file: " + responseCode);
      }

      // delete file
      file.delete();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static File dataFolder() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/RTUP/";
    } else {
      filePath = System.getProperty("user.home") + "/.intave/rtup/";
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return workDirectory;
  }
}
