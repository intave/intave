package de.jpx3.intave.resource;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.lib.asm.ByteVector;
import de.jpx3.intave.security.ContextSecrets;
import de.jpx3.intave.security.HashAccess;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class Resources {
  public static Resource resourceFromFile(File file) {
    return new FileResource(file);
  }

  public static Resource resourceFromFileWithLock(File file) {
    return resourceFromFile(file).locked(file);
  }

  public static Resource resourceFromWeb(URL url) {
    return new WebResource(url);
  }

  static Resource locked(File targetFile, Resource resource) {
    return new LockingLayer(targetFile, resource);
  }

  static Resource refreshFileAccessDateOnRead(File targetFile, Resource resource) {
    return new FileAccessTimeRefreshLayer(resource, targetFile);
  }

  static Resource encrypted(Resource resource) {
    return new EncryptionLayer(resource);
  }

  static Resource compressed(Resource resource) {
    // add later
    return resource;
  }

  static Resource retryRead(Resource resource, int retries) {
    return new RetryReadLayer(resource, retries);
  }

  static Resource fileSpread(File file, Function<File, Resource> resourcer, int spreads) {
    return new FileSpreadLayer(file, resourcer, spreads);
  }

  private final static int CLASS_VERSION = 4;

  @Native
  public static Resource versionDependentEncryptedFileResourceChain(String identifier) {
    File file = fileLocationOf(new UUID(~identifier.hashCode() | (CLASS_VERSION | CLASS_VERSION << 2), ~IntavePlugin.version().hashCode()) + "e");
    return refreshFileAccessDateOnRead(file, resourceFromFile(file).encrypted());
  }

  @Native
  public static Resource encryptedFileResourceChain(String identifier) {
    File file = fileLocationOf(new UUID(~identifier.hashCode() | (CLASS_VERSION | CLASS_VERSION << 2), -391180952) + "e");
    return refreshFileAccessDateOnRead(file, resourceFromFile(file).encrypted());
  }

  public static Resource cacheResourceChain(
    String url,
    String identifier,
    long expires
  ) {
    try {
      return cacheResourceChain(new URL(url), identifier, expires);
    } catch (MalformedURLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Native
  public static Resource cacheResourceChain(
    URL url,
    String identifier,
    long expires
  ) {
    File initialFile = fileLocationOf(new UUID(identifier.hashCode() | versionResourceKey(), ~IntavePlugin.version().hashCode()).toString().replace("-", "") + "c");
    Resource cache = fileSpread(initialFile, Resources::resourceFromFileWithLock, 8).encrypted();
    Resource access = resourceFromWeb(url);
    Resource resourceCache = new ResourceCache(cache, access, expires).retryReads(3);
    ResourceRegistry.registerResource(identifier, resourceCache);
    return resourceCache;
  }

  private static int fileHashCode = 0;

  @Native
  public static long versionResourceKey() {
    if (!IntaveControl.DISABLE_LICENSE_CHECK && fileHashCode == 0) {
      try {
        File currentJarFile = new File(IntavePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        fileHashCode = HashAccess.hashOf(currentJarFile).hashCode();
      } catch (URISyntaxException exception) {
        exception.printStackTrace();
        fileHashCode = -1;
      }
    }

    long quarterYearsSinceEpoch = ByteVector.startTime / (1000L * 60 * 60 * 24 * 365 / 4);
    String asString = String.valueOf(quarterYearsSinceEpoch);
    Random random = new Random(quarterYearsSinceEpoch);
    // compute the hash of the string
    MessageDigest messageDigest;
    try {
      messageDigest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    // shuffle the string using the random
    byte[] bytes = asString.getBytes(UTF_8);
    for (int i = 0; i < bytes.length; i++) {
      int index = random.nextInt(bytes.length);
      byte temp = bytes[i];
      bytes[i] = bytes[index];
      bytes[index] = temp;
    }
    // insert random bytes into the string, using the random
    byte[] randomBytes = new byte[bytes.length];
    for (int i = 0; i < randomBytes.length; i++) {
      randomBytes[i] = (byte) random.nextInt();
    }
    messageDigest.update(randomBytes);
    messageDigest.update(bytes);
    byte[] digest = messageDigest.digest();
    StringBuilder stringBuilder = new StringBuilder();
    for (byte b : digest) {
      stringBuilder.append(String.format("%02x", b));
    }
    String quarterHash = stringBuilder.toString();
    return (((long) (fileHashCode & 0xffff) | (quarterHash.hashCode() & 0xffff0000)) << Integer.SIZE);
  }

  private static File fileLocationOf(String resourceId) {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/";
    } else {
      if (GOMME_MODE) {
        filePath = ContextSecrets.secret("cache-directory");
      } else {
        filePath = System.getProperty("user.home") + "/.intave/";
      }
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return new File(workDirectory, resourceId);
  }
}
