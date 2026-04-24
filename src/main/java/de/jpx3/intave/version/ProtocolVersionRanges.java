package de.jpx3.intave.version;

import de.jpx3.intave.version.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

final class ProtocolVersionRanges implements Iterable<ProtocolVersionRange> {
  private final Collection<ProtocolVersionRange> versionRanges;

  public ProtocolVersionRanges(List<ProtocolVersionRange> versionRanges) {
    this.versionRanges = versionRanges;
  }

  public Optional<ProtocolVersionRange> newest() {
    return versionRanges.stream()
      .max(ProtocolVersionRange::compareTo);
  }

  public String byProtocolVersion(int version) {
    ProtocolVersionRange protocolVersionRange =
      versionRanges.stream()
        .filter(range -> range.includes(version))
        .findFirst()
        .orElseGet(() -> newest().orElseGet(() -> new ProtocolVersionRange(Integer.MIN_VALUE, Integer.MAX_VALUE, "1.0.0")));
    return protocolVersionRange.version();
  }

  public int nearestProtocolVersion(String version) {
    MinecraftVersion requestVersion = new MinecraftVersion(version);
    Optional<ProtocolVersionRange> nearest = versionRanges.stream()
      .min((first, second) -> {
        int firstDistance = Math.abs(first.asMinecraftVersion().compareTo(requestVersion));
        int secondDistance = Math.abs(second.asMinecraftVersion().compareTo(requestVersion));
        return firstDistance - secondDistance;
      });
    return nearest.map(ProtocolVersionRange::to).orElse(-1);
  }

  @NotNull
  @Override
  public Iterator<ProtocolVersionRange> iterator() {
    return versionRanges.iterator();
  }

  @Override
  public void forEach(Consumer<? super ProtocolVersionRange> action) {
    versionRanges.forEach(action);
  }

  @Override
  public Spliterator<ProtocolVersionRange> spliterator() {
    return versionRanges.spliterator();
  }
}
