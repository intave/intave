package de.jpx3.intave.resource;

import java.io.*;
import java.nio.file.Files;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

final class FileResource implements Resource {
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
    File tempFile = new File(this.file.getAbsolutePath() + ".tmp");

    try {
      ensureParentDirectory();

      if (!tempFile.exists() && !tempFile.createNewFile()) {
        throw new IllegalStateException("Unable to create temp file " + tempFile);
      }

      tempFile.setReadable(true);
      tempFile.setWritable(true);
      tempFile.setExecutable(false);

      try (FileOutputStream output = new FileOutputStream(tempFile)) {
        byte[] buf = new byte[4096];
        int i;
        while ((i = inputStream.read(buf)) != -1) {
          output.write(buf, 0, i);
        }
        output.getFD().sync();
      }

      moveIntoPlaceSafe(tempFile);

    } catch (IOException exception) {
      tempFile.delete();
      throw new IllegalStateException("Unable to write " + file.getAbsolutePath(), exception);
    }
  }

  @Override
  public OutputStream writeStream() {
    try {
      ensureParentDirectory();

      File tempFile = new File(this.file.getAbsolutePath() + ".tmp");

      if (!tempFile.exists() && !tempFile.createNewFile()) {
        throw new IllegalStateException("Unable to create temp file " + tempFile);
      }

      tempFile.setReadable(true);
      tempFile.setWritable(true);
      tempFile.setExecutable(false);

      FileOutputStream fos = new FileOutputStream(tempFile);
      BufferedOutputStream bos = new BufferedOutputStream(fos, 8192);

      return new FilterOutputStream(bos) {
        private boolean closed = false;

        @Override
        public void close() throws IOException {
          if (closed) return;
          closed = true;

          try {
            out.flush();
            fos.getFD().sync();
          } finally {
            super.close();
          }

          moveIntoPlaceSafe(tempFile);
        }
      };

    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Override
  public boolean writeStreamSupported() {
    return true;
  }

  @Override
  public InputStream read() {
    try {
      if (!available()) {
        return new ByteArrayInputStream(new byte[0]);
      }
      return Files.newInputStream(file.toPath());
    } catch (IOException exception) {
      return new ByteArrayInputStream(new byte[0]);
    }
  }

  @Override
  public void delete() {
    if (!file.delete() && file.exists()) {
      throw new IllegalStateException("Failed to delete file: " + file.getAbsolutePath());
    }
  }

  static void cleanupTempFiles(File folder) {
    if (folder == null || !folder.exists()) return;

    File[] files = folder.listFiles((dir, name) -> name.endsWith(".tmp"));
    if (files == null) return;

    for (File f : files) {
      try {
        f.delete();
      } catch (Exception ignored) {}
    }
  }

  private void ensureParentDirectory() throws IOException {
    File parentFile = file.getParentFile();
    if (parentFile != null && !parentFile.exists()) {
      Files.createDirectories(parentFile.toPath());
    }
  }

  private void moveIntoPlaceSafe(File tempFile) {
    try {
      Files.move(tempFile.toPath(), this.file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING);
    } catch (IOException e1) {
      try {
        Files.move(tempFile.toPath(), this.file.toPath(), REPLACE_EXISTING);
      } catch (IOException e2) {
        tempFile.delete();
      }
    }
  }
}