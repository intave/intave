package de.jpx3.intave.klass.locate;

import com.comphenix.protocol.utility.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class FieldLocations implements Iterable<FieldLocation> {
  private final Iterable<FieldLocation> fieldLocations;

  public FieldLocations(Iterable<FieldLocation> fieldLocations) {
    this.fieldLocations = fieldLocations;
  }

  public FieldLocations filterByClassKey(String key) {
    return filter(fieldLocation -> fieldLocation.classKey().equals(key));
  }

  public FieldLocations filterByFieldKey(String key) {
    return filter(fieldLocation -> fieldLocation.key().equals(key));
  }

  public FieldLocations reduceToCurrentVersion() {
    return filter(fieldLocation -> fieldLocation.matchesVersion(MinecraftVersion.getCurrentVersion()));
  }

  public Stream<FieldLocation> stream() {
    return StreamSupport.stream(this.fieldLocations.spliterator(), false);
  }

  public FieldLocations filter(Predicate<? super FieldLocation> predicate) {
    Iterable<FieldLocation> classLocations = stream().filter(predicate).collect(Collectors.toList());
    return new FieldLocations(classLocations);
  }

  public static FieldLocations empty() {
    return new FieldLocations(Collections.emptyList());
  }

  @NotNull
  @Override
  public Iterator<FieldLocation> iterator() {
    return fieldLocations.iterator();
  }

  @Override
  public void forEach(Consumer<? super FieldLocation> action) {
    fieldLocations.forEach(action);
  }

  @Override
  public Spliterator<FieldLocation> spliterator() {
    return fieldLocations.spliterator();
  }
}
