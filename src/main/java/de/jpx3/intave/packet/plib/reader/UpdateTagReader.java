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

package de.jpx3.intave.packet.plib.reader;

import de.jpx3.intave.registry.BlockRegistry;
import de.jpx3.intave.share.MinecraftKey;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public final class UpdateTagReader extends AbstractPacketReader {
  private static final String BLOCK_REGISTRY_KEY = "minecraft:block";

  public Map<MinecraftKey, List<MinecraftKey>> readTags() {
    Map<MinecraftKey, List<MinecraftKey>> tags = new HashMap<>();
    Object packetTags = packetTags();
    if (packetTags == null) {
      return tags;
    }

    Map<?, ?> tagMap;
    boolean idsOnWire;
    if (packetTags instanceof Map) {
      tagMap = blockTagMap((Map<?, ?>) packetTags);
      idsOnWire = true;
    } else {
      tagMap = legacyBlockTagMap(packetTags);
      idsOnWire = false;
    }
    if (tagMap == null || tagMap.isEmpty()) {
      return tags;
    }

    BlockRegistry blockRegistry = BlockRegistry.global();
    for (Map.Entry<?, ?> entry : tagMap.entrySet()) {
      MinecraftKey tagKey = minecraftKey(entry.getKey());
      if (tagKey == null) {
        continue;
      }

      List<MinecraftKey> values = new ArrayList<>();
      Iterator<?> members = membersOf(entry.getValue());
      while (members.hasNext()) {
        Object member = members.next();
        MinecraftKey value = idsOnWire && member instanceof Number
          ? blockRegistry.keyById(((Number) member).intValue())
          : blockRegistry.keyOf(member);
        if (value != null) {
          values.add(value);
        }
      }
      tags.put(tagKey, values);
    }
    return tags;
  }

  private Object packetTags() {
    for (Object value : packet().getModifier().getValues()) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static Map<?, ?> blockTagMap(Map<?, ?> registries) {
    for (Map.Entry<?, ?> registry : registries.entrySet()) {
      if (isBlockRegistryKey(registry.getKey())) {
        return mapField(registry.getValue());
      }
    }
    return null;
  }

  private static boolean isBlockRegistryKey(Object resourceKey) {
    if (resourceKey == null) {
      return false;
    }
    if (BLOCK_REGISTRY_KEY.equals(keyString(resourceKey))) {
      return true;
    }
    for (Field field : instanceFields(resourceKey.getClass())) {
      Object value = read(field, resourceKey);
      if (value != null && BLOCK_REGISTRY_KEY.equals(keyString(value))) {
        return true;
      }
    }
    return false;
  }

  private static Map<?, ?> legacyBlockTagMap(Object tagSupplier) {
    Object blockTags = firstReferenceField(tagSupplier);
    return mapField(blockTags);
  }

  private static Object firstReferenceField(Object owner) {
    if (owner == null) {
      return null;
    }
    for (Field field : instanceFields(owner.getClass())) {
      if (!field.getType().isPrimitive()) {
        Object value = read(field, owner);
        if (value != null) {
          return value;
        }
      }
    }
    return null;
  }

  private static Map<?, ?> mapField(Object owner) {
    if (owner instanceof Map) {
      return (Map<?, ?>) owner;
    }
    if (owner == null) {
      return null;
    }
    for (Field field : instanceFields(owner.getClass())) {
      if (Map.class.isAssignableFrom(field.getType())) {
        Object value = read(field, owner);
        if (value instanceof Map) {
          return (Map<?, ?>) value;
        }
      }
    }
    return null;
  }

  private static Iterator<?> membersOf(Object value) {
    if (value instanceof Iterable) {
      return ((Iterable<?>) value).iterator();
    }
    if (value != null && value.getClass().isArray()) {
      return new Iterator<Object>() {
        private final int length = Array.getLength(value);
        private int index;

        @Override
        public boolean hasNext() {
          return index < length;
        }

        @Override
        public Object next() {
          return Array.get(value, index++);
        }
      };
    }
    if (value != null) {
      for (Field field : instanceFields(value.getClass())) {
        Object members = read(field, value);
        if (members instanceof Iterable) {
          return ((Iterable<?>) members).iterator();
        }
        if (members != null && members.getClass().isArray()) {
          return membersOf(members);
        }
      }
    }
    return Collections.emptyList().iterator();
  }

  private static MinecraftKey minecraftKey(Object value) {
    if (value instanceof com.comphenix.protocol.wrappers.MinecraftKey) {
      return MinecraftKey.fromProtocolLib(
        (com.comphenix.protocol.wrappers.MinecraftKey) value
      );
    }
    String key = keyString(value);
    if (key == null || key.indexOf(':') < 0) {
      return null;
    }
    try {
      return MinecraftKey.from(key);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static String keyString(Object value) {
    if (value instanceof com.comphenix.protocol.wrappers.MinecraftKey) {
      return ((com.comphenix.protocol.wrappers.MinecraftKey) value).getFullKey();
    }
    return value == null ? null : value.toString();
  }

  private static List<Field> instanceFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> current = type; current != null && current != Object.class;
         current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers())) {
          fields.add(field);
        }
      }
    }
    return fields;
  }

  private static Object read(Field field, Object owner) {
    try {
      field.setAccessible(true);
      return field.get(owner);
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      return null;
    }
  }

}
