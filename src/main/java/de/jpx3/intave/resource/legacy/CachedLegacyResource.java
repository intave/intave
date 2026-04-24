package de.jpx3.intave.resource.legacy;

import de.jpx3.intave.IntavePlugin;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings({"UnusedReturnValue", "ResultOfMethodCallIgnored"})
@Deprecated
public final class CachedLegacyResource implements LegacyResource {
  private final String name;
  private final String uri;
  private final long expireDuration;

  public CachedLegacyResource(
    String name, String uri,
    long expireDuration
  ) {
    this.name = name;
    this.uri = uri;
    this.expireDuration = expireDuration;
    this.prepareFile();
  }

  public boolean prepareFile() {
    return fileStore().exists();
  }

  public List<String> readLines() {
    return collectLines(Collectors.toList());
  }

  public boolean available() {
    return fileStore().exists() && fileStore().length() > 0;
  }

  public InputStream read() {
    if (!fileStore().exists()) {
      return new ByteArrayInputStream(new byte[0]);
    }
    try {
      return Files.newInputStream(fileStore().toPath());
    } catch (IOException exception) {
      return new ByteArrayInputStream(new byte[0]);
    }
  }

  public void write(InputStream inputStream) {
    try {
      Files.copy(inputStream, fileStore().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private File fileStore() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/";
    } else {
      filePath = System.getProperty("user.home") + "/.intave/";
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdirs();
    }
    return new File(workDirectory, resourceId());
  }

  private String resourceId() {
    return new UUID(~name.hashCode(), ~intaveVersion().hashCode()) + ".txt";
  }

  private String intaveVersion() {
    return IntavePlugin.version();
  }
}
