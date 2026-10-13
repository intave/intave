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

package de.jpx3.intave.packet.plib.converter;

import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.BlockPosition;

import java.lang.reflect.Constructor;

public final class BlockPositionConverter {
  private static final ThreadLocal<EquivalentConverter<BlockPosition>> internalConverter =
    ThreadLocal.withInitial(BlockPositionConverter::newConverter);

  private static Constructor<?> blockPositionConstructor;
  private static StructureModifier<Integer> intModifier;
  private static StructureModifier<Long> longModifier;

  public static EquivalentConverter<BlockPosition> threadConverter() {
    return internalConverter.get();
  }

  private static EquivalentConverter<BlockPosition> newConverter() {
    return new EquivalentConverter<BlockPosition>() {
      public Object getGeneric(BlockPosition specific) {
        if (blockPositionConstructor == null) {
          try {
            blockPositionConstructor = MinecraftReflection.getBlockPositionClass().getConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE);
          } catch (Exception var4) {
            throw new RuntimeException("Cannot find block position constructor.", var4);
          }
        }
        try {
          return blockPositionConstructor.newInstance(specific.getX(), specific.getY(), specific.getZ());
        } catch (Exception var3) {
          throw new RuntimeException("Cannot construct ", var3);
        }
      }

      public BlockPosition getSpecific(Object generic) {
        if (MinecraftReflection.isBlockPosition(generic)) {
          if (intModifier == null) {
            //noinspection unchecked
            intModifier = (new StructureModifier(generic.getClass(), null, false)).withType(Integer.TYPE);
            if (intModifier.size() < 3) {
              throw new IllegalStateException("Cannot read class " + generic.getClass() + " for its integer fields.");
            }
          }
          if (intModifier.size() >= 3) {
            try {
              StructureModifier<Integer> instance = intModifier.withTarget(generic);
              return new BlockPosition(
                instance.read(0), instance.read(1), instance.read(2)
              );
            } catch (FieldAccessException var4) {
              throw new RuntimeException("Field access error.", var4);
            }
          }
        }

        return null;
      }

      public Class<BlockPosition> getSpecificType() {
        return BlockPosition.class;
      }
    };
  }
}
