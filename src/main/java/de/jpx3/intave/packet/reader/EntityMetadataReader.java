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

package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.BlockPosition;

import java.util.*;
import java.util.stream.Collectors;

// Inspired from work by lukas81298
// See https://pastebin.com/EuprZGHe for the blueprint
public final class EntityMetadataReader extends EntityReader {
  private static final boolean LEGACY_MODE = !MinecraftVersions.VER1_19_3.atOrAbove();

  public @Nullable Byte livingFlags() {
    if (!MinecraftVersions.VER1_9_0.atOrAbove()) {
      return null;
    }
    Object value = fetchRaw(livingFlagsIndex());
    return value instanceof Number ? ((Number) value).byteValue() : null;
  }

  static int livingFlagsIndex() {
    return MinecraftVersions.VER1_17_0.atOrAbove() ? 8
      : MinecraftVersions.VER1_14_0.atOrAbove() ? 7
      : MinecraftVersions.VER1_10_0.atOrAbove() ? 6 : 5;
  }

  public Optional<BlockPosition> bedPosition() {
    // on versions below 1.14 the bed position is not stored in metadata, it arrives in a separate use_bed packet
    if (!MinecraftVersions.VER1_14_0.atOrAbove()) {
      return Optional.empty();
    }
    int index;
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      index = 14;
    } else if (MinecraftVersions.VER1_15_0.atOrAbove()) {
      index = 13;
    } else {
      index = 12;
    }
    try {
      Object raw = fetchRaw(index);
      //noinspection OptionalAssignedToNull
      if (raw == null) {
        return Optional.empty();
      }
      // the value type depends on the server version, so check that it is an optional before casting
      if (!(raw instanceof Optional)) {
        return Optional.empty();
      }
      Optional<?> rawObject = (Optional<?>) raw;
      return rawObject.map(o -> BlockPosition.fromNative(o).toBlockPosition());
    } catch (Exception e) {
      System.err.println("Failed to read bed position from entity metadata, returning empty");
      System.err.println("Required index: " + index);
      System.err.println("Target entityid: " + entityId());
      System.err.println("Entity metadata: " + entryMap());
      e.printStackTrace();
      return Optional.empty();
    }
  }

  public Object fetchRaw(int requiredIndex) {
    if (LEGACY_MODE) {
      StructureModifier<List<WrappedWatchableObject>> modifier = packet().getWatchableCollectionModifier();
      List<WrappedWatchableObject> lists = modifier.read(0);
      if (lists == null || lists.isEmpty()) {
        return null;
      }
      for (WrappedWatchableObject watchable : lists) {
        if (watchable.getIndex() == requiredIndex) {
          return watchable.getRawValue();
        }
      }
    } else {
      List<WrappedDataValue> values = metadataValues();
      if (values == null || values.isEmpty()) {
        return null;
      }
      for (WrappedDataValue value : values) {
        if (value.getIndex() == requiredIndex) {
          return value.getRawValue();
        }
      }
    }
    return null;
  }

  public void setRaw(int requiredIndex, Object newValue) {
    if (LEGACY_MODE) {
      StructureModifier<List<WrappedWatchableObject>> modifier = packet().getWatchableCollectionModifier();
      List<WrappedWatchableObject> lists = modifier.read(0);
      if (lists == null || lists.isEmpty()) {
        return;
      }
      for (int i = 0; i < lists.size(); i++) {
        WrappedWatchableObject watchable = lists.get(i);
        if (watchable.getIndex() == requiredIndex) {
          WrappedWatchableObject newWatchable = new WrappedWatchableObject(
            watchable.getWatcherObject(),
            newValue
          );
          lists.set(i, newWatchable);
          modifier.write(0, lists);
          return;
        }
      }
    } else {
      StructureModifier<List<WrappedDataValue>> modifier = packet().getDataValueCollectionModifier();
      List<WrappedDataValue> values = modifier.read(0);
      if (values == null || values.isEmpty()) {
        return;
      }
      for (int i = 0; i < values.size(); i++) {
        WrappedDataValue value = values.get(i);
        if (value.getIndex() == requiredIndex) {
          WrappedDataValue newValueWrapper = new WrappedDataValue(
            value.getIndex(),
            value.getSerializer(),
            newValue
          );
          values.set(i, newValueWrapper);
          modifier.write(0, values);
          return;
        }
      }
    }
  }

  public Map<Integer, Object> entryMap() {
    if (LEGACY_MODE) {
      StructureModifier<List<WrappedWatchableObject>> modifier = packet().getWatchableCollectionModifier();
      List<WrappedWatchableObject> lists = modifier.read(0);
      if (lists == null || lists.isEmpty()) {
        return Collections.emptyMap();
      }
      return lists.stream().collect(Collectors.toMap(
        WrappedWatchableObject::getIndex,
        WrappedWatchableObject::getRawValue
      ));
    } else {
      List<WrappedDataValue> values = metadataValues();
      if (values == null || values.isEmpty()) {
        return Collections.emptyMap();
      }
      return values.stream().collect(Collectors.toMap(
        WrappedDataValue::getIndex,
        WrappedDataValue::getRawValue
      ));
    }
  }

  public List<Object> values() {
    if (LEGACY_MODE) {
      StructureModifier<List<WrappedWatchableObject>> modifier = packet().getWatchableCollectionModifier();
      List<WrappedWatchableObject> lists = modifier.read(0);
      if (lists == null || lists.isEmpty()) {
        return new ArrayList<>();
      }
      return lists.stream().map(WrappedWatchableObject::getRawValue).collect(Collectors.toList());
    } else {
      List<WrappedDataValue> values = metadataValues();
      if (values == null || values.isEmpty()) {
        return new ArrayList<>();
      }
      return values.stream().map(WrappedDataValue::getRawValue).collect(Collectors.toList());
    }
  }

  @Deprecated
  public List<WrappedWatchableObject> legacyMetadataObjects() {
    if (LEGACY_MODE) {
      return packet().getWatchableCollectionModifier().read(0);
    }
    List<WrappedWatchableObject> list = new ArrayList<>();
    for (WrappedDataValue value : metadataValues()) {
      list.add(convertDataValueToWatchable(value));
    }
    return list;
  }

  @Deprecated
  public void setLegacyMetadataObjects(List<WrappedWatchableObject> watchables) {
    if (LEGACY_MODE) {
      packet().getWatchableCollectionModifier().write(0, watchables);
    } else {
      packet().getDataValueCollectionModifier().write(0, watchables.stream()
        .map(EntityMetadataReader::convertWatchableToDataValue)
        .collect(Collectors.toList()));
    }
  }

  private List<WrappedDataValue> metadataValues() {
    return packet().getDataValueCollectionModifier().read(0);
  }

  private static WrappedWatchableObject convertDataValueToWatchable(WrappedDataValue value) {
    WrappedDataWatcherObject dataWatcherObject = new WrappedDataWatcherObject(
      value.getIndex(), value.getSerializer()
    );
    return new WrappedWatchableObject(dataWatcherObject, value.getValue());
  }

  private static WrappedDataValue convertWatchableToDataValue(WrappedWatchableObject object) {
    return new WrappedDataValue(
      object.getIndex(),
      object.getWatcherObject().getSerializer(),
      object.getValue()
    );
  }
}
