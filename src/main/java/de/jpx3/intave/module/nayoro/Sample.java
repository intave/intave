package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

public final class Sample {
  private String id;
  private Resource resource;

  public Sample() {
  }

  @Deprecated
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

  @Deprecated
  public long uploadAndDelete() throws IOException {
    if (resource == null) {
      return 0;
    }
    long length;
    try (InputStream inputStream = resource.read()) {
      length = inputStream.available();
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

  @Deprecated
  private Resource writableSampleResource() {
    File dataFolder = sampleFolder();
    File sampleFile;
    do {
      sampleFile = new File(dataFolder, (id = randomId()) + ".sample");
    } while (sampleFile.exists());
    return Resources.resourceFromFile(sampleFile)/*.compressed()*/.locked(sampleFile);
  }

  @Deprecated
  private static String randomId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  @Deprecated
  private static File sampleFolder() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/Samples/";
    } else {
      filePath = System.getProperty("user.home") + "/.intave/samples/";
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return workDirectory;
  }
}
