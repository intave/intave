/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.entity.type;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

final class EntityTypeDataRegistry {
  private static final String RESOURCE_DIRECTORY = "registry/entity_ids/";
  private static final MinecraftVersion UNIFIED_IDENTIFIER_VERSION = new MinecraftVersion("1.14");

	private final EntityTypeData[] livingEntities;
  private final EntityTypeData[] nonLivingEntities;

  EntityTypeDataRegistry() {
    this(MinecraftVersion.current());
  }

  EntityTypeDataRegistry(MinecraftVersion serverVersion) {
    String resourceVersion = nearestResourceVersion(serverVersion);
    boolean unifiedIdentifiers = new MinecraftVersion(resourceVersion).isAtLeast(UNIFIED_IDENTIFIER_VERSION);
    JsonArray entries = readJsonArray(RESOURCE_DIRECTORY + resourceVersion + ".json");

	  livingEntities = new EntityTypeData[largestIdentifier(entries, unifiedIdentifiers) + 1];
    nonLivingEntities = new EntityTypeData[largestIdentifier(entries, unifiedIdentifiers) + 1];
    for (JsonElement element : entries) {
      register(element.getAsJsonObject(), unifiedIdentifiers);
    }
  }

  private void register(JsonObject entry, boolean unifiedIdentifiers) {
    JsonElement identifierElement = entry.get(unifiedIdentifiers ? "id" : "internalId");
    if (identifierElement == null || identifierElement.isJsonNull()) {
      return;
    }

    int identifier = identifierElement.getAsInt();
    String name = entry.get("name").getAsString();
    HitboxSize size = HitboxSize.of(numberOrZero(entry, "width"), numberOrZero(entry, "height"));

    if (unifiedIdentifiers) {
      livingEntities[identifier] = new EntityTypeData(name, size, identifier, true, 11);
      nonLivingEntities[identifier] = new EntityTypeData(name, size, identifier, false, 11);
      return;
    }

    boolean living = "mob".equals(entry.get("type").getAsString());
    (living ? livingEntities : nonLivingEntities)[identifier] = new EntityTypeData(
      name, size, identifier, living, living ? 9 : 10
    );
  }

  private static int largestIdentifier(JsonArray entries, boolean unifiedIdentifiers) {
    String identifierName = unifiedIdentifiers ? "id" : "internalId";
    int largest = -1;
    for (JsonElement element : entries) {
      JsonElement identifier = element.getAsJsonObject().get(identifierName);
      if (identifier != null && !identifier.isJsonNull()) {
        largest = Math.max(largest, identifier.getAsInt());
      }
    }
    return largest;
  }

  private static float numberOrZero(JsonObject object, String name) {
    JsonElement value = object.get(name);
    return value == null || value.isJsonNull() ? 0.0F : value.getAsFloat();
  }

  private static String nearestResourceVersion(MinecraftVersion serverVersion) {
    String nearest = null;
    int nearestDistance = Integer.MAX_VALUE;
    for (JsonElement element : readJsonArray(RESOURCE_DIRECTORY + "index.json")) {
      String name = element.getAsString();
      MinecraftVersion candidate = new MinecraftVersion(name);
      if (candidate.getMajor() != serverVersion.getMajor()
        || candidate.getMinor() != serverVersion.getMinor()) {
        continue;
      }
      int distance = Math.abs(candidate.getBuild() - serverVersion.getBuild());
      if (distance < nearestDistance
        || distance == nearestDistance && candidate.getBuild() > serverVersion.getBuild()) {
        nearest = name;
        nearestDistance = distance;
      }
    }
    if (nearest == null) {
      throw new IntaveInternalException("Unsupported Minecraft version " + serverVersion.getVersion());
    }
    return nearest;
  }

  private static JsonArray readJsonArray(String path) {
    Resource resource = Resources.resourceFromJarOrBuild(path);
    if (!resource.available()) {
      throw new IntaveInternalException("Missing resource " + path);
    }
    try {
      return new JsonParser().parse(resource.readAsString()).getAsJsonArray();
    } catch (RuntimeException exception) {
      throw new IntaveInternalException("Unable to load " + path, exception);
    }
  }

  public EntityTypeData resolveFor(int entityType, boolean isLivingEntity) {
    EntityTypeData[] data = isLivingEntity ? livingEntities : nonLivingEntities;
    if (entityType < 0 || entityType >= data.length) {
      return null;
    }
    return data[entityType];
  }
}
