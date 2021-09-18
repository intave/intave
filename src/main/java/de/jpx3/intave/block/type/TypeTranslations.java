package de.jpx3.intave.block.type;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.ImmutableList;
import org.bukkit.Material;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class TypeTranslations {
  private final Collection<TypeTranslation> translations;

  private TypeTranslations(Collection<TypeTranslation> translations) {
    this.translations = translations;
  }

  public TypeTranslations specifiedTo(MinecraftVersion serverVersion, MinecraftVersion clientVersion) {
    Predicate<TypeTranslation> typeTranslationFilter = typeTranslation -> appropriateTranslation(typeTranslation, serverVersion, clientVersion);
    return clientVersion.isAtLeast(serverVersion) ? empty() : filtered(typeTranslationFilter);
  }

  private boolean appropriateTranslation(TypeTranslation typeTranslation, MinecraftVersion serverVersion, MinecraftVersion clientVersion) {
    return serverVersion.isAtLeast(typeTranslation.versionTo()) && !clientVersion.isAtLeast(typeTranslation.versionFrom()) && clientVersion.isAtLeast(typeTranslation.versionTo());
  }

  public TypeTranslations filtered(Predicate<TypeTranslation> keepConstraint) {
    return ofCollection(translations.stream().filter(keepConstraint).collect(Collectors.toList()));
  }

  private final static Collector<TypeTranslation, ?, Map<Material, Material>> MAP_COLLECTOR =
    Collectors.toMap(TypeTranslation::typeFrom, TypeTranslation::typeTo, (a, b) -> b);

  public Map<Material, Material> asMap() {
    return translations.stream().collect(MAP_COLLECTOR);
  }

  public static TypeTranslations empty() {
    return new TypeTranslations(Collections.emptyList());
  }

  public static TypeTranslations ofCollection(Collection<TypeTranslation> typeTranslations) {
    return new TypeTranslations(ImmutableList.copyOf(typeTranslations));
  }
}
