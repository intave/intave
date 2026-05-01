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
    try {
      ensureParentDirectory();
      File file = new File(this.file.getAbsolutePath() + ".tmp");
      if (!file.exists()) {
        if (!file.createNewFile()) {
          throw new IllegalStateException("Unable to create file " + file + ", exists: " + file.exists());
        }
      }
      file.setReadable(true);
      file.setWritable(true);
      file.setExecutable(false);
      try (FileOutputStream output = new FileOutputStream(file)) {
        byte[] buf = new byte[1024 * 2];
        int i;
        while ((i = inputStream.read(buf)) != -1) {
          output.write(buf, 0, i);
        }
        output.getFD().sync();
        inputStream.close();
      }
      moveIntoPlace(file);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to write " + file.getAbsolutePath(), exception);
    }
  }

  @Override
  public OutputStream writeStream() {
    try {
      ensureParentDirectory();
      File tempFile = new File(this.file.getAbsolutePath() + ".tmp");
      if (!tempFile.exists()) {
        if (!tempFile.createNewFile()) {
          throw new IllegalStateException("Unable to create tempFile " + tempFile + ", exists: " + tempFile.exists());
        }
      }
      tempFile.setReadable(true);
      tempFile.setWritable(true);
      tempFile.setExecutable(false);
      FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
      BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream, 1024 * 5);
      return new FilterOutputStream(outputStream) {
        private boolean closed;
        private boolean movedIntoPlace;

        @Override
        public void close() throws IOException {
          if (closed) {
            return;
          }
          closed = true;
          out.flush();
          fileOutputStream.getFD().sync();
          super.close();
          moveIntoPlace(tempFile);
          movedIntoPlace = true;
        }

        @Override
        protected void finalize() throws Throwable {
          try {
            if (!movedIntoPlace) {
              throw new IllegalStateException("OutputStream for " + file.getAbsolutePath() + " was not closed");
            }
          } finally {
            super.finalize();
          }
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
//      ByteArrayOutputStream inputBytes = new ByteArrayOutputStream();
//      byte[] buf = new byte[4096];
//      int i;
//      while ((i = inputStream.read(buf)) != -1) {
//        inputBytes.write(buf, 0, i);
//      }
//      inputStream.close();
//      return new ByteArrayInputStream(inputBytes.toByteArray());
      return Files.newInputStream(file.toPath());
    } catch (IOException exception) {
      return new ByteArrayInputStream(new byte[0]);
    }
  }

  @Override
  public void delete() {
    file.delete();
  }

  private void ensureParentDirectory() throws IOException {
    File parentFile = file.getParentFile();
    if (parentFile != null && !parentFile.exists()) {
      Files.createDirectories(parentFile.toPath());
    }
  }

  private void moveIntoPlace(File tempFile) throws IOException {
    try {
      Files.move(tempFile.toPath(), this.file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING);
    } catch (IOException atomicMoveException) {
      Files.move(tempFile.toPath(), this.file.toPath(), REPLACE_EXISTING);
    }
  }
}
