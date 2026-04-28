package de.jpx3.intave.klass.locate;

import com.comphenix.protocol.utility.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class ClassLocations implements Iterable<ClassLocation> {
  private final Iterable<ClassLocation> classLocations;

  public ClassLocations(Iterable<ClassLocation> classLocations) {
    this.classLocations = classLocations;
  }

  public ClassLocations filterByKey(String key) {
    return filter(classLocation -> classLocation.key().equalsIgnoreCase(key));
  }

  public ClassLocations reduceToCurrentVersion() {
    return filter(
      classLocation -> classLocation.matchesVersion(MinecraftVersion.getCurrentVersion())
    );
  }

  public <C, R> R collect(Collector<? super ClassLocation, C, R> collector) {
    C container = collector.supplier().get();
    BiConsumer<C, ? super ClassLocation> accumulator = collector.accumulator();
    Function<C, R> finisher = collector.finisher();
    for (ClassLocation classLocation : this) {
      accumulator.accept(container, classLocation);
    }
    return finisher.apply(container);
  }

  public Optional<ClassLocation> findAny() {
    Iterator<ClassLocation> iterator = iterator();
    return iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty();
  }

  public ClassLocation findAnyOrNull() {
    return findAny().orElse(null);
  }

  public ClassLocation findAnyOrDefault(Supplier<ClassLocation> supplier) {
    return findAny().orElseGet(supplier);
  }

  public Stream<ClassLocation> stream() {
    return StreamSupport.stream(this.classLocations.spliterator(), false);
  }

  public ClassLocations filter(Predicate<? super ClassLocation> predicate) {
    return new ClassLocations(
      stream().filter(predicate).collect(Collectors.toList())
    );
  }

  @NotNull
  @Override
  public Iterator<ClassLocation> iterator() {
    return classLocations.iterator();
  }

  @Override
  public void forEach(Consumer<? super ClassLocation> action) {
    classLocations.forEach(action);
  }

  @Override
  public Spliterator<ClassLocation> spliterator() {
    return classLocations.spliterator();
  }
}
