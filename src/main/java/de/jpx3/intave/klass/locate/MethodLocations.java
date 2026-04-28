package de.jpx3.intave.klass.locate;

import com.comphenix.protocol.utility.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class MethodLocations implements Iterable<MethodLocation> {
  private final Iterable<MethodLocation> methodLocations;

  public MethodLocations(Iterable<MethodLocation> methodLocations) {
    this.methodLocations = methodLocations;
  }

  public MethodLocations filterByClassKey(String key) {
    return filter(methodLocation -> methodLocation.classKey().equals(key));
  }

  public MethodLocations filterByMethodKey(String key) {
    return filter(methodLocation -> methodLocation.translatedKey().equals(key));
  }

  public MethodLocations reduceToCurrentVersion() {
    return filter(methodLocation -> methodLocation.matchesVersion(MinecraftVersion.getCurrentVersion()));
  }

  public Optional<MethodLocation> findAny() {
    return stream().findAny();
  }

  public MethodLocation findAnyOrNull() {
    return findAny().orElse(null);
  }

  public MethodLocation findAnyOrDefault(Supplier<MethodLocation> supplier) {
    return findAny().orElseGet(supplier);
  }

  public Stream<MethodLocation> stream() {
    return StreamSupport.stream(this.methodLocations.spliterator(), false);
  }

  public MethodLocations filter(Predicate<? super MethodLocation> predicate) {
    return new MethodLocations(
      stream().filter(predicate).collect(Collectors.toList())
    );
  }

  public static MethodLocations empty() {
    return new MethodLocations(Collections.emptyList());
  }

  @Override
  public void forEach(Consumer<? super MethodLocation> action) {
    methodLocations.forEach(action);
  }

  @Override
  public Spliterator<MethodLocation> spliterator() {
    return methodLocations.spliterator();
  }

  @NotNull
  @Override
  public Iterator<MethodLocation> iterator() {
    return methodLocations.iterator();
  }
}
