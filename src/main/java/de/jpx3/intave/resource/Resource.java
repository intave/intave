package de.jpx3.intave.resource;

import de.jpx3.intave.resource.legacy.LegacyResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public interface Resource extends LegacyResource {
  boolean available();

  long lastModified();

  void write(InputStream inputStream);

  InputStream read();

  void delete();

  default String asString() {
    return collectLines(Collectors.joining());
  }

  default List<String> lines() {
    return collectLines(Collectors.toList());
  }

  default <C, R> R collectLines(Collector<? super String, C, R> collector) {
    C container = collector.supplier().get();
    try (InputStream inputStream = read()) {
      if (inputStream == null) {
        return collector.finisher().apply(container);
      }
      Scanner scanner = new Scanner(inputStream, "UTF-8");
      while (scanner.hasNextLine()) {
        collector.accumulator().accept(container, scanner.nextLine());
      }
    } catch (IOException exception) {
      exception.printStackTrace();
    }
    return collector.finisher().apply(container);
  }

  default Resource compressed() {
    return Resources.compressed(this);
  }

  default Resource encrypted() {
    return Resources.encrypted(this);
  }

  default Resource locked(File lockTarget) {
    return Resources.locked(lockTarget, this);
  }

  default Resource retryReads(int retries) {
    return Resources.retryRead(this, retries);
  }
}
