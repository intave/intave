package de.jpx3.intave.block.type;

import de.jpx3.intave.version.MinecraftVersion;
import com.google.common.collect.ImmutableList;
import org.bukkit.Material;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class TypeTranslations {
  private final Collection<TypeTranslation> translations;

  private TypeTranslations(Collection<TypeTranslation> translations) {
    this.translations = translations;
  }

  public TypeTranslations specifiedTo(MinecraftVersion serverVersion, MinecraftVersion clientVersion) {
    Predicate<TypeTranslation> typeTranslationFilter = typeTranslation -> appropriateTranslation(typeTranslation, serverVersion, clientVersion);
    return clientVersion.isAtLeast(serverVersion) ? empty() : filter(typeTranslationFilter);
  }

  private boolean appropriateTranslation(TypeTranslation typeTranslation, MinecraftVersion serverVersion, MinecraftVersion clientVersion) {
    return serverVersion.isAtLeast(typeTranslation.versionTo()) && !clientVersion.isAtLeast(typeTranslation.versionFrom()) && clientVersion.isAtLeast(typeTranslation.versionTo());
  }

  public TypeTranslations filter(Predicate<? super TypeTranslation> keepConstraint) {
    return ofCollection(stream().filter(keepConstraint).collect(Collectors.toList()));
  }

  public Stream<TypeTranslation> stream() {
    return translations.stream();
  }

  private static final Collector<TypeTranslation, ?, Map<Material, Material>> MAP_COLLECTOR =
    Collectors.toMap(TypeTranslation::typeFrom, TypeTranslation::typeTo, (a, b) -> b);

  public Map<Material, Material> asTypeMap() {
    return collect(MAP_COLLECTOR);
  }

  public void forEach(Consumer<? super TypeTranslation> action) {
    translations.forEach(action);
  }

  public void forEachType(BiConsumer<? super Material, ? super Material> action) {
    translations.forEach(typeTranslation -> action.accept(typeTranslation.typeFrom(), typeTranslation.typeTo()));
  }

  public <R> R collect(Collector<? super TypeTranslation, ?, R> collector) {
    return translations.stream().collect(collector);
  }

  public static TypeTranslations empty() {
    return new TypeTranslations(Collections.emptyList());
  }

  public static TypeTranslations ofCollection(Collection<TypeTranslation> typeTranslations) {
    return new TypeTranslations(ImmutableList.copyOf(typeTranslations));
  }
}
