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

package de.jpx3.intave.block.state;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.netty.WirePacket;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BlockUpdate;
import de.jpx3.intave.share.MaterialAndVariant;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Material;

public final class BlockStateMaterialTests extends IntegrationTests {
  public BlockStateMaterialTests() {
    super("BS");
  }

  public static Iterable<Material> blockMaterials() {
    return MaterialSearch.filterBy(material -> material.isBlock() && !material.name().startsWith("LEGACY_"));
  }

  @Test(testCode = "default", severity = Severity.ERROR, parameters = "blockMaterials")
  public void resolvesDefaultState(Material material) {
    assertResolves(material, WrappedBlockData.createData(material));
  }

  @Test(testCode = "stairs", severity = Severity.ERROR)
  public void resolvesStairVariants() {
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      Material stairs = Material.valueOf("OAK_STAIRS");
      for (int id : BlockVariantRegister.variantIdsOf(stairs)) {
        assertResolves(stairs, WrappedBlockData.fromHandle(BlockVariantRegister.rawVariantOf(stairs, id)));
      }
    } else {
      Material stairs = Material.valueOf("WOOD_STAIRS");
      for (int metadata = 0; metadata < 8; metadata++) {
        assertResolves(stairs, WrappedBlockData.createData(stairs, metadata));
      }
    }
  }

  private void assertResolves(Material material, WrappedBlockData state) {
    PacketContainer packet = new PacketContainer(PacketType.Play.Server.BLOCK_CHANGE);
    packet.getBlockPositionModifier().write(0, new BlockPosition(0, 0, 0));
    packet.getBlockData().write(0, state);
    ByteBuf payload = MinecraftVersions.VER1_20_5.atOrAbove()
      ? (ByteBuf) packet.serializeToBuffer() : Unpooled.wrappedBuffer(WirePacket.bytesFromPacket(packet));
    try {
      BlockUpdate update = (MinecraftVersions.VER1_14_0.atOrAbove()
        ? BlockUpdate.STREAM_CODEC : BlockUpdate.LEGACY_STREAM_CODEC).decode(payload);
      assertFalse(payload.isReadable());
      MaterialAndVariant expected = new MaterialAndVariant(material, BlockVariantNativeAccess.variantAccess(state));
      MaterialAndVariant resolved = update.resolve().orElseThrow(() ->
        new AssertionError("No material for " + material + " at wire state " + update.stateId()));
      assertEquals(expected, resolved);
    } finally {
      payload.release();
    }
  }
}
