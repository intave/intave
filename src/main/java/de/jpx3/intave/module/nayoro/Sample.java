package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.connect.IntaveDomains;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.security.ContextSecrets;
import de.jpx3.intave.security.HWIDVerification;
import de.jpx3.intave.security.LicenseAccess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;
import java.util.UUID;
import java.util.zip.*;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

public final class Sample {
  private String id;
  private Resource resource;

  public Sample() {
  }

  public Resource resource() {
    if (resource == null) {
      resource = writableSampleResource();
      if (!resource.writeStreamSupported()) {
        throw new IntaveInternalException("Sample resource does not support writing!");
      }
    }
    return resource;
  }

  public String id() {
    return id;
  }

  public long uploadAndDelete() throws IOException {
    if (resource == null) {
      return 0;
    }
    URL url = new URL("https://" + IntaveDomains.primaryServiceDomain() + "/samples/upload");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setDoOutput(true);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/zip");
    connection.setRequestProperty("Identifier", LicenseAccess.rawLicense());
    connection.setRequestProperty("Hardware", HWIDVerification.publicHardwareIdentifier());
    connection.setRequestProperty("User-Agent", "Intave/" + IntavePlugin.version());
    long length = 0;
    try (
      InputStream read = new DeflaterInputStream(resource.read(), new Deflater(Deflater.BEST_COMPRESSION));
      OutputStream outputStream = connection.getOutputStream()
    ) {
      int count;
      byte[] buffer = new byte[8192];
      while ((count = read.read(buffer)) != -1) {
        outputStream.write(buffer, 0, count);
        length += count;
      }
    }
    InputStream inputStream = connection.getInputStream();
    StringBuilder response = new StringBuilder();
    Scanner scanner = new Scanner(inputStream);
    while (scanner.hasNextLine()) {
      response.append(scanner.nextLine());
    }
    scanner.close();
    if (!"SUCCESS".contentEquals(response)) {
      throw new RuntimeException("Server error: " + response);
    }
    delete();
    return length;
  }

  public void delete() {
    if (resource != null) {
      resource.delete();
      resource = null;
    }
  }

  private Resource writableSampleResource() {
    File dataFolder = dataFolder();
    File sampleFile;
    do {
      sampleFile = new File(dataFolder, (id = randomId()) + ".sample");
    } while (sampleFile.exists());
    return Resources.resourceFromFile(sampleFile)/*.compressed()*/.locked(sampleFile);
  }

  private static String randomId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private static File dataFolder() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/Samples/";
    } else {
      if (GOMME_MODE) {
        filePath = ContextSecrets.secret("cache-directory") + "samples/";
      } else {
        filePath = System.getProperty("user.home") + "/.intave/samples/";
      }
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return workDirectory;
  }
}
