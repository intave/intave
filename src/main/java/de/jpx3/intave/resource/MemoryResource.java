package de.jpx3.intave.resource;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MemoryResource implements Resource {
  private byte[] data = new byte[0];
  private long lastModified = 0;

  @Override
  public boolean available() {
    return data != null;
  }

  @Override
  public long lastModified() {
    return lastModified;
  }

  @Override
  public void write(InputStream inputStream) {
    lastModified = System.currentTimeMillis();
    try {
      data = new byte[0];
      int read;
      byte[] buffer = new byte[8192];
      while ((read = inputStream.read(buffer)) != -1) {
        byte[] newData = new byte[data.length + read];
        System.arraycopy(data, 0, newData, 0, data.length);
        System.arraycopy(buffer, 0, newData, data.length, read);
        data = newData;
      }
      inputStream.close();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  @Override
  public void write(byte[] bytes) {
    lastModified = System.currentTimeMillis();
    data = bytes;
  }

  @Override
  public InputStream read() {
    return new ByteArrayInputStream(data);
  }

  @Override
  public OutputStream writeStream() {
    AtomicBoolean dataWasReset = new AtomicBoolean(false);
    return new OutputStream() {
      @Override
      public void write(int b) {
        if (!dataWasReset.getAndSet(true)) {
          data = new byte[0];
          lastModified = System.currentTimeMillis();
        }
        byte[] newData = new byte[data.length + 1];
        System.arraycopy(data, 0, newData, 0, data.length);
        newData[data.length] = (byte) b;
        data = newData;
        lastModified = System.currentTimeMillis();
      }

      @Override
      public void write(byte @NotNull [] b) {
        if (!dataWasReset.getAndSet(true)) {
          data = new byte[0];
          lastModified = System.currentTimeMillis();
        }
        byte[] newData = new byte[data.length + b.length];
        System.arraycopy(data, 0, newData, 0, data.length);
        System.arraycopy(b, 0, newData, data.length, b.length);
        data = newData;
        lastModified = System.currentTimeMillis();
      }

      @Override
      public void write(byte @NotNull [] b, int off, int len) {
        if (!dataWasReset.getAndSet(true)) {
          data = new byte[0];
          lastModified = System.currentTimeMillis();
        }
        byte[] newData = new byte[data.length + len];
        System.arraycopy(data, 0, newData, 0, data.length);
        System.arraycopy(b, off, newData, data.length, len);
        data = newData;
        lastModified = System.currentTimeMillis();
      }
    };
  }

  @Override
  public boolean writeStreamSupported() {
    return true;
  }

  @Override
  public void delete() {
    data = new byte[0];
    lastModified = System.currentTimeMillis();
  }
}
