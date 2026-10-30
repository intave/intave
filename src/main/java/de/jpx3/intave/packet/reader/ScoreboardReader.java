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

import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.user.meta.ClientScoreboard;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/** Decodes the scoreboard packets which mutate the client's local scoreboard. */
public final class ScoreboardReader extends AbstractPacketReader {
  public void disableTeamCollisions() {
    if (MinecraftVersions.VER1_9_0.below()) {
      return;
    }
    int action = teamAction();
    if (action != 0 && action != 2) {
      return;
    }
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      Optional<InternalStructure> optional = packet().getOptionalStructures().readSafely(0);
      if (optional == null || !optional.isPresent()) {
        return;
      }
      InternalStructure parameters = optional.get();
      if (MinecraftVersions.VER1_21_5.atOrAbove()) {
        // 1.21.5 replaced the serialized rule names with native enums.
        Class<?> nativeRule = MinecraftReflection.getMinecraftClass(
          "world.scores.Team$CollisionRule", "world.scores.ScoreboardTeamBase$EnumTeamPush"
        );
        parameters.getEnumModifier(CollisionRule.class, nativeRule).write(0, CollisionRule.NEVER);
      } else {
        parameters.getStrings().write(1, "never");
      }
    } else {
      packet().getStrings().write(MinecraftVersions.VER1_13_0.atOrAbove() ? 2 : 5, "never");
    }
  }

  private enum CollisionRule {
    ALWAYS, NEVER, PUSH_OTHER_TEAMS, PUSH_OWN_TEAM
  }

  public Kind kind() {
    return Kind.valueOf(packet().getType().name());
  }

  public Objective objective() {
    String name = packet().getStrings().readSafely(0);
    int action = packet().getIntegers().read(0);
    String title = null;
    if (action != 1) {
      title = component(packet().getChatComponents().readSafely(0));
      if (title == null) {
        title = packet().getStrings().readSafely(1);
      }
    }
    return new Objective(name, title, action);
  }

  public Display display() {
    String slot;
    if (MinecraftVersions.VER1_20_2.atOrAbove()) {
      EnumWrappers.DisplaySlot displaySlot = packet().getDisplaySlots().read(0);
      slot = displaySlot.name().toLowerCase(Locale.ROOT);
    } else {
      slot = ClientScoreboard.legacyDisplaySlot(packet().getIntegers().read(0));
    }
    return new Display(slot, packet().getStrings().readSafely(0));
  }

  public Score score() {
    String owner = packet().getStrings().read(0);
    String objective = packet().getStrings().readSafely(1);
    EnumWrappers.ScoreboardAction action = MinecraftVersions.VER1_20_3.atOrAbove()
      ? null
      : packet().getScoreboardActions().readSafely(0);
    if (action == EnumWrappers.ScoreboardAction.REMOVE) {
      return new Score(owner, objective, 0, null, true);
    }
    Integer value = packet().getIntegers().readSafely(0);
    return new Score(owner, objective, value == null ? 0 : value, optionalComponent(), false);
  }

  public Score resetScore() {
    String owner = packet().getStrings().read(0);
    String objective = optionalString();
    return new Score(owner, objective, 0, null, true);
  }

  public Team team() {
    String name = packet().getStrings().read(0);
    int action = teamAction();
    List<String> entries = entries();
    if (action != 0 && action != 2) {
      return new Team(name, action, null, null, null, entries);
    }

    Supplier<String> prefix;
    Supplier<String> suffix;
    String color;
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      Optional<InternalStructure> optional = packet().getOptionalStructures().readSafely(0);
      InternalStructure parameters = optional == null || !optional.isPresent() ? null : optional.get();
      prefix = parameters == null ? EMPTY_TEXT : lazyComponent(parameters.getChatComponents().readSafely(1));
      suffix = parameters == null ? EMPTY_TEXT : lazyComponent(parameters.getChatComponents().readSafely(2));
      color = parameters == null ? "reset" : color(parameters.getModifier());
    } else if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      prefix = lazyComponent(packet().getChatComponents().readSafely(1));
      suffix = lazyComponent(packet().getChatComponents().readSafely(2));
      color = color(packet().getModifier());
    } else {
      String legacyPrefix = packet().getStrings().readSafely(2);
      String legacySuffix = packet().getStrings().readSafely(3);
      Integer legacyColor = packet().getIntegers().readSafely(0);
      color = legacyColor == null
        ? legacyColorIn(legacyPrefix)
        : ClientScoreboard.legacyColor(legacyColor);
      prefix = fixedText(legacyPrefix);
      suffix = fixedText(legacySuffix);
    }
    return new Team(name, action, prefix, suffix, color, entries);
  }

  private int teamAction() {
    // Before 1.13 the integer color precedes the action; newer layouts use an enum.
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      return packet().getIntegers().read(0);
    }
    return packet().getIntegers().read(1);
  }

  private static String color(StructureModifier<Object> fields) {
    // Older supported ProtocolLib builds do not expose getChatFormattings().
    for (Object value : fields.getValues()) {
      if (value instanceof Optional) {
        value = ((Optional<?>) value).orElse(null);
      }
      if (value instanceof Enum) {
        Enum<?> formatting = (Enum<?>) value;
        String type = formatting.getDeclaringClass().getSimpleName();
        if (type.equals("EnumChatFormat") || type.equals("ChatFormatting") || type.equals("TeamColor")) {
          return formatting.name().toLowerCase(Locale.ROOT);
        }
      }
    }
    return "reset";
  }

  private List<String> entries() {
    Collection<?> values = packet().getSpecificModifier(Collection.class).readSafely(0);
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> entries = new ArrayList<>(values.size());
    for (Object value : values) {
      if (value != null) {
        entries.add(value.toString());
      }
    }
    return entries;
  }

  private String optionalComponent() {
    Optional<?> optional = packet().getSpecificModifier(Optional.class).readSafely(0);
    if (optional == null) {
      // 1.20.3/4 store the score display name as a nullable component.
      return component(packet().getChatComponents().readSafely(0));
    }
    if (!optional.isPresent()) {
      return null;
    }
    try {
      return component(WrappedChatComponent.fromHandle(optional.get()));
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String optionalString() {
    Optional<?> optional = packet().getSpecificModifier(Optional.class).readSafely(0);
    if (optional != null) {
      return optional.isPresent() ? optional.get().toString() : null;
    }
    return packet().getStrings().readSafely(1);
  }

  private static String component(WrappedChatComponent component) {
    if (component == null) {
      return null;
    }
    return BaseComponent.toLegacyText(ComponentSerializer.parse(component.getJson()));
  }

  private static final Supplier<String> EMPTY_TEXT = () -> "";

  private static Supplier<String> fixedText(String text) {
    return text == null || text.isEmpty() ? EMPTY_TEXT : () -> text;
  }

  // Team prefixes are only needed when a sidebar line is rendered, convert them on first use
  private static Supplier<String> lazyComponent(WrappedChatComponent component) {
    return component == null ? EMPTY_TEXT : new LazyComponentText(component);
  }

  private static final class LazyComponentText implements Supplier<String> {
    private WrappedChatComponent component;
    private String text;

    private LazyComponentText(WrappedChatComponent component) {
      this.component = component;
    }

    @Override
    public synchronized String get() {
      if (text == null) {
        try {
          String converted = component(component);
          text = converted == null ? "" : converted;
        } catch (RuntimeException exception) {
          text = "";
        }
        component = null;
      }
      return text;
    }
  }

  private static String legacyColorIn(String text) {
    if (text != null) {
      for (int i = 0; i + 1 < text.length(); i++) {
        if (text.charAt(i) != '\u00a7') {
          continue;
        }
        int color = Character.digit(text.charAt(i + 1), 16);
        if (color >= 0) {
          return ClientScoreboard.legacyColor(color);
        }
      }
    }
    return "reset";
  }

  public enum Kind {
    SCOREBOARD_OBJECTIVE,
    SCOREBOARD_DISPLAY_OBJECTIVE,
    SCOREBOARD_SCORE,
    RESET_SCORE,
    SCOREBOARD_TEAM
  }

  public static final class Objective {
    public final String name;
    public final String title;
    public final int action;

    private Objective(String name, String title, int action) {
      this.name = name;
      this.title = title;
      this.action = action;
    }
  }

  public static final class Display {
    public final String slot;
    public final String objective;

    private Display(String slot, String objective) {
      this.slot = slot;
      this.objective = objective;
    }
  }

  public static final class Score {
    public final String owner;
    public final String objective;
    public final int value;
    public final String displayName;
    public final boolean remove;

    private Score(
      String owner, String objective, int value,
      String displayName, boolean remove
    ) {
      this.owner = owner;
      this.objective = objective;
      this.value = value;
      this.displayName = displayName;
      this.remove = remove;
    }
  }

  public static final class Team {
    public final String name;
    public final int action;
    public final Supplier<String> prefix;
    public final Supplier<String> suffix;
    public final String color;
    public final List<String> entries;

    private Team(
      String name, int action, Supplier<String> prefix, Supplier<String> suffix,
      String color, List<String> entries
    ) {
      this.name = name;
      this.action = action;
      this.prefix = prefix;
      this.suffix = suffix;
      this.color = color;
      this.entries = entries;
    }
  }
}
