package de.jpx3.intave.resource;

import de.jpx3.intave.resource.legacy.LegacyResource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Resource extends LegacyResource {
  boolean available();
  long lastModified();

  void write(InputStream inputStream);

  default void write(byte[] bytes) {
    write(new ByteArrayInputStream(bytes));
  }

  default void write(String string) {
    write(string.getBytes(StandardCharsets.UTF_8));
  }

  default void write(Collection<String> lines) {
    if (lines == null) {
      throw new NullPointerException("Lines cannot be null");
    }
    if (writeStreamSupported()) {
      try (OutputStream outputStream = writeStream()) {
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        boolean first = true;
        for (String line : lines) {
          if (first) {
            first = false;
          } else {
            writer.write(System.lineSeparator());
          }
          writer.write(line);
        }
        writer.flush();
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
    } else {
      write(String.join(System.lineSeparator(), lines));
    }
  }

  default void write(Stream<String> lines) {
    if (lines == null) {
      throw new NullPointerException("Lines cannot be null");
    }
    if (writeStreamSupported()) {
      try (OutputStream outputStream = writeStream()) {
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        AtomicBoolean first = new AtomicBoolean(true);
        lines.forEach(line -> {
          try {
            if (first.get()) {
              first.set(false);
            } else {
              writer.write(System.lineSeparator());
            }
            writer.write(line);
          } catch (IOException exception) {
            throw new RuntimeException(exception);
          }
        });
        writer.flush();
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
    } else {
      write(lines.collect(Collectors.joining(System.lineSeparator())));
    }
  }

  default void write(Resource resource) {
    if (writeStreamSupported()) {
      try (InputStream inputStream = resource.read();
           OutputStream outputStream = writeStream()) {
        if (inputStream == null) {
          return;
        }
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, read);
        }
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
    } else {
      try (InputStream read = resource.read()) {
        write(read);
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
    }
  }

  InputStream read();

  default OutputStream writeStream() {
    throw new UnsupportedOperationException("This resource does not support write streams");
  }

  default boolean writeStreamSupported() {
    return false;
  }

  void delete();

  default String readAsString() {
    return collectLines(Collectors.joining("\n"));
  }

  default List<String> readLines() {
    return collectLines(Collectors.toList());
  }

  default void replaceLines(Function<? super String, ? extends List<String>> lineReplacer) {
    List<String> lines = readLines();
    List<String> newLines = new ArrayList<>();
    for (String line : lines) {
      newLines.addAll(lineReplacer.apply(line));
    }
    write(newLines);
  }

  default <C, R> R collectLines(Collector<? super String, C, R> collector) {
    return collectLines(collector, Long.MAX_VALUE);
  }

  default <C, R> R collectLines(Collector<? super String, C, R> collector, long limit) {
    C container = collector.supplier().get();
    BiConsumer<C, ? super String> accumulator = collector.accumulator();
    Function<C, R> finisher = collector.finisher();
    try (InputStream inputStream = read()) {
      if (inputStream == null) {
        return finisher.apply(container);
      }
      try (Scanner scanner = new Scanner(inputStream, "UTF-8")) {
        while (scanner.hasNextLine() && limit-- > 0) {
          accumulator.accept(container, scanner.nextLine());
        }
      }
    } catch (IOException exception) {
      exception.printStackTrace();
    }
    return finisher.apply(container);
  }

  default Resource compressed() {
    return Resources.withCompression(this);
  }

  default Resource encrypted() {
    return Resources.withEncryption(this);
  }

  default Resource locked(File lockTarget) {
    return Resources.withLockingFile(lockTarget, this);
  }

  default Resource retryReads(int retries) {
    return Resources.retryRead(this, retries);
  }

  default Resource hashProtected(File file) {
    return Resources.hashProtected(file.getAbsolutePath(), this);
  }
}
