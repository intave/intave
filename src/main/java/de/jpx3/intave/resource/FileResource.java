package de.jpx3.intave.resource;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;

public final class FileResource implements Resource {
  private final File file;

  public FileResource(File file) {
    this.file = file;
  }

  @Override
  public boolean available() {
    return file.exists() && file.length() != 0;
  }

  @Override
  public long lastModified() {
    return file.lastModified();
  }

  @Override
  public void write(InputStream inputStream) {
    try {
      if (!file.exists()) {
        if (!file.createNewFile()) {
          throw new IllegalStateException("Unable to create file " + file + ", exists: " + file.exists());
        }
      }
      ByteArrayOutputStream inputBytes = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int i;
      while ((i = inputStream.read(buf)) != -1) {
        inputBytes.write(buf, 0, i);
      }
      inputStream.close();
      FileChannel fileChannel = new FileOutputStream(file).getChannel();
      ReadableByteChannel byteChannel = Channels.newChannel(new ByteArrayInputStream(inputBytes.toByteArray()));
      fileChannel.transferFrom(byteChannel, 0, Long.MAX_VALUE);
    } catch (IOException exception) {
      exception.printStackTrace();
    }
  }

  @Override
  public InputStream read() {
    try {
      if (!available()) {
        return new ByteArrayInputStream(new byte[0]);
      }
      InputStream inputStream = Files.newInputStream(file.toPath());
      ByteArrayOutputStream inputBytes = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int i;
      while ((i = inputStream.read(buf)) != -1) {
        inputBytes.write(buf, 0, i);
      }
      inputStream.close();
      return new ByteArrayInputStream(inputBytes.toByteArray());
    } catch (IOException exception) {
//      throw new IllegalStateException(exception);
      return new ByteArrayInputStream(new byte[0]);
    }
  }

  @Override
  public void delete() {
    file.delete();
  }
}
