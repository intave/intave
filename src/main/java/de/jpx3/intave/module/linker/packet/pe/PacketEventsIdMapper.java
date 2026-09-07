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

package de.jpx3.intave.module.linker.packet.pe;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import de.jpx3.intave.module.linker.packet.PacketId;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates Intave's engine independent {@link PacketId} constants into PacketEvents packet types.
 * <p>
 * Types are resolved by <em>name</em> through reflection instead of by direct constant reference.
 * PacketEvents renames and adds constants between releases (the build targets 2.13.0 while older
 * runtimes ship 2.4.0); a hard reference to a constant that does not exist in the running version
 * would fail with {@link NoSuchFieldError} at class initialization and take the whole packet layer
 * down. Resolving by name degrades to "this packet is unavailable on this version" instead.
 */
public final class PacketEventsIdMapper {

  private static final String CLIENT_TYPES = "com.github.retrooper.packetevents.protocol.packettype.PacketType$Play$Client";
  private static final String SERVER_TYPES = "com.github.retrooper.packetevents.protocol.packettype.PacketType$Play$Server";

  /**
   * Intave client packet name -> PacketEvents constant candidates, in preference order.
   * <p>
   * The candidates are the same packet under the names different protocol generations gave it -
   * {@code TRANSACTION} is {@code WINDOW_CONFIRMATION} up to 1.16 and {@code PING} from 1.17,
   * {@code STEER_VEHICLE} became {@code PLAYER_INPUT} in 1.21.2. PacketEvents declares
   * <em>every</em> such constant on every release regardless of the server it runs on, so the choice
   * cannot be made by asking which constant exists; it is made per server version in
   * {@link #resolve}.
   */
  private static final Map<String, String[]> CLIENT = new HashMap<>();
  /** Intave server packet name -> PacketEvents constant candidates; see {@link #CLIENT}. */
  private static final Map<String, String[]> SERVER = new HashMap<>();

  private static final Map<String, List<PacketTypeCommon>> RESOLVED_CLIENT = new HashMap<>();
  private static final Map<String, List<PacketTypeCommon>> RESOLVED_SERVER = new HashMap<>();

  private static final List<String> UNRESOLVED = new ArrayList<>();

  static {
    /* ---- client bound (player -> server) ---- */
    client("ABILITIES", "PLAYER_ABILITIES");
    client("ADVANCEMENTS", "ADVANCEMENT_TAB");
    client("ATTACK", "INTERACT_ENTITY");
    client("ARM_ANIMATION", "ANIMATION");
    client("AUTO_RECIPE", "CRAFT_RECIPE_REQUEST");
    client("BEACON", "SET_BEACON_EFFECT");
    client("BLOCK_DIG", "PLAYER_DIGGING");
    client("BLOCK_PLACE", "PLAYER_BLOCK_PLACEMENT");
    client("BOAT_MOVE", "STEER_BOAT");
    client("B_EDIT", "EDIT_BOOK");
    client("CHAT", "CHAT_MESSAGE");
    client("CLIENT_COMMAND", "CLIENT_STATUS");
    client("CLIENT_TICK_END", "CLIENT_TICK_END");
    client("CLOSE_WINDOW", "CLOSE_WINDOW");
    client("CUSTOM_PAYLOAD", "PLUGIN_MESSAGE");
    client("DIFFICULTY_CHANGE", "SET_DIFFICULTY");
    client("DIFFICULTY_LOCK", "LOCK_DIFFICULTY");
    client("ENCHANT_ITEM", "CLICK_WINDOW_BUTTON");
    client("ENTITY_ACTION", "ENTITY_ACTION");
    client("ENTITY_NBT_QUERY", "QUERY_ENTITY_NBT");
    client("FLYING", "PLAYER_FLYING");
    client("HELD_ITEM_SLOT", "HELD_ITEM_CHANGE");
    client("ITEM_NAME", "NAME_ITEM");
    client("JIGSAW_GENERATE", "GENERATE_STRUCTURE");
    client("KEEP_ALIVE", "KEEP_ALIVE");
    client("LOOK", "PLAYER_ROTATION");
    client("PICK_ITEM", "PICK_ITEM", "PICK_ITEM_FROM_BLOCK");
    client("PONG", "PONG");
    client("POSITION", "PLAYER_POSITION");
    client("POSITION_LOOK", "PLAYER_POSITION_AND_ROTATION");
    client("RECIPE_DISPLAYED", "SET_DISPLAYED_RECIPE");
    client("RECIPE_SETTINGS", "SET_RECIPE_BOOK_STATE");
    client("RESOURCE_PACK_STATUS", "RESOURCE_PACK_STATUS");
    client("SETTINGS", "CLIENT_SETTINGS");
    client("SET_COMMAND_BLOCK", "UPDATE_COMMAND_BLOCK");
    client("SET_COMMAND_MINECART", "UPDATE_COMMAND_BLOCK_MINECART");
    client("SET_CREATIVE_SLOT", "CREATIVE_INVENTORY_ACTION");
    client("SET_JIGSAW", "UPDATE_JIGSAW_BLOCK");
    client("SPECTATE", "SPECTATE");
    client("STEER_VEHICLE", "STEER_VEHICLE", "PLAYER_INPUT");
    client("STRUCT", "UPDATE_STRUCTURE_BLOCK");
    client("TAB_COMPLETE", "TAB_COMPLETE");
    client("TELEPORT_ACCEPT", "TELEPORT_CONFIRM");
    client("TILE_NBT_QUERY", "QUERY_BLOCK_NBT");
    client("TRANSACTION", "WINDOW_CONFIRMATION");
    client("TR_SEL", "SELECT_TRADE");
    client("UPDATE_SIGN", "UPDATE_SIGN");
    client("USE_ENTITY", "INTERACT_ENTITY");
    client("USE_ITEM", "USE_ITEM");
    client("USE_ITEM_ON", "PLAYER_BLOCK_PLACEMENT");
    client("VEHICLE_MOVE", "VEHICLE_MOVE");
    client("WINDOW_CLICK", "CLICK_WINDOW");

    /* ---- server bound (server -> player) ---- */
    server("ABILITIES", "PLAYER_ABILITIES");
    server("ADVANCEMENTS", "UPDATE_ADVANCEMENTS");
    server("ANIMATION", "ENTITY_ANIMATION");
    server("ATTACH_ENTITY", "ATTACH_ENTITY");
    server("AUTO_RECIPE", "CRAFT_RECIPE_RESPONSE");
    server("BED", "USE_BED");
    server("BLOCK_ACTION", "BLOCK_ACTION");
    server("BLOCK_BREAK", "ACKNOWLEDGE_PLAYER_DIGGING");
    server("BLOCK_BREAK_ANIMATION", "BLOCK_BREAK_ANIMATION");
    server("BLOCK_CHANGE", "BLOCK_CHANGE");
    server("BLOCK_CHANGED_ACK", "ACKNOWLEDGE_BLOCK_CHANGES");
    server("BOSS", "BOSS_BAR");
    server("CAMERA", "CAMERA");
    server("CHAT", "CHAT_MESSAGE", "SYSTEM_CHAT_MESSAGE");
    server("CLOSE_WINDOW", "CLOSE_WINDOW");
    server("COLLECT", "COLLECT_ITEM");
    server("COMBAT_EVENT", "COMBAT_EVENT", "END_COMBAT_EVENT");
    server("COMMANDS", "DECLARE_COMMANDS");
    server("CRAFT_PROGRESS_BAR", "WINDOW_PROPERTY");
    server("CUSTOM_PAYLOAD", "PLUGIN_MESSAGE");
    server("CUSTOM_SOUND_EFFECT", "NAMED_SOUND_EFFECT", "SOUND_EFFECT");
    server("ENTITY", "ENTITY_MOVEMENT");
    server("ENTITY_DESTROY", "DESTROY_ENTITIES");
    server("ENTITY_EFFECT", "ENTITY_EFFECT");
    server("ENTITY_EQUIPMENT", "ENTITY_EQUIPMENT");
    server("ENTITY_HEAD_ROTATION", "ENTITY_HEAD_LOOK");
    server("ENTITY_LOOK", "ENTITY_ROTATION");
    server("ENTITY_METADATA", "ENTITY_METADATA");
    server("ENTITY_MOVE_LOOK", "ENTITY_RELATIVE_MOVE_AND_ROTATION");
    server("ENTITY_POSITION_SYNC", "ENTITY_POSITION_SYNC", "ENTITY_TELEPORT");
    server("ENTITY_SOUND", "ENTITY_SOUND_EFFECT");
    server("ENTITY_STATUS", "ENTITY_STATUS");
    server("ENTITY_TELEPORT", "ENTITY_TELEPORT");
    server("ENTITY_VELOCITY", "ENTITY_VELOCITY");
    server("EXPERIENCE", "SET_EXPERIENCE");
    server("EXPLOSION", "EXPLOSION");
    server("GAME_STATE_CHANGE", "CHANGE_GAME_STATE");
    server("HELD_ITEM_SLOT", "HELD_ITEM_CHANGE");
    server("INITIALIZE_BORDER", "INITIALIZE_WORLD_BORDER");
    server("KEEP_ALIVE", "KEEP_ALIVE");
    server("KICK_DISCONNECT", "DISCONNECT");
    server("LIGHT_UPDATE", "UPDATE_LIGHT");
    server("LOGIN", "JOIN_GAME");
    server("LOOK_AT", "FACE_PLAYER");
    server("MAP", "MAP_DATA");
    server("MAP_CHUNK", "CHUNK_DATA");
    server("MAP_CHUNK_BULK", "MAP_CHUNK_BULK");
    server("MOUNT", "SET_PASSENGERS");
    server("MULTI_BLOCK_CHANGE", "MULTI_BLOCK_CHANGE");
    server("NAMED_ENTITY_SPAWN", "SPAWN_PLAYER");
    server("NAMED_SOUND_EFFECT", "NAMED_SOUND_EFFECT", "SOUND_EFFECT");
    server("NBT_QUERY", "NBT_QUERY_RESPONSE");
    server("OPEN_BOOK", "OPEN_BOOK");
    server("OPEN_SIGN_EDITOR", "OPEN_SIGN_EDITOR");
    server("OPEN_SIGN_ENTITY", "OPEN_SIGN_EDITOR");
    server("OPEN_WINDOW", "OPEN_WINDOW");
    server("OPEN_WINDOW_HORSE", "OPEN_HORSE_WINDOW");
    server("OPEN_WINDOW_MERCHANT", "MERCHANT_OFFERS");
    server("PING", "PING");
    server("PLAYER_INFO", "PLAYER_INFO", "PLAYER_INFO_UPDATE");
    server("PLAYER_INFO_REMOVE", "PLAYER_INFO_REMOVE");
    server("PLAYER_LIST_HEADER_FOOTER", "PLAYER_LIST_HEADER_AND_FOOTER");
    server("POSITION", "PLAYER_POSITION_AND_LOOK");
    server("RECIPES", "DECLARE_RECIPES");
    server("RECIPE_UPDATE", "UNLOCK_RECIPES");
    server("REL_ENTITY_MOVE", "ENTITY_RELATIVE_MOVE");
    server("REL_ENTITY_MOVE_LOOK", "ENTITY_RELATIVE_MOVE_AND_ROTATION");
    server("REMOVE_ENTITY_EFFECT", "REMOVE_ENTITY_EFFECT");
    server("RESOURCE_PACK_SEND", "RESOURCE_PACK_SEND");
    server("RESPAWN", "RESPAWN");
    server("SCOREBOARD_DISPLAY_OBJECTIVE", "DISPLAY_SCOREBOARD");
    server("SCOREBOARD_OBJECTIVE", "SCOREBOARD_OBJECTIVE");
    server("SCOREBOARD_SCORE", "UPDATE_SCORE");
    server("SCOREBOARD_TEAM", "TEAMS");
    server("SELECT_ADVANCEMENT_TAB", "SELECT_ADVANCEMENTS_TAB");
    server("SERVER_DIFFICULTY", "SERVER_DIFFICULTY");
    server("SET_BORDER_CENTER", "WORLD_BORDER_CENTER");
    server("SET_BORDER_LERP_SIZE", "WORLD_BORDER_LERP_SIZE");
    server("SET_BORDER_SIZE", "WORLD_BORDER_SIZE");
    server("SET_BORDER_WARNING_DELAY", "WORLD_BORDER_WARNING_DELAY");
    server("SET_BORDER_WARNING_DISTANCE", "WORLD_BORDER_WARNING_REACH");
    server("SET_COMPRESSION", "SET_COMPRESSION");
    server("SET_COOLDOWN", "SET_COOLDOWN");
    server("SET_SLOT", "SET_SLOT");
    server("SPAWN_ENTITY", "SPAWN_ENTITY");
    server("SPAWN_ENTITY_EXPERIENCE_ORB", "SPAWN_EXPERIENCE_ORB");
    server("SPAWN_ENTITY_LIVING", "SPAWN_LIVING_ENTITY", "SPAWN_ENTITY");
    server("SPAWN_ENTITY_PAINTING", "SPAWN_PAINTING", "SPAWN_ENTITY");
    server("SPAWN_ENTITY_WEATHER", "SPAWN_WEATHER_ENTITY", "SPAWN_ENTITY");
    server("SPAWN_POSITION", "SPAWN_POSITION");
    server("STATISTIC", "STATISTICS");
    server("STATISTICS", "STATISTICS");
    server("STOP_SOUND", "STOP_SOUND");
    server("TAB_COMPLETE", "TAB_COMPLETE");
    server("TAGS", "TAGS");
    server("TILE_ENTITY_DATA", "BLOCK_ENTITY_DATA");
    server("TITLE", "TITLE", "SET_TITLE_TEXT");
    server("TRANSACTION", "WINDOW_CONFIRMATION", "PING");
    server("UNLOAD_CHUNK", "UNLOAD_CHUNK");
    server("UPDATE_ATTRIBUTES", "UPDATE_ATTRIBUTES");
    server("UPDATE_ENTITY_NBT", "UPDATE_ENTITY_NBT");
    server("UPDATE_HEALTH", "UPDATE_HEALTH");
    server("UPDATE_SIGN", "UPDATE_SIGN");
    server("UPDATE_TIME", "TIME_UPDATE");
    server("UPDATE_TAGS", "TAGS");
    server("USE_BED", "USE_BED");
    server("VEHICLE_MOVE", "VEHICLE_MOVE");
    server("VIEW_CENTRE", "UPDATE_VIEW_POSITION");
    server("VIEW_DISTANCE", "UPDATE_VIEW_DISTANCE");
    server("WINDOW_DATA", "WINDOW_PROPERTY");
    server("WINDOW_ITEMS", "WINDOW_ITEMS");
    server("WORLD_BORDER", "WORLD_BORDER");
    server("WORLD_EVENT", "EFFECT");
    server("WORLD_PARTICLES", "PARTICLE");
  }

  private PacketEventsIdMapper() {
  }

  private static void client(String intaveName, String... packetEventsCandidates) {
    CLIENT.put(intaveName, packetEventsCandidates);
  }

  private static void server(String intaveName, String... packetEventsCandidates) {
    SERVER.put(intaveName, packetEventsCandidates);
  }

  /**
   * @return every PacketEvents type the given Intave client packet maps to; empty when the packet
   * does not exist on the running PacketEvents version.
   */
  public static List<PacketTypeCommon> typesOf(PacketId.Client packet) {
    if (packet == null) {
      return Collections.emptyList();
    }
    if ("*".equals(packet.lookupName())) {
      return allOf(CLIENT_TYPES);
    }
    return resolve(packet.lookupName(), CLIENT, RESOLVED_CLIENT, CLIENT_TYPES);
  }

  /**
   * @return every PacketEvents type the given Intave server packet maps to; empty when the packet
   * does not exist on the running PacketEvents version.
   */
  public static List<PacketTypeCommon> typesOf(PacketId.Server packet) {
    if (packet == null) {
      return Collections.emptyList();
    }
    if ("*".equals(packet.lookupName())) {
      return allOf(SERVER_TYPES);
    }
    return resolve(packet.lookupName(), SERVER, RESOLVED_SERVER, SERVER_TYPES);
  }

  /**
   * @return the constant each Intave packet id actually bound on this server, for the ids that some
   * subscription asked for. Reported from the resolution cache rather than by resolving the whole
   * table, so it shows what is really in use and cannot itself mark unused ids as unresolved.
   * <p>
   * This is the table worth reading first when a check goes quiet: several Intave ids carry two
   * candidate names for the same packet across protocol generations, and binding the wrong one
   * produces a subscription that is registered, healthy and never called.
   */
  public static Map<String, String> boundNames() {
    Map<String, String> names = new java.util.TreeMap<>();
    collectBound(RESOLVED_CLIENT, names);
    collectBound(RESOLVED_SERVER, names);
    return names;
  }

  private static void collectBound(Map<String, List<PacketTypeCommon>> cache, Map<String, String> into) {
    for (Map.Entry<String, List<PacketTypeCommon>> entry : cache.entrySet()) {
      List<PacketTypeCommon> types = entry.getValue();
      if (types.isEmpty()) {
        continue;
      }
      into.put(entry.getKey(), types.get(0).getName());
    }
  }

  /**
   * @return Intave packet ids whose table entry lists more than one candidate name. These are the
   * ones where the choice was actually made, so they are the ones worth printing.
   */
  public static java.util.Set<String> ambiguousIds() {
    java.util.Set<String> ids = new java.util.TreeSet<>();
    for (Map.Entry<String, String[]> entry : CLIENT.entrySet()) {
      if (entry.getValue().length > 1) {
        ids.add(entry.getKey());
      }
    }
    for (Map.Entry<String, String[]> entry : SERVER.entrySet()) {
      if (entry.getValue().length > 1) {
        ids.add(entry.getKey());
      }
    }
    return ids;
  }

  /** Packets that could not be resolved on this runtime; useful for a one-shot startup report. */
  public static List<String> unresolvedPackets() {
    return Collections.unmodifiableList(UNRESOLVED);
  }

  private static List<PacketTypeCommon> resolve(
    String lookupName,
    Map<String, String[]> table,
    Map<String, List<PacketTypeCommon>> cache,
    String holderClass
  ) {
    List<PacketTypeCommon> cached = cache.get(lookupName);
    if (cached != null) {
      return cached;
    }
    String[] candidates = table.get(lookupName);
    if (candidates == null) {
      // Not in the table: try the Intave name verbatim, several packets share their name.
      candidates = new String[]{lookupName};
    }
    List<PacketTypeCommon> existing = new ArrayList<>(2);
    for (String candidate : candidates) {
      PacketTypeCommon type = constantOf(holderClass, candidate);
      // Identity is the right comparison: a constant kept as an alias of another is the same object.
      if (type != null && !existing.contains(type)) {
        existing.add(type);
      }
    }
    if (existing.isEmpty()) {
      if (!UNRESOLVED.contains(lookupName)) {
        UNRESOLVED.add(lookupName);
      }
      return Collections.emptyList();
    }
    ClientVersion version = runningVersion();
    PacketTypeCommon chosen = version == null ? null : firstCarriedBy(existing, version);
    boolean versionChecked = chosen != null;
    if (chosen == null) {
      // No running version yet, or nothing answered: fall back to the first declared candidate.
      chosen = existing.get(0);
    }
    List<PacketTypeCommon> types = Collections.singletonList(chosen);
    if (versionChecked) {
      // Only cache a version checked answer. Resolving before PacketEvents knows the server version
      // would otherwise freeze the fallback in for the lifetime of the process.
      cache.put(lookupName, types);
    }
    return types;
  }

  /**
   * @return the first candidate that the running server version actually puts on the wire, or null
   * when none of them answers with a real id.
   * <p>
   * This is what picks between two names for one packet. PacketEvents declares
   * {@code WINDOW_CONFIRMATION} and {@code PING} - and {@code SPAWN_LIVING_ENTITY} next to
   * {@code SPAWN_ENTITY}, and {@code PLAYER_INFO} next to {@code PLAYER_INFO_UPDATE} - on every
   * release, so choosing by "which constant exists" would always take the older name and leave the
   * subscription bound to a packet a modern server never sends. A packet id of -1 (or an exception
   * from a type that has none on this version) is PacketEvents' own answer for "not on this wire".
   */
  private static PacketTypeCommon firstCarriedBy(List<PacketTypeCommon> candidates, ClientVersion version) {
    for (PacketTypeCommon candidate : candidates) {
      try {
        if (candidate.getId(version) >= 0) {
          return candidate;
        }
      } catch (RuntimeException exception) {
        // A type with no id table for this version; treat exactly like -1 and keep looking.
      }
    }
    return null;
  }

  /** @return the running server's protocol version, or null before PacketEvents is up. */
  private static ClientVersion runningVersion() {
    try {
      PacketEventsAPI<?> api = PacketEvents.getAPI();
      if (api == null) {
        return null;
      }
      ServerVersion version = api.getServerManager().getVersion();
      return version == null ? null : version.toClientVersion();
    } catch (RuntimeException | LinkageError throwable) {
      return null;
    }
  }

  private static PacketTypeCommon constantOf(String holderClass, String constantName) {
    try {
      Class<?> holder = Class.forName(holderClass);
      Field field = holder.getField(constantName);
      if (!Modifier.isStatic(field.getModifiers())) {
        return null;
      }
      Object value = field.get(null);
      return value instanceof PacketTypeCommon ? (PacketTypeCommon) value : null;
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
      return null;
    }
  }

  private static List<PacketTypeCommon> allOf(String holderClass) {
    List<PacketTypeCommon> types = new ArrayList<>();
    try {
      Class<?> holder = Class.forName(holderClass);
      for (Field field : holder.getFields()) {
        if (!Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        Object value = field.get(null);
        if (value instanceof PacketTypeCommon) {
          types.add((PacketTypeCommon) value);
        }
      }
    } catch (ClassNotFoundException | IllegalAccessException exception) {
      return Collections.emptyList();
    }
    return types;
  }
}
