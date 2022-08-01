package de.jpx3.intave.resource;

import de.jpx3.intave.resource.legacy.LegacyResource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public interface Resource extends LegacyResource {
  boolean available();

  long lastModified();

  void write(InputStream inputStream);

  default void write(byte[] bytes) {
    write(new ByteArrayInputStream(bytes));
  }

  default void write(String string) {
    write(string.getBytes());
  }

  default void write(Collection<String> lines) {
    write(String.join(System.lineSeparator(), lines));
  }

  InputStream read();

  void delete();

  default String asString() {
    return collectLines(Collectors.joining());
  }

  default List<String> lines() {
    return collectLines(Collectors.toList());
  }

  default <C, R> R collectLines(Collector<? super String, C, R> collector) {
    Supplier<C> supplier = collector.supplier();
    BiConsumer<C, ? super String> accumulator = collector.accumulator();
    Function<C, R> finisher = collector.finisher();
    C container = supplier.get();
    try (InputStream inputStream = read()) {
      if (inputStream == null) {
        return finisher.apply(container);
      }
      Scanner scanner = new Scanner(inputStream, "UTF-8");
      while (scanner.hasNextLine()) {
        accumulator.accept(container, scanner.nextLine());
      }
    } catch (IOException exception) {
      exception.printStackTrace();
    }
    return finisher.apply(container);
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

  default Resource hashProtected(File file) {
    return Resources.hashProtected(file.getAbsolutePath(), this);
  }
}
