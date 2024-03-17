package de.jpx3.intave.diagnostic;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.BackgroundExecutors;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.nio.file.StandardOpenOption.APPEND;

public class CSVExport implements AutoCloseable {

  private final String name;
  private final String[] header;
  private final File file;
  private List<String> pendingData = new ArrayList<>();

  public CSVExport(String name, String... header) {
    this.name = name;
    this.header = header;
    this.file = generateFile();
  }

  public void write(Object... data) {
    StringBuilder builder = new StringBuilder();
    for (Object o : data) {
      builder.append(o).append(",");
    }
    builder.deleteCharAt(builder.length() - 1);
    pendingData.add(builder.toString());
    if (pendingData.size() > 100) {
      flushAsync();
    }
  }

  public void flushAsync() {
    BackgroundExecutors.executeWhenever(() -> {
      if (pendingData.isEmpty()) {
        return;
      }
      if (!file.exists()) {
        file.getParentFile().mkdirs();
        try {
          file.createNewFile();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        // write header
        StringBuilder builder = new StringBuilder();
        for (String s : header) {
          builder.append(s).append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
        pendingData.add(0, builder.toString());
      }
      try {
        Files.write(file.toPath(), pendingData, APPEND);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public void save() {
    flushAsync();
  }

  private File generateFile() {
    return new File(IntavePlugin.singletonInstance().dataFolder(), name + UUID.randomUUID() + ".csv");
  }

  @Override
  public void close() throws Exception {
    flushAsync();
  }
}
