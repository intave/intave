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

package de.jpx3.intave.module.tracker.block;

import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.state.BlockStates;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.module.feedback.NativeFeedbackTestSupport;
import de.jpx3.intave.module.feedback.PendingCountingFeedbackObserver;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.mutable.*;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.block.BlockBreakAckWirePacket;
import de.jpx3.intave.packet.wire.block.BlockChangedAckWirePacket;
import de.jpx3.intave.share.*;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static de.jpx3.intave.packet.contract.PacketType.Client.*;
import static de.jpx3.intave.packet.contract.PacketType.Server.*;
import static org.junit.jupiter.api.Assertions.*;

final class BlockUpdateTrackerTest {
  private NativeFeedbackTestSupport feedback;
  private final List<Player> players = new ArrayList<>();
  private Map<Material, Map<Integer, BlockVariant>> variants;
  private Map<Material, Map<Integer, BlockVariant>> savedVariants;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws Exception {
    feedback = new NativeFeedbackTestSupport();
    feedback.linker.linkSubscriptionsIn(new BlockUpdateTracker());
    var variantsField = BlockVariantRegister.class.getDeclaredField("blockVariants");
    variantsField.setAccessible(true);
    variants = (Map<Material, Map<Integer, BlockVariant>>) variantsField.get(null);
    savedVariants = new HashMap<>(variants);
    BlockVariant simpleVariant = (BlockVariant) Proxy.newProxyInstance(BlockVariant.class.getClassLoader(),
      new Class<?>[]{BlockVariant.class}, (proxy, method, args) -> method.getName().equals("properties") ? Map.of() : null);
    variants.put(Material.STONE, Map.of(42, simpleVariant));
    variants.put(Material.valueOf("GRANITE"), Map.of(43, simpleVariant));
    BlockStates.invalidateMaterialAndVariantCache();
  }

  @AfterEach
  void tearDown() throws Exception {
    players.forEach(UserRepository::unregisterUser);
    variants.clear();
    variants.putAll(savedVariants);
    BlockStates.invalidateMaterialAndVariantCache();
    feedback.close();
  }

  @Test
  void nearbyChangesWaitForFeedbackAndCaptureTheSentStateAcrossVersions() {
    for (int protocol : new int[]{47, 107, 340, 393, 498, 754, 758, 759, 764, 777}) {
      Harness h = client(protocol);
      var packet = h.single(1, "granite");
      h.send(BLOCK_CHANGE, packet, false);
      assertTrue(h.calls.isEmpty());
      assertEquals(1, h.user.meta().connection().pendingBlockUpdates.pending());
      packet.setBlockUpdate(h.update(20, "stone"));
      h.acknowledge();
      assertEquals(List.of("unlockOverride", "override", "invalidateCacheAround"), h.mutations());
      var update = h.call("override");
      assertEquals(1, update.get(1));
      assertEquals(Material.valueOf("GRANITE"), update.get(4));
      assertEquals(43, update.get(5)); // The server's variant index, independent of the wire ID.
      assertEquals(0, h.user.meta().connection().pendingBlockUpdates.pending());
    }
  }

  @Test
  void resolvesModernWireStateToTheServersVariantIndex() {
    Harness h = client(47);
    h.send(BLOCK_CHANGE, h.single(20, "stone"), false);
    assertEquals(42, h.call("override").get(5));
    assertTrue(h.pending.isEmpty());
  }

  @Test
  void distantChangesApplyImmediatelyButAMixedBatchWaitsTogether() {
    for (int protocol : new int[]{47, 754, 777}) {
      Harness h = client(protocol);
      h.send(BLOCK_CHANGE, h.single(8, "stone"), false);
      assertTrue(h.pending.isEmpty());
      assertEquals(1, h.count("override"));
      h.calls.clear();
      MutableMultiBlockChangePacket packet = WirePackets.createPlayPacket(MULTI_BLOCK_CHANGE, protocol);
      packet.setBlockUpdates(List.of(h.update(1, "stone"), h.update(12, "granite")));
      h.send(MULTI_BLOCK_CHANGE, packet, false);
      assertTrue(h.calls.isEmpty());
      packet.setBlockUpdates(List.of(h.update(4, "stone")));
      h.acknowledge();
      assertEquals(2, h.count("override"));
      assertEquals(0, h.user.meta().connection().pendingBlockUpdates.pending());
    }
  }

  @Test
  void olderDigAcknowledgementsStillDeliverBlockStates() {
    for (int protocol : new int[]{498, 578, 754, 758}) {
      Harness h = client(protocol);
      h.send(BLOCK_BREAK_ACK, new BlockBreakAckWirePacket(h.update(1, "stone"), PlayerDigAction.STOP_DESTROY_BLOCK, false), false);
      assertTrue(h.calls.isEmpty());
      h.acknowledge();
      assertEquals(1, h.count("override"));
    }
  }

  @Test
  void speculationAndSequenceAcknowledgementsKeepTheirOrdering() {
    for (int protocol : new int[]{758, 759, 760, 777}) {
      Harness h = client(protocol);
      h.speculative = true;
      h.send(BLOCK_CHANGE, h.single(1, "stone"), false);
      h.acknowledge();
      assertEquals(protocol >= 760 ? 1 : 0, h.count("updateClientSpeculationValue"));
      assertEquals(protocol >= 760 ? 0 : 1, h.count("override"));
      if (protocol < 759) continue;
      var ack = new BlockChangedAckWirePacket(12);
      h.send(BLOCK_CHANGED_ACK, ack, false);
      ack.setSequenceNumber(90);
      assertEquals(0, h.count("moveClientSpeculationsToOverride"));
      h.acknowledge();
      assertEquals(12, h.call("moveClientSpeculationsToOverride").get(1));
    }
  }

  @Test
  void chunkConfirmationsUseCapturedCoordinatesAndCannotResurrectUnloadedChunks() {
    for (int protocol : new int[]{107, 758, 777}) {
      Harness h = client(protocol);
      var connection = h.user.meta().connection();
      var chunk = h.chunk(1, -1);
      h.send(MAP_CHUNK, chunk, false);
      assertFalse(connection.hasClientChunk(1, -1));
      assertEquals(0, h.count("invalidateOverridesInBounds"));
      chunk.setChunkCoordinates(0, 8, 8);
      h.acknowledge();
      assertTrue(connection.hasClientChunk(1, -1));
      assertEquals(List.of(16, 32, -16, 0), h.call("invalidateOverridesInBounds"));
      h.send(MAP_CHUNK, h.chunk(2, 0), false);
      MutableUnloadChunkPacket unload = WirePackets.createPlayPacket(UNLOAD_CHUNK, protocol);
      unload.setChunkCoordinates(0, 2, 0);
      h.send(UNLOAD_CHUNK, unload, false);
      h.acknowledge();
      assertFalse(connection.hasClientChunk(2, 0));
    }
  }

  @Test
  void bulkChunksConfirmTogetherAndEmptyBulkDoesNothing() {
    Harness h = client(47);
    MutableMapChunkBulkPacket packet = WirePackets.createPlayPacket(MAP_CHUNK_BULK, 47);
    h.send(MAP_CHUNK_BULK, packet, false);
    assertTrue(h.pending.isEmpty());
    var empty = new ChunkData(List.of(), new int[256]);
    packet.setChunks(false, List.of(new ChunkBulkData(0, 0, 0, empty), new ChunkBulkData(10, 10, 0, empty)));
    h.send(MAP_CHUNK_BULK, packet, false);
    assertEquals(1, h.pending.size());
    packet.setChunks(false, List.of());
    h.acknowledge();
    assertTrue(h.user.meta().connection().hasClientChunk(0, 0));
    assertTrue(h.user.meta().connection().hasClientChunk(10, 10));
    assertEquals(2, h.count("invalidateOverridesInBounds"));
  }

  @Test
  void distantAndOverflowChunksAppendToTheNextFeedback() {
    for (boolean overflow : new boolean[]{false, true}) {
      Harness h = client(777);
      if (overflow) h.user.meta().connection().transactionPacketCounter = 151;
      int x = overflow ? 1 : 20;
      h.send(MAP_CHUNK, h.chunk(x, 0), false);
      assertTrue(h.pending.isEmpty());
      assertFalse(h.user.meta().connection().hasClientChunk(x, 0));
      h.send(BLOCK_CHANGED_ACK, new BlockChangedAckWirePacket(1), false);
      h.acknowledge();
      assertTrue(h.user.meta().connection().hasClientChunk(x, 0));
      assertEquals(1, h.count("invalidateOverridesInBounds"));
    }
  }

  @Test
  void distantChunksWithOverridesRequestTheirOwnFeedback() {
    Harness h = client(777);
    h.hasOverrides = true;
    h.send(MAP_CHUNK, h.chunk(20, 0), false);
    assertEquals(1, h.pending.size());
    h.acknowledge();
    assertTrue(h.user.meta().connection().hasClientChunk(20, 0));
  }

  @Test
  void cancelledPacketsNeverChangeBlocksOrChunks() {
    Harness h = client(777);
    h.send(BLOCK_CHANGE, h.single(1, "stone"), true);
    h.send(BLOCK_CHANGED_ACK, new BlockChangedAckWirePacket(8), true);
    h.send(MAP_CHUNK, h.chunk(0, 0), true);
    h.user.meta().connection().pendingClientChunkLoad(1, 0).run();
    MutableUnloadChunkPacket unload = WirePackets.createPlayPacket(UNLOAD_CHUNK, 777);
    unload.setChunkCoordinates(0, 1, 0);
    h.send(UNLOAD_CHUNK, unload, true);
    assertTrue(h.user.meta().connection().hasClientChunk(1, 0));
    assertFalse(h.user.meta().connection().hasClientChunk(0, 0));
    assertTrue(h.pending.isEmpty());
    assertTrue(h.calls.isEmpty());
    assertEquals(0, h.user.meta().connection().pendingBlockUpdates.pending());
  }

  @Test
  void interactionGuardOnlyChecksBlockTargetsAndKeepsItsDistanceBoundary() {
    for (int protocol : new int[]{47, 107, 758, 777}) {
      Harness h = client(protocol);
      for (PlayerDigAction action : PlayerDigAction.values()) {
        if (action.ordinal() > 6) continue;
        MutableBlockDigPacket dig = WirePackets.createPlayPacket(BLOCK_DIG, protocol);
        dig.setAction(action);
        dig.setBlockPosition(new BlockPosition(17, 64, 0));
        assertEquals(action.ordinal() <= 2, h.receive(BLOCK_DIG, dig));
        dig.setBlockPosition(new BlockPosition(16, 64, 0));
        assertFalse(h.receive(BLOCK_DIG, dig));
      }
      MutableBlockInteractionPacket place = WirePackets.createPlayPacket(USE_ITEM_ON, protocol);
      place.setDirection(Direction.UP);
      place.setBlockPosition(new BlockPosition(17, 64, 0));
      assertTrue(h.receive(USE_ITEM_ON, place));
      place.setBlockPosition(new BlockPosition(16, 64, 0));
      assertFalse(h.receive(USE_ITEM_ON, place));
      if (protocol == 47) {
        place.clearDirection(); // Legacy face 255: use the held item without a block target.
        place.setBlockPosition(new BlockPosition(-1, -1, -1));
        assertFalse(h.receive(USE_ITEM_ON, place));
      } else {
        assertFalse(h.receive(USE_ITEM, WirePackets.createPlayPacket(USE_ITEM, protocol)));
      }
    }
  }

  private Harness client(int protocol) {
    Harness h = new Harness(protocol);
    players.add(h.user.player());
    UserRepository.manuallyRegisterUser(h.user.player(), h.user);
    return h;
  }

  private static final class Harness {
    private final User user;
    private final List<Call> calls = new ArrayList<>();
    private final Queue<TypedPacketContract> pending = new ArrayDeque<>();
    private boolean speculative, hasOverrides;

    private Harness(int protocol) {
      BlockCache cache = (BlockCache) Proxy.newProxyInstance(BlockCache.class.getClassLoader(), new Class<?>[]{BlockCache.class},
        (proxy, method, args) -> {
          calls.add(new Call(method.getName(), args == null ? List.of() : Arrays.asList(args)));
          return switch (method.getName()) {
            case "updateClientSpeculationValue" -> speculative;
            case "hasOverridesInBounds" -> hasOverrides;
            default -> method.getReturnType() == boolean.class ? false : null;
          };
        });
      var world = FakeWorldFactory.createWorld((name, args) -> null);
      UUID id = UUID.randomUUID();
      var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
        case "getWorld" -> world;
        case "getLocation" -> new Location(world, 0, 64, 0);
        case "getUniqueId" -> id;
        default -> null;
      });
      user = UserFactory.createTestUserFor(player, (ignored, method) -> switch (method) {
        case "protocolVersion" -> protocol;
        case "blockCache" -> cache;
        default -> null;
      });
      user.meta().connection().pendingBlockUpdates = new PendingCountingFeedbackObserver();
      user.meta().movement().setPosition(new Position(0, 64, 0));
      user.meta().movement().setLastPosition(0, 64, 0);
      user.meta().movement().setVerifiedLastPosition(new Position(0, 64, 0), "test");
      calls.clear();
    }

    private BlockUpdate update(int x, String name) {
      var state = BlockStates.translate(47, name.equals("granite") ? 17 : 16, user.protocolVersion());
      return new BlockUpdate(new BlockPosition(x, 64, 0), state.id());
    }

    private MutableSingleBlockChangePacket single(int x, String name) {
      MutableSingleBlockChangePacket packet = WirePackets.createPlayPacket(BLOCK_CHANGE, user.protocolVersion());
      packet.setBlockUpdate(update(x, name));
      return packet;
    }

    private MutableMapChunkPacket chunk(int x, int z) {
      MutableMapChunkPacket packet = WirePackets.createPlayPacket(MAP_CHUNK, user.protocolVersion());
      packet.setChunkCoordinates(0, x, z);
      return packet;
    }

    private void send(PacketType.Server type, PacketContract packet, boolean cancelled) {
      var event = new PacketSendEvent(user, type, packet);
      event.setCancelled(cancelled);
      var sent = PacketEventBus.dispatch(event);
      if (cancelled) assertTrue(sent.isEmpty());
      else {
        assertSame(packet, sent.getFirst().contract());
        pending.addAll(sent.subList(1, sent.size()));
      }
    }

    private boolean receive(PacketType.Client type, PacketContract packet) {
      var event = new PacketReceiveEvent(user, type, packet);
      PacketEventBus.dispatch(event);
      return event.isCancelled();
    }

    private void acknowledge() {
      assertFalse(pending.isEmpty());
      var reply = NativeFeedbackTestSupport.acknowledgement(user, pending.remove());
      PacketEventBus.dispatch(reply);
      assertTrue(reply.isCancelled());
    }

    private long count(String name) { return calls.stream().filter(call -> call.name.equals(name)).count(); }

    private List<Object> call(String name) {
      return calls.stream().filter(call -> call.name.equals(name)).findFirst().orElseThrow().args;
    }

    private List<String> mutations() {
      return calls.stream().map(Call::name).filter(name -> !name.equals("updateClientSpeculationValue")).toList();
    }
  }

  private record Call(String name, List<Object> args) {}
}
