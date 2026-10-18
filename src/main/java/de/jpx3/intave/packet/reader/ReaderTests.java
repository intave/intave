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

import de.jpx3.intave.share.GameStateChangeType;
import de.jpx3.intave.share.EntityUseAction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.ScoreboardObjectiveAction;
import de.jpx3.intave.share.ScoreboardTeamAction;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.plib.converter.PlayerHandConverter;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.BlockCaches;
import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.check.world.interaction.Interaction;
import de.jpx3.intave.check.world.interaction.InteractionType;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.module.dispatch.AttackDispatcher;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.linker.packet.ForwardingPacketAdapter;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.entity.EntityCollisionDisabler;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.packet.plib.converter.PosMoveRotConverter;
import de.jpx3.intave.packet.plib.converter.PositionAndRotationConverter;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionAndRotation;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.share.Teleport;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.version.ServerProtocolVersion;
import de.jpx3.intave.share.PlayerDigAction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class ReaderTests extends IntegrationTests {
  private static final Set<PacketType> EXCLUDED_TYPES = new HashSet<>();
  static {
    EXCLUDED_TYPES.add(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
  }

  public ReaderTests() {
    super("PR");
  }

  @Test(testCode = "recorded-packet-actions", severity = Severity.ERROR)
  public void testRecordedPacketActions() {
    for (PlayerHand hand : PlayerHand.values()) {
      if (MinecraftVersions.VER1_9_0.below() && hand == PlayerHand.OFF_HAND) continue;
      // 26.3 removed the serverbound swing packet; the other action packets still exist.
      if (MinecraftVersions.VER26_3.below()) {
        PacketContainer swing = new PacketContainer(PacketType.Play.Client.ARM_ANIMATION);
        if (MinecraftVersions.VER1_9_0.atOrAbove()) swing.getHands().write(0, PlayerHandConverter.toProtocolLib(hand));
        try (ArmAnimationReader reader = PacketReaders.readerOf(swing)) {
          assertEquals(hand, reader.hand());
        }
      }
      PacketContainer use = new PacketContainer(PacketType.Play.Client.BLOCK_PLACE);
      if (MinecraftVersions.VER1_9_0.atOrAbove()) {
        use.getHands().write(0, PlayerHandConverter.toProtocolLib(hand));
      } else {
        use.getIntegers().write(0, 255);
      }
      try (BlockInteractionReader reader = PacketReaders.readerOf(use)) {
        assertEquals(hand, reader.hand());
        assertTrue(reader.isItemUseRequest());
      }
      if (MinecraftVersions.VER1_9_0.below()) {
        use.getIntegers().write(0, 1);
        try (BlockInteractionReader reader = PacketReaders.readerOf(use)) {
          assertFalse(reader.isItemUseRequest());
        }
      }
    }

    PacketContainer status = new PacketContainer(PacketType.Play.Server.ENTITY_STATUS);
    for (byte value : new byte[]{9, 35, 2}) {
      status.getBytes().write(0, value);
      try (EntityStatusReader reader = PacketReaders.readerOf(status)) {
        assertEquals(value == 35, reader.indicatesTotemActivation());
        assertEquals(value == 9, reader.indicatesItemUseFinished());
      }
    }
  }

  @Test(testCode = "recorded-abilities", severity = Severity.ERROR)
  public void testRecordedAbilities() {
    PacketContainer packet = new PacketContainer(PacketType.Play.Server.ABILITIES);
    for (boolean flying : new boolean[]{false, true}) {
      packet.getBooleans().write(1, flying).write(2, !flying);
      try (AbilityOutReader reader = PacketReaders.readerOf(packet)) {
        assertEquals(flying, reader.flying());
        assertEquals(!flying, reader.flyingAllowed());
      }
    }
  }

  @Test(testCode = "legacy-entity-use", severity = Severity.ERROR)
  public void testLegacyEntityUse() {
    if (MinecraftVersions.VER1_17_0.atOrAbove()) return;

    org.bukkit.util.Vector hitPosition = new org.bukkit.util.Vector(0.25, 1.5, -0.5);
    for (EnumWrappers.EntityUseAction action : Arrays.asList(
      EnumWrappers.EntityUseAction.INTERACT, EnumWrappers.EntityUseAction.INTERACT_AT
    )) {
      for (PlayerHand hand : PlayerHand.values()) {
        if (MinecraftVersions.VER1_9_0.below() && hand == PlayerHand.OFF_HAND) continue;

        PacketContainer packet = new PacketContainer(PacketType.Play.Client.USE_ENTITY);
        packet.getEntityUseActions().write(0, action);
        if (MinecraftVersions.VER1_9_0.atOrAbove()) {
          packet.getHands().write(0, PlayerHandConverter.toProtocolLib(hand));
        }
        packet.getVectors().write(0, action == EnumWrappers.EntityUseAction.INTERACT_AT ? hitPosition : null);
        try (EntityUseReader reader = PacketReaders.readerOf(packet)) {
          assertEquals(EntityUseAction.valueOf(action.name()), reader.useAction());
          assertEquals(hand, reader.hand());
          if (action == EnumWrappers.EntityUseAction.INTERACT_AT) {
            assertEquals(hitPosition, reader.hitPosition().convertToBukkitVec());
          } else {
            assertNull(reader.hitPosition());
          }
        }
      }
    }
  }

  @Test(testCode = "player-time", severity = Severity.ERROR)
  public void testNativePlayerTime() throws ReflectiveOperationException {
    Class<?> type = PacketType.Play.Server.UPDATE_TIME.getPacketClass();
    Object handle;
    boolean clocks;
    try {
      handle = type.getConstructor(long.class, Map.class).newInstance(123L, Collections.emptyMap());
      clocks = true;
    } catch (NoSuchMethodException legacy) {
      handle = type.getConstructor(long.class, long.class, boolean.class).newInstance(123L, 18000L, false);
      clocks = false;
    }
    PacketContainer packet = new PacketContainer(PacketType.Play.Server.UPDATE_TIME, handle);
    try (TimeUpdateReader reader = PacketReaders.readerOf(packet)) {
      TimeUpdateReader.Update update = reader.update();
      ac.intave.samples.event.TimeEvent time = update.event();
      assertEquals(123L, update.gameTime());
      if (clocks) {
        assertNull(time.time());
        assertTrue(time.clocks().isEmpty());
      } else {
        assertEquals(18000L, time.time());
        assertEquals(false, time.ticking());
      }
    }
  }

  @Test(testCode = "world-weather", severity = Severity.ERROR)
  public void testNativeWeather() {
    PacketContainer packet = new PacketContainer(PacketType.Play.Server.GAME_STATE_CHANGE);
    for (GameStateChangeType state : Arrays.asList(
      GameStateChangeType.END_RAIN, GameStateChangeType.BEGIN_RAIN,
      GameStateChangeType.RAIN_LEVEL_CHANGE, GameStateChangeType.THUNDER_LEVEL_CHANGE
    )) {
      if (packet.getIntegers().size() > 0) {
        packet.getIntegers().write(0, state.id());
      } else {
        packet.getGameStateIDs().write(0, state.id());
      }
      packet.getFloat().write(0, 0.625F);
      try (GameStateChangeReader reader = PacketReaders.readerOf(packet)) {
        assertEquals(state, reader.type());
        assertEquals(0.625F, reader.value());
      }
    }
  }

  @Test(testCode = "native-scoreboard", severity = Severity.ERROR)
  public void testNativeScoreboardPackets() throws ReflectiveOperationException {
    org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
    org.bukkit.scoreboard.Objective objective = board.registerNewObjective("reader", "dummy");
    objective.setDisplayName("Reader title");
    Object nativeObjective = scoreboardHandle(objective);
    Class<?> objectiveType = PacketType.Play.Server.SCOREBOARD_OBJECTIVE.getPacketClass();
    for (int action : new int[]{0, 1, 2}) {
      Object handle = objectiveType.getConstructor(nativeObjective.getClass(), int.class)
        .newInstance(nativeObjective, action);
      try (ScoreboardReader reader = PacketReaders.readerOf(new PacketContainer(PacketType.Play.Server.SCOREBOARD_OBJECTIVE, handle))) {
        ScoreboardReader.Objective decoded = reader.objective();
        assertEquals("reader", decoded.name);
        assertEquals(ScoreboardObjectiveAction.byId(action), decoded.action);
        if (action == 1) {
          assertNull(decoded.title);
        } else {
          assertEquals("Reader title", plainScoreboardText(decoded.title));
        }
      }
    }

    Class<?> displayType = PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE.getPacketClass();
    boolean checkedDisplay = false;
    for (java.lang.reflect.Constructor<?> constructor : displayType.getConstructors()) {
      Class<?>[] parameters = constructor.getParameterTypes();
      if (parameters.length != 2 || !parameters[1].isInstance(nativeObjective)) continue;
      checkedDisplay = true;
      for (int slot : new int[]{1, 15}) {
        Object nativeSlot = parameters[0].isEnum() ? parameters[0].getEnumConstants()[slot] : slot;
        Object handle = constructor.newInstance(nativeSlot, nativeObjective);
        try (ScoreboardReader reader = PacketReaders.readerOf(new PacketContainer(PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE, handle))) {
          assertEquals(slot == 1 ? "sidebar" : "team_red", reader.display().slot);
          assertEquals("reader", reader.display().objective);
        }
      }
    }
    assertTrue(checkedDisplay);

    org.bukkit.scoreboard.Team team = board.registerNewTeam("readers");
    team.setPrefix("pre ");
    team.setSuffix(" post");
    team.addEntry("Entry");
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      java.lang.reflect.Method setColor = team.getClass().getMethod("setColor", org.bukkit.ChatColor.class);
      setColor.setAccessible(true);
      setColor.invoke(team, org.bukkit.ChatColor.RED);
    }
    Object nativeTeam = scoreboardHandle(team);
    Class<?> teamType = PacketType.Play.Server.SCOREBOARD_TEAM.getPacketClass();
    for (int action : new int[]{0, 1, 2, 3, 4}) {
      Object handle = null;
      if (MinecraftVersions.VER1_17_0.below()) {
        handle = action <= 2
          ? teamType.getConstructor(nativeTeam.getClass(), int.class).newInstance(nativeTeam, action)
          : teamType.getConstructor(nativeTeam.getClass(), Collection.class, int.class)
            .newInstance(nativeTeam, Collections.singletonList("Entry"), action);
      } else {
        for (java.lang.reflect.Method method : teamType.getDeclaredMethods()) {
          Class<?>[] parameters = method.getParameterTypes();
          if (!java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.getReturnType() != teamType
            || parameters.length == 0 || !parameters[0].isInstance(nativeTeam)) continue;
          if (action == 1 && parameters.length == 1) {
            handle = method.invoke(null, nativeTeam);
          } else if ((action == 0 || action == 2) && parameters.length == 2 && parameters[1] == boolean.class) {
            handle = method.invoke(null, nativeTeam, action == 0);
          } else if (action >= 3 && parameters.length == 3 && parameters[1] == String.class && parameters[2].isEnum()) {
            handle = method.invoke(null, nativeTeam, "Entry", parameters[2].getEnumConstants()[action - 3]);
          }
        }
      }
      assertTrue(handle != null);
      try (ScoreboardReader reader = PacketReaders.readerOf(new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM, handle))) {
        assertTeamCollisionUpdate(reader, action);
        ScoreboardReader.Team decoded = reader.team();
        assertEquals("readers", decoded.name);
        assertEquals(ScoreboardTeamAction.byId(action), decoded.action);
        if (action == 0 || action == 2) {
          assertEquals("pre ", plainScoreboardText(decoded.prefix));
          assertEquals(" post", plainScoreboardText(decoded.suffix));
          assertEquals(MinecraftVersions.VER1_13_0.atOrAbove() ? "red" : "reset", decoded.color);
        }
        if (action == 0 || action >= 3) assertEquals(Collections.singletonList("Entry"), decoded.entries);
      }
    }

    PacketContainer score;
    if (MinecraftVersions.VER1_20_3.atOrAbove()) {
      score = nativeScorePacket("Visible entry");
    } else {
      score = new PacketContainer(PacketType.Play.Server.SCOREBOARD_SCORE);
      score.getStrings().write(0, "Entry").write(1, "reader");
      score.getIntegers().write(0, 42);
      score.getScoreboardActions().write(0, EnumWrappers.ScoreboardAction.CHANGE);
    }
    try (ScoreboardReader reader = PacketReaders.readerOf(score)) {
      ScoreboardReader.Score decoded = reader.score();
      assertEquals("Entry", decoded.owner);
      assertEquals("reader", decoded.objective);
      assertEquals(42, decoded.value);
      assertFalse(decoded.remove);
      if (MinecraftVersions.VER1_20_3.atOrAbove()) assertEquals("Visible entry", plainScoreboardText(decoded.displayName));
    }
    if (MinecraftVersions.VER1_20_3.atOrAbove()) {
      try (ScoreboardReader reader = PacketReaders.readerOf(nativeScorePacket(null))) {
        ScoreboardReader.Score decoded = reader.score();
        assertEquals("Entry", decoded.owner);
        assertEquals("reader", decoded.objective);
        assertEquals(42, decoded.value);
        assertFalse(decoded.remove);
        assertNull(decoded.displayName);
      }
      Class<?> resetType = PacketType.Play.Server.RESET_SCORE.getPacketClass();
      for (String name : Arrays.asList("reader", null)) {
        Object handle = resetType.getConstructor(String.class, String.class).newInstance("Entry", name);
        try (ScoreboardReader reader = PacketReaders.readerOf(new PacketContainer(PacketType.Play.Server.RESET_SCORE, handle))) {
          assertEquals("Entry", reader.resetScore().owner);
          if (name == null) {
            assertNull(reader.resetScore().objective);
          } else {
            assertEquals(name, reader.resetScore().objective);
          }
          assertTrue(reader.resetScore().remove);
        }
      }
    } else {
      score.getScoreboardActions().write(0, EnumWrappers.ScoreboardAction.REMOVE);
      try (ScoreboardReader reader = PacketReaders.readerOf(score)) {
        assertTrue(reader.score().remove);
      }
    }
  }

  private void assertTeamCollisionUpdate(ScoreboardReader reader, int action) {
    StructureModifier<Object> fields = reader.packet().getModifier();
    List<Object> packetBefore = new ArrayList<>(fields.getValues());
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      Optional<InternalStructure> parameters = reader.packet().getOptionalStructures().read(0);
      if (parameters.isPresent()) fields = parameters.get().getModifier();
    }
    List<Object> before = new ArrayList<>(fields.getValues());
    new EntityCollisionDisabler().receiveScoreboardUpdate(reader);
    List<Object> after = fields.getValues();
    int changed = 0;
    for (int i = 0; i < before.size(); i++) {
      if (!Objects.equals(before.get(i), after.get(i))) {
        changed++;
        // Check the only mutation, independently of the reader's field indexes.
        Object rule = after.get(i);
        assertEquals("never", rule instanceof Enum ? ((Enum<?>) rule).name().toLowerCase(Locale.ROOT) : rule);
        Object previous = before.get(i);
        assertEquals("always", previous instanceof Enum ? ((Enum<?>) previous).name().toLowerCase(Locale.ROOT) : previous);
      }
    }
    assertEquals(MinecraftVersions.VER1_9_0.atOrAbove() && (action == 0 || action == 2) ? 1 : 0, changed);
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      assertEquals(packetBefore, reader.packet().getModifier().getValues());
    }
    // Repeated updates must be harmless.
    new EntityCollisionDisabler().receiveScoreboardUpdate(reader);
    assertEquals(after, fields.getValues());
  }

  private static PacketContainer nativeScorePacket(String displayName) throws ReflectiveOperationException {
    Class<?> scoreType = PacketType.Play.Server.SCOREBOARD_SCORE.getPacketClass();
    Object component = com.comphenix.protocol.wrappers.WrappedChatComponent
      .fromText(displayName == null ? "" : displayName).getHandle();
    for (java.lang.reflect.Constructor<?> constructor : scoreType.getConstructors()) {
      Class<?>[] parameters = constructor.getParameterTypes();
      if (parameters.length != 5 || parameters[0] != String.class
        || parameters[1] != String.class || parameters[2] != int.class) continue;
      Object display = displayName == null ? null : component;
      Object format = null;
      if (parameters[3] == Optional.class && parameters[4] == Optional.class) {
        display = Optional.ofNullable(display);
        format = Optional.empty();
      } else if (!parameters[3].isInstance(component) || parameters[4].isPrimitive()) {
        continue;
      }
      // 1.20.3/4 use nullable fields; 1.20.5+ wrap them in Optional.
      Object handle = constructor.newInstance("Entry", "reader", 42, display, format);
      return new PacketContainer(PacketType.Play.Server.SCOREBOARD_SCORE, handle);
    }
    throw new NoSuchMethodException("Unsupported native scoreboard score constructor: " + scoreType.getName());
  }

  private static Object scoreboardHandle(Object value) throws ReflectiveOperationException {
    try {
      java.lang.reflect.Method method = value.getClass().getDeclaredMethod("getHandle");
      method.setAccessible(true);
      return method.invoke(value);
    } catch (NoSuchMethodException legacyTeam) {
      java.lang.reflect.Field field = value.getClass().getDeclaredField("team");
      field.setAccessible(true);
      return field.get(value);
    }
  }

  private static String plainScoreboardText(String value) {
    return org.bukkit.ChatColor.stripColor(value);
  }

  @Test(testCode = "player-info-null-profile", severity = Severity.ERROR)
  public void testPlayerInfoEntriesWithNullProfiles() throws ReflectiveOperationException {
    if (MinecraftVersions.VER1_19_3.below()) return;

    UUID profileId = UUID.randomUUID();
    Object entry = MinecraftVersion.current().equals(new MinecraftVersion("1.21.11"))
      ? nativePlayerInfoEntry(profileId)
      : new SyntheticPlayerInfoEntry(profileId, null);
    List<PlayerInfoReader.PlayerInfoEntry> entries = PlayerInfoReader.inspectEntries(
      Arrays.asList(entry), true
    );
    assertNotNull(entries);
    assertEquals(profileId, entries.get(0).profileId());

    PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
    packet.getModifier().withType(List.class).write(0, new ArrayList<>(Arrays.asList(entry)));
    try (PlayerInfoReader reader = PacketReaders.readerOf(packet)) {
      List<PlayerInfoReader.PlayerInfoEntry> packetEntries = reader.playerInfoEntries();
      assertNotNull(packetEntries);
      assertEquals(profileId, packetEntries.get(0).profileId());
      assertTrue(reader.writePlayerInfoEntries(packetEntries));
      assertSame(entry, ((List<?>) packet.getModifier().withType(List.class).read(0)).get(0));
    }

    assertNull(PlayerInfoReader.inspectEntries(null, true));
    assertNull(PlayerInfoReader.inspectEntries(Arrays.asList(entry, null), true));
    assertNull(PlayerInfoReader.inspectEntries(Arrays.asList(new SyntheticPlayerInfoEntry(null, null)), true));
  }

  private Object nativePlayerInfoEntry(UUID profileId) throws ReflectiveOperationException {
    Class<?> entryType = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
    return entryType.getConstructor(
      UUID.class, Class.forName("com.mojang.authlib.GameProfile"), boolean.class, int.class,
      Class.forName("net.minecraft.world.level.GameType"), Class.forName("net.minecraft.network.chat.Component"),
      boolean.class, int.class, Class.forName("net.minecraft.network.chat.RemoteChatSession$Data")
    ).newInstance(profileId, null, false, 35, null, null, false, 0, null);
  }

  private static final class SyntheticPlayerInfoEntry {
    private final UUID profileId;
    private final Object profile;

    private SyntheticPlayerInfoEntry(UUID profileId, Object profile) {
      this.profileId = profileId;
      this.profile = profile;
    }
  }

  @Test(testCode = "entity-velocity-native", severity = Severity.ERROR)
  public void testNativeEntityVelocityConversion() throws ReflectiveOperationException {
    if (!MinecraftVersion.current().isAtLeast(new MinecraftVersion("1.21.11"))) return;
    de.jpx3.intave.share.Motion expected = new de.jpx3.intave.share.Motion(0.125D, -0.7D, 1.5D);
    Class<?> vectorType = Class.forName("net.minecraft.world.phys.Vec3");
    Object nativeVector = vectorType.getConstructor(double.class, double.class, double.class)
      .newInstance(expected.motionX(), expected.motionY(), expected.motionZ());
    Object handle = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket")
      .getConstructor(int.class, vectorType).newInstance(42, nativeVector);
    EntityVelocityReader reader = new EntityVelocityReader();
    reader.enter(PacketContainer.fromPacket(handle));
    try (EntityVelocityReader acquired = reader) {
      assertEquals(42, acquired.entityId());
      assertEquals(expected.motionX(), acquired.motionX());
      assertEquals(expected.motionY(), acquired.motionY());
      assertEquals(expected.motionZ(), acquired.motionZ());
      assertEquals(expected, acquired.motion());
    }
  }

  @Test(testCode = "silent-explosion", severity = Severity.ERROR)
  public void testSilentExplosion() {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.EXPLOSION);
    de.jpx3.intave.share.Motion motion = new de.jpx3.intave.share.Motion(0.123456789, -0.7, 0.5);
    boolean modern = MinecraftVersions.VER1_21_2.atOrAbove();
    try (ExplosionReader reader = PacketReaders.readerOf(packet)) {
      reader.setSilentDefaults();
      reader.setMotion(motion);
    }
    try (ExplosionReader reader = PacketReaders.readerOf(packet)) {
      assertEquals(modern ? motion.motionX() : (double) (float) motion.motionX(), reader.motion().motionX());
      assertEquals(modern ? motion.motionY() : (double) (float) motion.motionY(), reader.motion().motionY());
      assertEquals(modern ? motion.motionZ() : (double) (float) motion.motionZ(), reader.motion().motionZ());
      reader.setSilentDefaults();
      assertEquals(modern ? motion.motionY() : (double) (float) motion.motionY(), reader.motion().motionY());
      reader.setMotion(new de.jpx3.intave.share.Motion());
      assertEquals(0.0D, reader.motion().motionY());
    }
    if (modern) {
      assertEquals(-1.0E9D, packet.getVectors().read(0).getY());
      if (packet.getFloat().size() > 0) {
        assertEquals(0.0F, packet.getFloat().read(0));
        assertEquals(0, packet.getIntegers().read(0));
        assertNotNull(packet.getModifier().read(6));
      }
      if (packet.getBooleans().size() > 0) assertEquals(false, packet.getBooleans().read(0));
    } else {
      assertEquals(-1.0E9D, packet.getDoubles().read(1));
      assertEquals(0.0F, packet.getFloat().read(0));
      assertEquals(0, packet.getBlockPositionCollectionModifier().read(0).size());
    }
    if (MinecraftVersions.VER1_20_2.atOrAbove()) {
      for (int i = 0; i < packet.getNewParticles().size(); i++) assertNotNull(packet.getNewParticles().read(i));
    }
  }

  @Test(testCode = "block-ack-routing", severity = Severity.ERROR)
  public void testNativeBlockAcknowledgementRouting() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_2.below()) return;
    Object handle = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket")
      .getConstructor(int.class).newInstance(37);
    PacketContainer packet = PacketContainer.fromPacket(handle);
    try (BlockChangedAckReader reader = PacketReaders.readerOf(packet)) {
      assertEquals(37, reader.sequenceNumber());
    }
    if (!PacketType.fromName("BLOCK_CHANGED_ACK").contains(packet.getType())) {
      fail("Native block acknowledgement resolves to " + packet.getType()
        + " instead of a registered BLOCK_CHANGED_ACK subscription type");
    }

    org.bukkit.World world = Bukkit.getWorlds().get(0);
    org.bukkit.entity.Player player = FakePlayerFactory.createPlayer((name, args) ->
      name.equals("getWorld") ? world : null);
    BlockCache cache = BlockCaches.cacheForPlayerWithResolver(player, null);
    List<EmptyFeedbackCallback> feedback = new ArrayList<>();
    List<String> messages = new ArrayList<>();
    User delegate = UserFactory.createTestUserFor(player, (ignored, key) -> {
      if (key.equals("protocolVersion")) return ServerProtocolVersion.current();
      if (key.equals("blockCache")) return cache;
      if (key.equals("shouldIgnoreNextOutboundPacket")) return false;
      return null;
    });
    User user = (User) java.lang.reflect.Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class},
      (proxy, method, args) -> {
        if (method.getName().equals("packetTickFeedback")) {
          assertSame(packet, ((PacketEvent)args[0]).getPacket());
          feedback.add((EmptyFeedbackCallback)args[1]);
          return null;
        }
        if (method.getName().equals("receives")) return true;
        if (method.getName().equals("sendMessage")) {
          messages.add((String)args[0]);
          return null;
        }
        return method.invoke(delegate, args);
      });
    UserRepository.manuallyRegisterUser(player, user);
    try {
      cache.setClientSpeculationValue(world, 0, 64, 0, Material.AIR, 0, 37);
      Interaction placement = new Interaction(0, null, world, player, null, 1,
        InteractionType.PLACE, Material.STONE, new ItemStack(Material.STONE),
        PlayerHand.MAIN_HAND, null, 0.5F, 1F, 0.5F, 37);
      placement.setEmulated();
      placement.markPlacementEmulated();
      placement.setEmulationPosition(new BlockPosition(0, 64, 0));
      InteractionRaytrace.InteractionMeta interactionMeta =
        (InteractionRaytrace.InteractionMeta) user.checkMetadata(InteractionRaytrace.InteractionMeta.class);
      interactionMeta.speculativeInteraction = placement;
      InteractionRaytrace check = IntavePlugin.singletonInstance().checks().searchCheck(InteractionRaytrace.class);
      // 26.2 sends a swing after successful placement. 26.3 proceeds without one.
      if (MinecraftVersions.VER26_3.below()) {
        check.receiveAnyTickContextPacket(PacketEvent.fromClient(this,
          new PacketContainer(PacketType.Play.Client.ARM_ANIMATION), player));
      }
      check.receiveAnyTickContextPacket(PacketEvent.fromClient(this,
        new PacketContainer(PacketType.Play.Client.PONG), player));
      assertTrue(cache.isClientSpeculatingAt(0, 64, 0));
      assertFalse(messages.stream().anyMatch(message -> message.contains("UNDO_PLACE")));
      PacketEvent event = PacketEvent.fromServer(this, packet, player);
      for (com.comphenix.protocol.events.PacketListener listener : ProtocolLibrary.getProtocolManager().getPacketListeners()) {
        if (listener instanceof ForwardingPacketAdapter && listener.getSendingWhitelist().getTypes().contains(packet.getType())) {
          listener.onPacketSending(event);
        }
      }
      assertEquals(1, feedback.size());
      assertTrue(cache.isClientSpeculatingAt(0, 64, 0));
      feedback.get(0).success();
      assertFalse(cache.isClientSpeculatingAt(0, 64, 0));
      assertTrue(messages.stream().anyMatch(message -> message.contains("CL_SPEC_FIN_37")));

      // Missing swings still cancel legacy predictions, but cannot cancel 26.3 placements.
      cache.setClientSpeculationValue(world, 0, 64, 0, Material.AIR, 0, 38);
      interactionMeta.speculativeInteraction = placement;
      check.receiveAnyTickContextPacket(PacketEvent.fromClient(this,
        new PacketContainer(PacketType.Play.Client.PONG), player));
      assertEquals(MinecraftVersions.VER26_3.atOrAbove(), cache.isClientSpeculatingAt(0, 64, 0));

      Interaction deferredPlacement = new Interaction(1, null, world, player, null, 1,
        InteractionType.PLACE, Material.STONE, new ItemStack(Material.STONE),
        PlayerHand.MAIN_HAND, null, 0.5F, 1F, 0.5F, 39);
      interactionMeta.speculativeInteraction = deferredPlacement;
      check.receiveAnyTickContextPacket(PacketEvent.fromClient(this,
        new PacketContainer(PacketType.Play.Client.PONG), player));
      assertEquals(MinecraftVersions.VER26_3.atOrAbove() ? InteractionType.PLACE : InteractionType.INTERACT,
        deferredPlacement.type());
      assertTrue(interactionMeta.speculativeInteraction == null);
    } finally {
      UserRepository.unregisterUser(player);
    }
  }

  @Test(testCode = "attachment-reader", severity = Severity.ERROR)
  public void testAttachmentReaderDistinguishesMountsFromLeashes() {
    boolean legacy = MinecraftVersions.VER1_9_0.below();
    PacketContainer packet = new PacketContainer(PacketType.Play.Server.ATTACH_ENTITY);
    int offset = legacy ? 1 : 0;
    if (legacy) packet.getIntegers().write(0, 0);
    packet.getIntegers().write(offset, 7).write(offset + 1, 10);
    try (AttachEntityReader reader = PacketReaders.readerOf(packet)) {
      assertEquals(7, reader.entityId());
      assertEquals(10, reader.vehicleId());
      assertEquals(legacy, reader.isMount());
      SubstitutionIterator<Integer> ids = reader.iterator();
      assertEquals(7, ids.next());
      ids.set(17);
      assertEquals(10, ids.next());
      ids.set(20);
      assertFalse(ids.hasNext());
      assertEquals(17, reader.entityId());
      assertEquals(20, reader.vehicleId());
      // A zero source ID on modern servers is still a leash, not a legacy mount flag.
      packet.getIntegers().write(0, legacy ? 1 : 0);
      assertFalse(reader.isMount());
    }
  }

  @Test(testCode = "mount-reader", severity = Severity.ERROR)
  public void testMountReaderRetainsAllPassengerIds() {
    if (MinecraftVersions.VER1_9_0.below()) return;
    PacketContainer packet = new PacketContainer(PacketType.Play.Server.MOUNT);
    packet.getIntegers().write(0, 10);
    packet.getIntegerArrays().write(0, new int[]{7, 11});
    try (MountEntityReader reader = PacketReaders.readerOf(packet)) {
      assertEquals(10, reader.entityId());
      assertTrue(Arrays.equals(new int[]{7, 11}, reader.mounts()));
      packet.getIntegerArrays().write(0, new int[0]);
      assertEquals(0, reader.mounts().length);
    }
  }

  @Test(testCode = "vehicle-movement", severity = Severity.ERROR)
  public void testVehicleMovementReadWrite() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_2.below()) return;
    PacketContainer packet = vehiclePacket(1.25D, 64.5D, -2.75D, 90F, -30F);
    try (PlayerMoveReader reader = PacketReaders.readerOf(packet)) {
      assertTrue(reader.isVehicleMove());
      assertTrue(reader.hasMovement());
      assertTrue(reader.hasRotation());
      assertTrue(reader.onGround());
      assertMovement(reader, 1.25D, 64.5D, -2.75D, 90F, -30F);
      assertFalse(reader.anyNaNOrInfiniteValue());
      reader.setPositionX(-16.25D);
      reader.setPositionY(72.125D);
      reader.setPositionZ(33.75D);
      reader.setYaw(-125.5F);
      reader.setPitch(45.25F);
      reader.setOnGround(false);
      assertMovement(reader, -16.25D, 72.125D, 33.75D, -125.5F, 45.25F);
      assertFalse(reader.onGround());
      reader.setPosition(new Position(5.5D, -12.25D, 128.75D));
      assertMovement(reader, 5.5D, -12.25D, 128.75D, -125.5F, 45.25F);
    }
    // Reacquire the reader to catch writes that only changed a detached view.
    try (PlayerMoveReader reader = PacketReaders.readerOf(packet)) {
      assertMovement(reader, 5.5D, -12.25D, 128.75D, -125.5F, 45.25F);
      assertFalse(reader.onGround());
    }
  }

  @Test(testCode = "position-rotation-converter", severity = Severity.ERROR)
  public void testPositionAndRotationConversionAndReplacement() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_2.below()) return;
    PositionAndRotation initial = new PositionAndRotation(1.25D, 64.5D, -2.75D, 90F, -30F);
    PositionAndRotation changed = new PositionAndRotation(-0.0D, -12.25D, 128.75D, -125.5F, -0.0F);
    PacketContainer packet = vehiclePacket(initial.x(), initial.y(), initial.z(), initial.yaw(), initial.pitch());
    Object originalNative = MinecraftVersions.VER26_3.atOrAbove() ? packet.getModifier().read(0) : null;
    PositionAndRotation snapshot;
    try (PlayerMoveReader reader = PacketReaders.readerOf(packet)) {
      snapshot = reader.positionAndRotation();
      assertEquals(initial, snapshot);
      reader.setPositionAndRotation(changed);
      assertEquals(changed, reader.positionAndRotation());
      assertTrue(reader.onGround());
    }
    assertEquals(initial, snapshot);
    try (PlayerMoveReader reader = PacketReaders.readerOf(packet)) {
      assertEquals(changed, reader.positionAndRotation());
    }
    if (originalNative != null) {
      PositionAndRotationConverter converter = PositionAndRotationConverter.INSTANCE;
      assertEquals(initial, converter.getSpecific(originalNative));
      assertEquals(changed, converter.getSpecific(converter.getGeneric(changed)));
      // Verify the actual native accessor values, independently of the converter.
      Object replacement = packet.getModifier().read(0);
      assertEquals(changed.yaw(), converter.nativeType().getMethod("yRot").invoke(replacement));
      assertEquals(changed.pitch(), converter.nativeType().getMethod("xRot").invoke(replacement));
    }
  }

  @Test(testCode = "fresh-player-teleport", severity = Severity.ERROR)
  public void testFreshPlayerTeleportWrite() {
    if (MinecraftVersions.VER1_21_3.below()) return;

    PositionMoveRotation change = new PositionMoveRotation(
      new Position(12.25D, 64.5D, -3.75D),
      new Motion(0.125D, -0.25D, 0.5D),
      new Rotation(90.0F, -30.0F)
    );
    Set<Relative> flags = EnumSet.of(Relative.X, Relative.DELTA_Y);
    Teleport teleport = Teleport.of(1L, OptionalInt.of(37), change, flags, null, true);
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.POSITION);

    try (PlayerTeleportReader reader = PacketReaders.readerOf(packet)) {
      reader.writeTeleport(teleport);
    }

    try (PlayerTeleportReader reader = PacketReaders.readerOf(packet)) {
      assertEquals(teleport.change(), reader.positionMoveRotation());
      assertEquals(teleport.relativeSet(), reader.flags());
      assertEquals(teleport.id(), reader.teleportId());
    }
    assertNull(PosMoveRotConverter.INSTANCE.getSpecific(null));
    assertNull(PosMoveRotConverter.INSTANCE.getGeneric(null));
  }

  @Test(testCode = "player-teleport-packet-state", severity = Severity.ERROR)
  public void testPlayerTeleportUsesPacketAsOnlyState() {
    boolean nativeMotion = MinecraftVersions.VER1_21_3.atOrAbove();
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.POSITION);
    PositionMoveRotation initial = new PositionMoveRotation(
      new Position(12.25D, 64.5D, -3.75D), Motion.newEmpty(), new Rotation(90F, -30F)
    );
    try (PlayerTeleportReader reader = PacketReaders.readerOf(packet)) {
      reader.setPositionMoveRotation(initial);
      PositionMoveRotation snapshot = reader.positionMoveRotation();
      // Neither input objects nor decoded snapshots are live packet views.
      initial.position().setX(1000);
      snapshot.position().setX(2000);
      reader.position().setY(3000);
      reader.rotation().setYaw(180);
      reader.motion().setMotionX(1);
      assertEquals(12.25D, reader.positionX());
      assertEquals(64.5D, reader.positionY());
      assertEquals(90F, reader.yaw());
      assertEquals(0D, reader.motionX());

      // Observe writes before the first reader is flushed or released.
      try (PlayerTeleportReader observer = new PlayerTeleportReader()) {
        observer.enter(packet);
        reader.setPositionX(-16.25D);
        reader.setPositionY(72.125D);
        reader.setPositionZ(33.75D);
        reader.setYaw(-125.5F);
        reader.setPitch(45.25F);
        reader.setMotionX(0.125D);
        reader.setMotionY(-0.25D);
        reader.setMotionZ(0.5D);
        PositionMoveRotation expected = new PositionMoveRotation(
          new Position(-16.25D, 72.125D, 33.75D),
          nativeMotion ? new Motion(0.125D, -0.25D, 0.5D) : Motion.newEmpty(),
          new Rotation(-125.5F, 45.25F)
        );
        assertEquals(expected, observer.positionMoveRotation());

        observer.setPositionX(7.5D);
        assertEquals(7.5D, reader.positionX());
        reader.flush();
        assertEquals(7.5D, observer.positionX());
      }
    }
    try (PlayerTeleportReader reader = PacketReaders.readerOf(packet)) {
      assertEquals(7.5D, reader.positionX());
    }
  }

  @Test(testCode = "partial-player-movement", severity = Severity.ERROR)
  public void testPartialMovementDoesNotInventMissingComponents() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_2.below()) return;
    String prefix = "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$";
    Object[] handles = {
      Class.forName(prefix + "Pos").getConstructor(double.class, double.class, double.class, boolean.class, boolean.class)
        .newInstance(1.25D, 64.5D, -2.75D, true, false),
      Class.forName(prefix + "Rot").getConstructor(float.class, float.class, boolean.class, boolean.class)
        .newInstance(90F, -30F, true, false),
      Class.forName(prefix + "StatusOnly").getConstructor(boolean.class, boolean.class).newInstance(true, false)
    };
    for (int i = 0; i < handles.length; i++) {
      // Exercise native decoding independently of packet-name registration.
      PlayerMoveReader acquired = new PlayerMoveReader();
      acquired.enter(PacketContainer.fromPacket(handles[i]));
      try (PlayerMoveReader reader = acquired) {
        assertNull(reader.positionAndRotation());
        assertEquals(i == 0, reader.hasMovement());
        assertEquals(i == 1, reader.hasRotation());
        boolean rejected = false;
        try {
          reader.setPositionAndRotation(new PositionAndRotation(8, 9, 10, 11, 12));
        } catch (IllegalStateException expected) {
          rejected = true;
        }
        assertTrue(rejected);
        if (i == 0) assertEquals(new Position(1.25D, 64.5D, -2.75D), reader.position());
        else assertNull(reader.position());
        if (i == 1) assertEquals(new de.jpx3.intave.share.Rotation(90F, -30F), reader.rotation());
        else assertNull(reader.rotation());
        assertFalse(reader.anyNaNOrInfiniteValue());
        assertTrue(reader.onGround());
      }
    }
  }

  @Test(testCode = "vehicle-invalid-movement", severity = Severity.ERROR)
  public void testVehicleMovementRejectsNonFiniteValues() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_2.below()) return;
    for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      for (int field = 0; field < 5; field++) {
        PacketContainer packet = vehiclePacket(field == 0 ? invalid : 1.25D,
          field == 1 ? invalid : 64.5D, field == 2 ? invalid : -2.75D,
          field == 3 ? (float)invalid : 90F, field == 4 ? (float)invalid : -30F);
        try (PlayerMoveReader reader = PacketReaders.readerOf(packet)) {
          assertTrue(reader.anyNaNOrInfiniteValue());
        }
      }
    }
  }

  @Test(testCode = "player-movement", severity = Severity.ERROR)
  public void testPlayerMovementKeepsItsOwnLayout() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_2.below()) return;
    Object handle = Class.forName("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$PosRot")
      .getConstructor(double.class, double.class, double.class, float.class, float.class, boolean.class, boolean.class)
      .newInstance(1.25D, 64.5D, -2.75D, 90F, -30F, true, false);
    PacketContainer packet = new PacketContainer(PacketType.Play.Client.POSITION_LOOK, handle);
    try (PlayerMoveReader reader = PacketReaders.readerOf(packet)) {
      assertFalse(reader.isVehicleMove());
      assertTrue(reader.hasMovement());
      assertTrue(reader.hasRotation());
      assertTrue(reader.onGround());
      assertMovement(reader, 1.25D, 64.5D, -2.75D, 90F, -30F);
      reader.setPosition(new Position(-1D, 80D, 3D));
      reader.setYaw(15F);
      reader.setPitch(-60F);
      reader.setOnGround(false);
      assertMovement(reader, -1D, 80D, 3D, 15F, -60F);
      assertFalse(reader.onGround());
      assertFalse(reader.anyNaNOrInfiniteValue());
      PositionAndRotation combined = new PositionAndRotation(7, 8, 9, 10, 11);
      reader.setPositionAndRotation(combined);
      assertEquals(combined, reader.positionAndRotation());
    }
  }

  private void assertMovement(PlayerMoveReader reader, double x, double y, double z, float yaw, float pitch) {
    assertEquals(new PositionAndRotation(x, y, z, yaw, pitch), reader.positionAndRotation());
    assertEquals(x, reader.positionX());
    assertEquals(y, reader.positionY());
    assertEquals(z, reader.positionZ());
    assertEquals(new Position(x, y, z), reader.position());
    assertEquals(yaw, reader.yaw());
    assertEquals(pitch, reader.pitch());
    assertEquals(new de.jpx3.intave.share.Rotation(yaw, pitch), reader.rotation());
  }

  private PacketContainer vehiclePacket(double x, double y, double z, float yaw, float pitch)
    throws ReflectiveOperationException {
    Class<?> vector = Class.forName("net.minecraft.world.phys.Vec3");
    Object position = vector.getConstructor(double.class, double.class, double.class).newInstance(x, y, z);
    Class<?> packet = Class.forName("net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket");
    Object handle;
    if (MinecraftVersions.VER26_3.atOrAbove()) {
      Class<?> transform = Class.forName("net.minecraft.core.PositionAndRotation");
      Object movingTo = transform.getMethod("of", vector, float.class, float.class).invoke(null, position, yaw, pitch);
      handle = packet.getConstructor(transform, boolean.class).newInstance(movingTo, true);
    } else {
      handle = packet.getConstructor(vector, float.class, float.class, boolean.class).newInstance(position, yaw, pitch, true);
    }
    return new PacketContainer(PacketType.Play.Client.VEHICLE_MOVE, handle);
  }

  @Test(testCode = "native-protocol", severity = Severity.ERROR)
  public void testNativeProtocolMatchesSharedVersionResource() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_2.below()) return;
    Object version = Class.forName("net.minecraft.SharedConstants").getMethod("getCurrentVersion").invoke(null);
    int nativeProtocol = (int) Class.forName("net.minecraft.WorldVersion").getMethod("protocolVersion").invoke(version);
    assertEquals(nativeProtocol, ServerProtocolVersion.current());
  }

  @Test(testCode = "263-items", severity = Severity.ERROR)
  public void testItemStackPacketConversion() {
    if (MinecraftVersions.VER26_2.below()) return;
    ItemStack item = new ItemStack(Material.DIAMOND, 7);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName("Packet conversion test");
    meta.setLore(Arrays.asList("Keep metadata", "through conversion"));
    item.setItemMeta(meta);

    PacketContainer slot = new PacketContainer(PacketType.Play.Server.SET_SLOT);
    slot.getIntegers().write(0, 0).write(1, 4).write(2, 12);
    slot.getItemModifier().write(0, item);
    ItemStack copy;
    try (WindowSingleItemReader singleReader = PacketReaders.readerOf(slot)) {
      new AttackDispatcher().filterSharpness(singleReader);
      assertEquals(item, slot.getItemModifier().read(0));
      assertEquals(item, singleReader.itemMap().get(12));
      copy = singleReader.item().clone();
      copy.setAmount(3);
      singleReader.setItem(copy);
      assertEquals(3, slot.getItemModifier().read(0).getAmount());
      assertEquals(7, item.getAmount());
    }

    ItemStack empty = new ItemStack(Material.AIR);
    PacketContainer contents = new PacketContainer(PacketType.Play.Server.WINDOW_ITEMS);
    contents.getIntegers().write(0, 0).write(1, 4);
    contents.getItemListModifier().write(0, Arrays.asList(item, empty));
    contents.getItemModifier().write(0, copy);
    WindowBulkItemReader bulkReader = new WindowBulkItemReader();
    bulkReader.enter(contents);
    assertEquals(item, bulkReader.itemMap().get(0));
    assertEquals(Material.AIR, bulkReader.itemMap().get(1).getType());
    assertEquals(copy, bulkReader.carriedItem());

    PacketContainer equipment = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
    equipment.getSlotStackPairLists().write(0, Arrays.asList(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, item)));
    assertEquals(item, equipment.getSlotStackPairLists().read(0).get(0).getSecond());
    slot.getItemModifier().write(0, empty);
    assertEquals(Material.AIR, slot.getItemModifier().read(0).getType());
    slot.getItemModifier().write(0, null);
    assertEquals(Material.AIR, slot.getItemModifier().read(0).getType());
    ProtocolLibraryAdapter.prepareItemStackConversion();
    slot.getItemModifier().write(0, item);
    assertEquals(item, slot.getItemModifier().read(0));
  }

  @Test(testCode = "263-relative", severity = Severity.ERROR)
  public void testRelativeEntityMovement() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_2.below()) return;
    int protocol = ServerProtocolVersion.current();
    for (PacketType type : Arrays.asList(PacketType.Play.Server.REL_ENTITY_MOVE,
      PacketType.Play.Server.REL_ENTITY_MOVE_LOOK, PacketType.Play.Server.ENTITY_LOOK)) {
      PacketContainer packet = linearMovePacket(type, (short)4096, (short)-2048, (short)1);
      boolean position = type != PacketType.Play.Server.ENTITY_LOOK;
      Entity entity = testEntity();
      entity.immediateEntityMovement(packet);
      entity.handleEntityMovement(null, packet, false);
      assertEquals(position ? 1.0D : 0.0D, entity.immediateServerPosition.getX());
      assertEquals(position ? -0.5D : 0.0D, entity.immediateServerPosition.getY());
      assertEquals(position ? 1.0D / 4096 : 0.0D, entity.immediateServerPosition.getZ());
      assertEquals(position ? 4096L : 0L, entity.serverPosX);
      EntityMovementReader.Delta update = EntityMovementReader.read(packet, protocol);
      assertEquals(position ? 4096L : 0L, update.x());
      assertTrue(packet.getBooleans().read(0));
      if (type != PacketType.Play.Server.REL_ENTITY_MOVE) {
        assertEquals((byte)64, packet.getBytes().read(0));
        assertEquals((byte)-32, packet.getBytes().read(1));
      }
      if (protocol >= 777 && position) {
        EntityMovementReader.writeLinear263(packet, Short.MIN_VALUE, (short)0, Short.MAX_VALUE);
        EntityMovementReader.Delta delta = EntityMovementReader.read(packet, protocol);
        assertEquals((long)Short.MIN_VALUE, delta.x());
        assertEquals((long)Short.MAX_VALUE, delta.z());
      }
    }
  }

  @Test(testCode = "263-stepped", severity = Severity.ERROR)
  public void testSteppedEntityMovement() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_3.below()) return;
    Class<?> stepType = Class.forName("net.minecraft.network.protocol.game.VecDelta$Stepped$DeltaStep");
    java.lang.reflect.Constructor<?> step = stepType.getConstructor(short.class, short.class, short.class, int.class);
    Object delta = Class.forName("net.minecraft.network.protocol.game.VecDelta$Stepped").getConstructor(List.class)
      .newInstance(Arrays.asList(step.newInstance((short)30000, (short)-1, (short)1, 2),
        step.newInstance((short)30000, (short)1, (short)-1, 4)));
    PacketContainer packet = movePacket(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK, delta);
    EntityMovementReader.Delta decoded = EntityMovementReader.read(packet, ServerProtocolVersion.current());
    assertTrue(decoded.stepped());
    assertEquals(2, decoded.steps().size());
    assertEquals(2, decoded.steps().get(0).ticks);
    assertEquals(4, decoded.steps().get(1).ticks);
    assertEquals(60000L, decoded.x());
    assertEquals(0L, decoded.y());
    Entity entity = testEntity();
    entity.immediateEntityMovement(packet);
    entity.handleEntityMovement(null, packet, false);
    assertEquals(60000L, entity.serverPosX);
    assertEquals(60000D / 4096, entity.immediateServerPosition.getX());

  }

  @Test(testCode = "263-sync", severity = Severity.ERROR)
  public void testEntityPositionPathSync() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_3.below()) return;
    Class<?> vector = Class.forName("net.minecraft.world.phys.Vec3");
    Object end = vector.getConstructor(double.class, double.class, double.class).newInstance(1.25D, 64.125D, -2.5D);
    Object path = Class.forName("net.minecraft.world.entity.PositionPath$Linear").getConstructor(vector).newInstance(end);
    Object handle = Class.forName("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket")
      .getConstructor(int.class, Class.forName("net.minecraft.world.entity.PositionPath"), float.class, float.class, boolean.class)
      .newInstance(7, path, 90F, -45F, true);
    PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_POSITION_SYNC, handle);
    Entity entity = testEntity();
    entity.immediateEntityPositionSync(packet);
    assertEquals(1.25D, entity.immediateServerPosition.getX());
    assertEquals(64.125D, entity.immediateServerPosition.getY());
    assertEquals(-2.5D, entity.immediateServerPosition.getZ());
    de.jpx3.intave.share.PositionMoveRotation sync =
      EntityMovementReader.readPositionSync(packet, ServerProtocolVersion.current());
    assertEquals(90F, sync.rotation().yaw());
    assertEquals(-45F, sync.rotation().pitch());
    assertEquals(64.125D, sync.position().getY());
  }

  private Entity testEntity() {
    return new Entity(7, new EntityTypeData("player", HitboxSize.of(0.6F, 1.8F), 159, true, 0), true);
  }

  private PacketContainer linearMovePacket(PacketType type, short x, short y, short z) throws ReflectiveOperationException {
    if (type == PacketType.Play.Server.ENTITY_LOOK) {
      Object handle = Class.forName("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot")
        .getConstructor(int.class, byte.class, byte.class, boolean.class).newInstance(7, (byte)64, (byte)-32, true);
      return new PacketContainer(type, handle);
    }
    if (MinecraftVersions.VER26_3.atOrAbove()) {
      Object delta = Class.forName("net.minecraft.network.protocol.game.VecDelta$Linear")
        .getConstructor(short.class, short.class, short.class).newInstance(x, y, z);
      return movePacket(type, delta);
    }
    boolean rotation = type == PacketType.Play.Server.REL_ENTITY_MOVE_LOOK;
    Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$" + (rotation ? "PosRot" : "Pos"));
    Object handle = rotation
      ? packetClass.getConstructor(int.class, short.class, short.class, short.class, byte.class, byte.class, boolean.class)
        .newInstance(7, x, y, z, (byte)64, (byte)-32, true)
      : packetClass.getConstructor(int.class, short.class, short.class, short.class, boolean.class).newInstance(7, x, y, z, true);
    return new PacketContainer(type, handle);
  }

  private PacketContainer movePacket(PacketType type, Object delta) throws ReflectiveOperationException {
    boolean rotation = type == PacketType.Play.Server.REL_ENTITY_MOVE_LOOK;
    Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$" + (rotation ? "PosRot" : "Pos"));
    Class<?> deltaType = Class.forName("net.minecraft.network.protocol.game.VecDelta");
    Object handle = rotation
      ? packetClass.getConstructor(int.class, deltaType, byte.class, byte.class, boolean.class).newInstance(7, delta, (byte)64, (byte)-32, true)
      : packetClass.getConstructor(int.class, deltaType, boolean.class).newInstance(7, delta, true);
    return new PacketContainer(type, handle);
  }

  @Test(testCode = "native-dig", severity = Severity.ERROR)
  public void testNativeDigActions() throws ReflectiveOperationException {
    if (new MinecraftVersion("1.21.11").below()) return;
    PacketContainer packet = new PacketContainer(PacketType.Play.Client.BLOCK_DIG);
    BlockPosition position = new BlockPosition(-17, 64, 33);
    packet.getBlockPositionModifier().write(0, position);
    Class<?> actionType = Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action");
    for (Object value : actionType.getEnumConstants()) {
      packet.getModifier().withType(actionType).write(0, value);
      String name = ((Enum<?>) value).name();
      if ("SWAP_ITEM_WITH_OFFHAND".equals(name)) name = "SWAP_HELD_ITEMS";
      PlayerDigAction expected;
      try {
        expected = PlayerDigAction.valueOf(name);
      } catch (IllegalArgumentException ignored) {
        expected = null;
      }
      try (BlockDigReader reader = PacketReaders.readerOf(packet)) {
        if (reader.action() != expected) fail("Incorrect digging action for " + name);
        assertEquals("STAB".equals(name), reader.isStab());
        assertEquals(position, reader.blockPosition());
        assertEquals(new de.jpx3.intave.share.BlockPosition(-17, 64, 33), reader.nativeBlockPosition());
      }
    }
    BlockDigReader.setAction(packet, PlayerDigAction.RELEASE_USE_ITEM);
    try (BlockDigReader reader = PacketReaders.readerOf(packet)) {
      assertEquals(PlayerDigAction.RELEASE_USE_ITEM, reader.action());
    }
  }

  public static Iterable<PacketType> registeredPacketTypes() {
    List<PacketType> types = new ArrayList<>();
    for (PacketType value : PacketType.values()) {
      if (PacketReaders.hasReader(value) && !EXCLUDED_TYPES.contains(value)) {
        types.add(value);
      }
    }
    return types;
  }

  @Test(testCode = "A", severity = Severity.ERROR, parameters = "registeredPacketTypes")
  public void testRegisteredReadersAcquireAndReleasePackets(PacketType type) {
    // One native packet per case lets the runner yield between expensive constructions.
    PacketContainer packet = new PacketContainer(type);
    AbstractPacketReader reader = PacketReaders.readerOf(packet);
    try (AbstractPacketReader acquired = reader) {
      assertSame(packet, acquired.packet());
    }
    assertNull(reader.packet());
  }
}
