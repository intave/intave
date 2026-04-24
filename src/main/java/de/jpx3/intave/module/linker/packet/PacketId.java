package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

public final class PacketId {
  public enum Client {
    @Deprecated
    ALL("*"),
    ABILITIES_IN("ABILITIES", PacketType.Play.Client.PLAYER_ABILITIES),
    ADVANCEMENTS("ADVANCEMENTS", PacketType.Play.Client.ADVANCEMENT_TAB),
    ARM_ANIMATION("ARM_ANIMATION", PacketType.Play.Client.ANIMATION),
    AUTO_RECIPE("AUTO_RECIPE", PacketType.Play.Client.CRAFT_RECIPE_REQUEST),
    BEACON("BEACON", PacketType.Play.Client.SET_BEACON_EFFECT),
    BLOCK_DIG("BLOCK_DIG", PacketType.Play.Client.PLAYER_DIGGING),
    BLOCK_PLACE("BLOCK_PLACE", PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT),
    BOAT_MOVE("BOAT_MOVE", PacketType.Play.Client.STEER_BOAT),
    B_EDIT("B_EDIT", PacketType.Play.Client.EDIT_BOOK),
    CHAT_IN("CHAT", PacketType.Play.Client.CHAT_MESSAGE, PacketType.Play.Client.CHAT_COMMAND, PacketType.Play.Client.CHAT_COMMAND_UNSIGNED),
    CLIENT_COMMAND("CLIENT_COMMAND", PacketType.Play.Client.CLIENT_STATUS),
    CLIENT_TICK_END("CLIENT_TICK_END", PacketType.Play.Client.CLIENT_TICK_END),
    CLOSE_WINDOW("CLOSE_WINDOW", PacketType.Play.Client.CLOSE_WINDOW),
    CUSTOM_PAYLOAD_IN("CUSTOM_PAYLOAD", PacketType.Play.Client.PLUGIN_MESSAGE),
    DIFFICULTY_CHANGE("DIFFICULTY_CHANGE", PacketType.Play.Client.SET_DIFFICULTY),
    DIFFICULTY_LOCK("DIFFICULTY_LOCK", PacketType.Play.Client.LOCK_DIFFICULTY),
    ENCHANT_ITEM("ENCHANT_ITEM", PacketType.Play.Client.CLICK_WINDOW_BUTTON),
    ENTITY_ACTION_IN("ENTITY_ACTION", PacketType.Play.Client.ENTITY_ACTION),
    ENTITY_NBT_QUERY("ENTITY_NBT_QUERY", PacketType.Play.Client.QUERY_ENTITY_NBT),
    FLYING("FLYING", PacketType.Play.Client.PLAYER_FLYING),
    HELD_ITEM_SLOT_IN("HELD_ITEM_SLOT", PacketType.Play.Client.HELD_ITEM_CHANGE),
    ITEM_NAME("ITEM_NAME", PacketType.Play.Client.NAME_ITEM),
    JIGSAW_GENERATE("JIGSAW_GENERATE", PacketType.Play.Client.GENERATE_STRUCTURE),
    KEEP_ALIVE("KEEP_ALIVE", PacketType.Play.Client.KEEP_ALIVE),
    LOOK("LOOK", PacketType.Play.Client.PLAYER_ROTATION),
    PICK_ITEM("PICK_ITEM", PacketType.Play.Client.PICK_ITEM),
    PONG("PONG", PacketType.Play.Client.PONG),
    POSITION("POSITION", PacketType.Play.Client.PLAYER_POSITION),
    POSITION_LOOK("POSITION_LOOK", PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION),
    RECIPE_DISPLAYED("RECIPE_DISPLAYED", PacketType.Play.Client.SET_DISPLAYED_RECIPE),
    RECIPE_SETTINGS("RECIPE_SETTINGS", PacketType.Play.Client.SET_RECIPE_BOOK_STATE),
    RESOURCE_PACK_STATUS("RESOURCE_PACK_STATUS", PacketType.Play.Client.RESOURCE_PACK_STATUS),
    SETTINGS("SETTINGS", PacketType.Play.Client.CLIENT_SETTINGS),
    SET_COMMAND_BLOCK("SET_COMMAND_BLOCK", PacketType.Play.Client.UPDATE_COMMAND_BLOCK),
    SET_COMMAND_MINECART("SET_COMMAND_MINECART", PacketType.Play.Client.UPDATE_COMMAND_BLOCK_MINECART),
    SET_CREATIVE_SLOT("SET_CREATIVE_SLOT", PacketType.Play.Client.CREATIVE_INVENTORY_ACTION),
    SET_JIGSAW("SET_JIGSAW", PacketType.Play.Client.UPDATE_JIGSAW_BLOCK),
    SPECTATE("SPECTATE", PacketType.Play.Client.SPECTATE),
    STEER_VEHICLE("STEER_VEHICLE", PacketType.Play.Client.STEER_VEHICLE, PacketType.Play.Client.PLAYER_INPUT),
    STRUCT("STRUCT", PacketType.Play.Client.UPDATE_STRUCTURE_BLOCK),
    TAB_COMPLETE_IN("TAB_COMPLETE", PacketType.Play.Client.TAB_COMPLETE),
    TELEPORT_ACCEPT("TELEPORT_ACCEPT", PacketType.Play.Client.TELEPORT_CONFIRM),
    TILE_NBT_QUERY("TILE_NBT_QUERY", PacketType.Play.Client.QUERY_BLOCK_NBT),
    @Deprecated
    TRANSACTION("TRANSACTION", PacketType.Play.Client.WINDOW_CONFIRMATION),
    TR_SEL("TR_SEL", PacketType.Play.Client.SELECT_TRADE),
    UPDATE_SIGN("UPDATE_SIGN", PacketType.Play.Client.UPDATE_SIGN),
    USE_ENTITY("USE_ENTITY", PacketType.Play.Client.INTERACT_ENTITY),
    USE_ITEM("USE_ITEM", PacketType.Play.Client.USE_ITEM),
    USE_ITEM_ON("USE_ITEM_ON", PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT),
    VEHICLE_MOVE("VEHICLE_MOVE", PacketType.Play.Client.VEHICLE_MOVE),
    WINDOW_CLICK("WINDOW_CLICK", PacketType.Play.Client.CLICK_WINDOW),
    ;

    private final String lookupName;
    private final PacketTypeCommon[] packetTypes;

    Client(String lookupName, PacketTypeCommon... packetTypes) {
      this.lookupName = lookupName;
      this.packetTypes = packetTypes;
    }

    public String lookupName() {
      return lookupName;
    }

    public PacketTypeCommon[] packetTypes() {
      return packetTypes.clone();
    }
  }

  public enum Server {
    @Deprecated
    ALL("*"),
    ABILITIES_OUT("ABILITIES", PacketType.Play.Server.PLAYER_ABILITIES),
    ADVANCEMENTS("ADVANCEMENTS", PacketType.Play.Server.UPDATE_ADVANCEMENTS),
    ANIMATION("ANIMATION", PacketType.Play.Server.ENTITY_ANIMATION),
    ATTACH_ENTITY("ATTACH_ENTITY", PacketType.Play.Server.ATTACH_ENTITY),
    AUTO_RECIPE("AUTO_RECIPE", PacketType.Play.Server.CRAFT_RECIPE_RESPONSE),
    BED("BED", PacketType.Play.Server.USE_BED),
    BLOCK_ACTION("BLOCK_ACTION", PacketType.Play.Server.BLOCK_ACTION),
    BLOCK_BREAK("BLOCK_BREAK", PacketType.Play.Server.ACKNOWLEDGE_PLAYER_DIGGING, PacketType.Play.Server.ACKNOWLEDGE_BLOCK_CHANGES),
    BLOCK_BREAK_ANIMATION("BLOCK_BREAK_ANIMATION", PacketType.Play.Server.BLOCK_BREAK_ANIMATION),
    BLOCK_CHANGE("BLOCK_CHANGE", PacketType.Play.Server.BLOCK_CHANGE),
    BLOCK_CHANGED_ACK("BLOCK_CHANGED_ACK", PacketType.Play.Server.ACKNOWLEDGE_BLOCK_CHANGES),
    BOSS("BOSS", PacketType.Play.Server.BOSS_BAR),
    CAMERA("CAMERA", PacketType.Play.Server.CAMERA),
    CHAT_OUT("CHAT", PacketType.Play.Server.CHAT_MESSAGE, PacketType.Play.Server.SYSTEM_CHAT_MESSAGE, PacketType.Play.Server.DISGUISED_CHAT, PacketType.Play.Server.ACTION_BAR),
    CLOSE_WINDOW("CLOSE_WINDOW", PacketType.Play.Server.CLOSE_WINDOW),
    COLLECT("COLLECT", PacketType.Play.Server.COLLECT_ITEM),
    COMBAT_EVENT("COMBAT_EVENT", PacketType.Play.Server.COMBAT_EVENT, PacketType.Play.Server.ENTER_COMBAT_EVENT, PacketType.Play.Server.END_COMBAT_EVENT, PacketType.Play.Server.DEATH_COMBAT_EVENT),
    COMMANDS("COMMANDS", PacketType.Play.Server.DECLARE_COMMANDS),
    CRAFT_PROGRESS_BAR("CRAFT_PROGRESS_BAR", PacketType.Play.Server.WINDOW_PROPERTY),
    CUSTOM_PAYLOAD_OUT("CUSTOM_PAYLOAD", PacketType.Play.Server.PLUGIN_MESSAGE),
    CUSTOM_SOUND_EFFECT("CUSTOM_SOUND_EFFECT", PacketType.Play.Server.SOUND_EFFECT),
    ENTITY("ENTITY", PacketType.Play.Server.ENTITY_MOVEMENT),
    ENTITY_DESTROY("ENTITY_DESTROY", PacketType.Play.Server.DESTROY_ENTITIES),
    ENTITY_EFFECT("ENTITY_EFFECT", PacketType.Play.Server.ENTITY_EFFECT),
    ENTITY_EQUIPMENT("ENTITY_EQUIPMENT", PacketType.Play.Server.ENTITY_EQUIPMENT),
    ENTITY_HEAD_ROTATION("ENTITY_HEAD_ROTATION", PacketType.Play.Server.ENTITY_HEAD_LOOK),
    ENTITY_LOOK("ENTITY_LOOK", PacketType.Play.Server.ENTITY_ROTATION),
    ENTITY_METADATA("ENTITY_METADATA", PacketType.Play.Server.ENTITY_METADATA),
    ENTITY_MOVE_LOOK("ENTITY_MOVE_LOOK", PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION),
    ENTITY_SOUND("ENTITY_SOUND", PacketType.Play.Server.ENTITY_SOUND_EFFECT),
    ENTITY_STATUS("ENTITY_STATUS", PacketType.Play.Server.ENTITY_STATUS),
    ENTITY_TELEPORT("ENTITY_TELEPORT", PacketType.Play.Server.ENTITY_TELEPORT),
    ENTITY_POSITION_SYNC("ENTITY_POSITION_SYNC", PacketType.Play.Server.ENTITY_POSITION_SYNC),
    ENTITY_VELOCITY("ENTITY_VELOCITY", PacketType.Play.Server.ENTITY_VELOCITY),
    EXPERIENCE("EXPERIENCE", PacketType.Play.Server.SET_EXPERIENCE),
    EXPLOSION("EXPLOSION", PacketType.Play.Server.EXPLOSION),
    GAME_STATE_CHANGE("GAME_STATE_CHANGE", PacketType.Play.Server.CHANGE_GAME_STATE),
    HELD_ITEM_SLOT_OUT("HELD_ITEM_SLOT", PacketType.Play.Server.HELD_ITEM_CHANGE),
    KEEP_ALIVE("KEEP_ALIVE", PacketType.Play.Server.KEEP_ALIVE),
    KICK_DISCONNECT("KICK_DISCONNECT", PacketType.Play.Server.DISCONNECT),
    LIGHT_UPDATE("LIGHT_UPDATE", PacketType.Play.Server.UPDATE_LIGHT),
    LOGIN("LOGIN", PacketType.Play.Server.JOIN_GAME),
    LOOK_AT("LOOK_AT", PacketType.Play.Server.FACE_PLAYER),
    MAP("MAP", PacketType.Play.Server.MAP_DATA),
    MAP_CHUNK("MAP_CHUNK", PacketType.Play.Server.CHUNK_DATA),
    MAP_CHUNK_BULK("MAP_CHUNK_BULK", PacketType.Play.Server.MAP_CHUNK_BULK),
    MOUNT("MOUNT", PacketType.Play.Server.SET_PASSENGERS),
    MULTI_BLOCK_CHANGE("MULTI_BLOCK_CHANGE", PacketType.Play.Server.MULTI_BLOCK_CHANGE),
    NAMED_ENTITY_SPAWN("NAMED_ENTITY_SPAWN", PacketType.Play.Server.SPAWN_PLAYER),
    NAMED_SOUND_EFFECT("NAMED_SOUND_EFFECT", PacketType.Play.Server.NAMED_SOUND_EFFECT),
    NBT_QUERY("NBT_QUERY", PacketType.Play.Server.NBT_QUERY_RESPONSE),
    OPEN_BOOK("OPEN_BOOK", PacketType.Play.Server.OPEN_BOOK),
    OPEN_SIGN_EDITOR("OPEN_SIGN_EDITOR", PacketType.Play.Server.OPEN_SIGN_EDITOR),
    OPEN_SIGN_ENTITY("OPEN_SIGN_ENTITY", PacketType.Play.Server.OPEN_SIGN_EDITOR),
    OPEN_WINDOW("OPEN_WINDOW", PacketType.Play.Server.OPEN_WINDOW),
    OPEN_WINDOW_HORSE("OPEN_WINDOW_HORSE", PacketType.Play.Server.OPEN_HORSE_WINDOW),
    OPEN_WINDOW_MERCHANT("OPEN_WINDOW_MERCHANT", PacketType.Play.Server.MERCHANT_OFFERS),
    PING("PING", PacketType.Play.Server.PING),
    PLAYER_INFO("PLAYER_INFO", PacketType.Play.Server.PLAYER_INFO, PacketType.Play.Server.PLAYER_INFO_UPDATE),
    PLAYER_INFO_REMOVE("PLAYER_INFO_REMOVE", PacketType.Play.Server.PLAYER_INFO_REMOVE),
    PLAYER_LIST_HEADER_FOOTER("PLAYER_LIST_HEADER_FOOTER", PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER),
    POSITION("POSITION", PacketType.Play.Server.PLAYER_POSITION_AND_LOOK),
    RECIPES("RECIPES", PacketType.Play.Server.DECLARE_RECIPES),
    RECIPE_UPDATE("RECIPE_UPDATE", PacketType.Play.Server.RECIPE_BOOK_ADD, PacketType.Play.Server.RECIPE_BOOK_REMOVE, PacketType.Play.Server.RECIPE_BOOK_SETTINGS),
    REL_ENTITY_MOVE("REL_ENTITY_MOVE", PacketType.Play.Server.ENTITY_RELATIVE_MOVE),
    REL_ENTITY_MOVE_LOOK("REL_ENTITY_MOVE_LOOK", PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION),
    REMOVE_ENTITY_EFFECT("REMOVE_ENTITY_EFFECT", PacketType.Play.Server.REMOVE_ENTITY_EFFECT),
    RESOURCE_PACK_SEND("RESOURCE_PACK_SEND", PacketType.Play.Server.RESOURCE_PACK_SEND),
    RESPAWN("RESPAWN", PacketType.Play.Server.RESPAWN),
    SCOREBOARD_DISPLAY_OBJECTIVE("SCOREBOARD_DISPLAY_OBJECTIVE", PacketType.Play.Server.DISPLAY_SCOREBOARD),
    SCOREBOARD_OBJECTIVE("SCOREBOARD_OBJECTIVE", PacketType.Play.Server.SCOREBOARD_OBJECTIVE),
    SCOREBOARD_SCORE("SCOREBOARD_SCORE", PacketType.Play.Server.UPDATE_SCORE, PacketType.Play.Server.RESET_SCORE),
    SCOREBOARD_TEAM("SCOREBOARD_TEAM", PacketType.Play.Server.TEAMS),
    SELECT_ADVANCEMENT_TAB("SELECT_ADVANCEMENT_TAB", PacketType.Play.Server.SELECT_ADVANCEMENTS_TAB),
    SERVER_DIFFICULTY("SERVER_DIFFICULTY", PacketType.Play.Server.SERVER_DIFFICULTY),
    SET_COMPRESSION("SET_COMPRESSION", PacketType.Play.Server.SET_COMPRESSION),
    SET_COOLDOWN("SET_COOLDOWN", PacketType.Play.Server.SET_COOLDOWN),
    SET_SLOT("SET_SLOT", PacketType.Play.Server.SET_SLOT),
    SPAWN_ENTITY("SPAWN_ENTITY", PacketType.Play.Server.SPAWN_ENTITY),
    SPAWN_ENTITY_EXPERIENCE_ORB("SPAWN_ENTITY_EXPERIENCE_ORB", PacketType.Play.Server.SPAWN_EXPERIENCE_ORB),
    SPAWN_ENTITY_LIVING("SPAWN_ENTITY_LIVING", PacketType.Play.Server.SPAWN_LIVING_ENTITY),
    SPAWN_ENTITY_PAINTING("SPAWN_ENTITY_PAINTING", PacketType.Play.Server.SPAWN_PAINTING),
    SPAWN_ENTITY_WEATHER("SPAWN_ENTITY_WEATHER", PacketType.Play.Server.SPAWN_WEATHER_ENTITY),
    SPAWN_POSITION("SPAWN_POSITION", PacketType.Play.Server.SPAWN_POSITION),
    STATISTIC("STATISTIC", PacketType.Play.Server.STATISTICS),
    STATISTICS("STATISTICS", PacketType.Play.Server.STATISTICS),
    STOP_SOUND("STOP_SOUND", PacketType.Play.Server.STOP_SOUND),
    TAB_COMPLETE_OUT("TAB_COMPLETE", PacketType.Play.Server.TAB_COMPLETE),
    TAGS("TAGS", PacketType.Play.Server.TAGS),
    TILE_ENTITY_DATA("TILE_ENTITY_DATA", PacketType.Play.Server.BLOCK_ENTITY_DATA),
    TITLE("TITLE", PacketType.Play.Server.TITLE, PacketType.Play.Server.SET_TITLE_TEXT, PacketType.Play.Server.SET_TITLE_SUBTITLE, PacketType.Play.Server.SET_TITLE_TIMES, PacketType.Play.Server.CLEAR_TITLES),
    @Deprecated
    TRANSACTION("TRANSACTION", PacketType.Play.Server.WINDOW_CONFIRMATION),
    UNLOAD_CHUNK("UNLOAD_CHUNK", PacketType.Play.Server.UNLOAD_CHUNK),
    UPDATE_ATTRIBUTES("UPDATE_ATTRIBUTES", PacketType.Play.Server.UPDATE_ATTRIBUTES),
    UPDATE_ENTITY_NBT("UPDATE_ENTITY_NBT", PacketType.Play.Server.UPDATE_ENTITY_NBT),
    UPDATE_HEALTH("UPDATE_HEALTH", PacketType.Play.Server.UPDATE_HEALTH),
    UPDATE_SIGN("UPDATE_SIGN", PacketType.Play.Server.UPDATE_SIGN),
    UPDATE_TIME("UPDATE_TIME", PacketType.Play.Server.TIME_UPDATE),
    USE_BED("USE_BED", PacketType.Play.Server.USE_BED),
    VEHICLE_MOVE("VEHICLE_MOVE", PacketType.Play.Server.VEHICLE_MOVE),
    VIEW_CENTRE("VIEW_CENTRE", PacketType.Play.Server.UPDATE_VIEW_POSITION),
    VIEW_DISTANCE("VIEW_DISTANCE", PacketType.Play.Server.UPDATE_VIEW_DISTANCE),
    WINDOW_DATA("WINDOW_DATA", PacketType.Play.Server.WINDOW_PROPERTY),
    WINDOW_ITEMS("WINDOW_ITEMS", PacketType.Play.Server.WINDOW_ITEMS),
    WORLD_BORDER("WORLD_BORDER", PacketType.Play.Server.WORLD_BORDER, PacketType.Play.Server.WORLD_BORDER_CENTER, PacketType.Play.Server.WORLD_BORDER_SIZE, PacketType.Play.Server.WORLD_BORDER_LERP_SIZE, PacketType.Play.Server.WORLD_BORDER_WARNING_DELAY, PacketType.Play.Server.WORLD_BORDER_WARNING_REACH),
    WORLD_EVENT("WORLD_EVENT", PacketType.Play.Server.EFFECT),
    WORLD_PARTICLES("WORLD_PARTICLES", PacketType.Play.Server.PARTICLE),

    ;

    private final String lookupName;
    private final PacketTypeCommon[] packetTypes;

    Server(String lookupName, PacketTypeCommon... packetTypes) {
      this.lookupName = lookupName;
      this.packetTypes = packetTypes;
    }

    public String lookupName() {
      return lookupName;
    }

    public PacketTypeCommon[] packetTypes() {
      return packetTypes.clone();
    }
  }
}
