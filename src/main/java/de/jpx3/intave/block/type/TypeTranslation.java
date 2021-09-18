package de.jpx3.intave.block.type;

import com.comphenix.protocol.utility.MinecraftVersion;
import org.bukkit.Material;

public final class TypeTranslation {
  private final MinecraftVersion versionFrom, versionTo;
  private final Material typeFrom, typeTo;

  public TypeTranslation(MinecraftVersion versionFrom, MinecraftVersion versionTo, Material typeFrom, Material typeTo) {
    this.versionFrom = versionFrom;
    this.versionTo = versionTo;
    this.typeFrom = typeFrom;
    this.typeTo = typeTo;
  }

  public MinecraftVersion versionFrom() {
    return versionFrom;
  }

  public MinecraftVersion versionTo() {
    return versionTo;
  }

  public Material typeFrom() {
    return typeFrom;
  }

  public Material typeTo() {
    return typeTo;
  }
}
