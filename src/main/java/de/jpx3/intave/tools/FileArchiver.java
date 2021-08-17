package de.jpx3.intave.tools;

import com.google.common.base.Preconditions;
import de.jpx3.intave.access.IntaveBootFailureException;
import de.jpx3.intave.access.IntaveInternalException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FileArchiver {
  public void archiveAndDeleteFile(File oldFile, File archiveFile) {
    validate(oldFile, archiveFile);
    archiveFile(oldFile, archiveFile);
    tryDeleteFile(oldFile);
  }

  public void archiveFile(File oldFile, File archiveFile) {
    validate(oldFile, archiveFile);
    tryCreateFile(archiveFile);
    moveFileToArchive(oldFile, archiveFile);
  }

  private void validate(File oldFile, File archiveFile) {
    Preconditions.checkNotNull(oldFile);
    Preconditions.checkNotNull(archiveFile);
    validateInputFile(oldFile);
    validateArchiveFile(archiveFile);
  }

  private void validateInputFile(File file) {
    if (file.isDirectory()) {
      throw new IllegalArgumentException("Can't pack directory");
    }
    if (!file.exists()) {
      throw new IllegalArgumentException("Input file does not exist");
    }
    if (!file.canRead()) {
      throw new IllegalArgumentException("Can't read input file");
    }
  }

  private void validateArchiveFile(File file) {
    if (file.isDirectory()) {
      throw new IllegalArgumentException("Can't have folder as archive");
    }
    if (!file.getName().endsWith(".zip")) {
      throw new IllegalArgumentException("Archive needs a .zip suffix");
    }
    if (file.exists()) {
      throw new IllegalArgumentException("Archive already exists?");
    }
  }

  private void moveFileToArchive(File file, File archiveFile) {
    try(
      FileInputStream in = new FileInputStream(file);
      ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archiveFile));
    ) {
      out.putNextEntry(new ZipEntry(file.getName()));
      out.setLevel(Deflater.BEST_COMPRESSION);
      int count;
      byte[] buffer = new byte[1024];
      while ((count = in.read(buffer)) != -1) {
        out.write(buffer, 0, count);
      }
    } catch (IOException exception) {
      throw new IntaveBootFailureException(exception);
    }
  }

  private void tryCreateFile(File file) {
    try {
      file.createNewFile();
    } catch (IOException e) {
      throw new IntaveInternalException(e);
    }
  }

  private void tryDeleteFile(File file) {
    file.delete();
  }
}
