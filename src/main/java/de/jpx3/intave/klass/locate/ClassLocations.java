package de.jpx3.intave.klass.locate;

import com.comphenix.protocol.utility.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ClassLocations implements Iterable<ClassLocation> {
  private final Iterable<ClassLocation> classLocations;

  public ClassLocations(Iterable<ClassLocation> classLocations) {
    this.classLocations = classLocations;
  }

  public ClassLocations filterByKey(String key) {
    return filter(classLocation -> classLocation.key().equals(key));
  }

  public ClassLocations reduceToCurrentVersion() {
    int currentMinecraftVersion = currentMinecraftVersion();
    return filter(classLocation -> classLocation.versionMatcher().matches(currentMinecraftVersion));
  }

  private int currentMinecraftVersion() {
    return MinecraftVersion.getCurrentVersion().getMinor();
  }

  public Optional<ClassLocation> findAny() {
    return stream().findAny();
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

  public ClassLocations filter(Predicate<ClassLocation> predicate) {
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
