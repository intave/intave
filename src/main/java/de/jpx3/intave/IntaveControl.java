package de.jpx3.intave;

import de.jpx3.intave.module.nayoro.OperationalMode;

import java.util.Arrays;
import java.util.List;

import static de.jpx3.intave.IntaveBuildConfig.*;
import static de.jpx3.intave.module.nayoro.OperationalMode.CLOUD_STORAGE;
import static de.jpx3.intave.module.nayoro.OperationalMode.LOCAL_STORAGE;

public final class IntaveControl {
  public static final boolean APPLY_GLOBAL_LOW_TRUSTFACTOR = false;
  public static final boolean DEBUG_MOVEMENT = false;
  public static final boolean DEBUG_EMULATION = false;
  public static final boolean DEBUG_HEURISTICS = false;
  public static final boolean DEBUG_INTERACTION = false;
  public static final boolean DEBUG_INTERACTION_TRUST_CHAIN = false;
  public static final boolean DEBUG_INTERACTION_REFRESHES = false;
  public static final boolean DEBUG_INTERACTION_PACKET_ROUTING = false;
  public static final boolean DEBUG_INTERACTION_DISCREET = true;
  public static final List<String> INTERACTION_DEBUG_NAMES = Arrays.asList("Jpx3", "aesthatic", "Richy", "egirlAllergiker");
  public static final boolean DEBUG_STUCK_REVIVAL = false;
  public static final boolean LATENCY_PING_AS_XP_LEVEL = false;
  public static boolean DEBUG_MOVEMENT_IGNORE = false; // if SG
  public static final boolean DEBUG_MOUNTING = false;
  public static final boolean DEBUG_ELYTRA = false;
  public static final boolean DEBUG_PLAYER_ACTIONS = false;
  public static boolean DEBUG_TELEPORT_LOCKS = false; // if SG
  public static final boolean DEBUG_TELEPORT_CAUSE_AND_CAUSER = false;
  public static final boolean DEBUG_TELEPORT_PACKET_STACKTRACE = false;
  public static final boolean TELEPORT_FAR_AWAY_ON_Q_PRESS = false;
  public static final boolean DEBUG_INTAVE_TELEPORT_EVENT_CANCELS = false;
  public static final boolean DISABLE_BLOCK_CACHING_ENTIRELY = false;
  public static final boolean BLOCK_CACHE_DEBUG = false;
  public static final boolean IGNORE_CHUNK_PACKETS = false;
  public static final boolean USE_MINING_STRATEGIES = false;
  public static final boolean DISABLE_AUTOCLICKER_CHECK = true;
  public static final boolean RESET_HURT_TIME_ON_JOIN = true;
  public static final boolean IGNORE_CACHE_REFRESH_ON_SIMULATION_FAULT = false;
  public static final boolean SIBYL_DEBUG = false;
  public static final boolean SIBYL_ALLOW_ALL = false;
  public static final boolean ENABLE_MEMTRACE = false;
  public static final boolean NETTY_DUMP_ON_TIMEOUT = false;
  public static final boolean FILL_UFOE_STACKTRACE = false;
  public static final boolean USE_TIMINGS = true;
  public static final boolean DUMP_BLOCK_HITBOX_ON_RIGHT_CLICK = false;
  public static final boolean SETBACK_WITH_PRESSED_KEYS = false;
  public static final boolean CLICKPATTERNS_OUTPUT = false;
  public static final boolean DEBUG_GRAYLIST = false;
  public static final boolean DEBUG_BLUELIST = false;
  public static final boolean DEBUG_CMS = false;
  public static final boolean USE_DEBUG_LOCATE_RESOURCE = false;
  public static final boolean USE_DEBUG_TRUSTFACTOR_RESOURCE = false;
  public static final boolean USE_DEBUG_SCRIPT_RESOURCES = false;
  public static final boolean DEBUG_OUTPUT_FOR_TESTS = false;
  public static final boolean DEBUG_VARIANT_COMPILATION = false;
  public static final boolean DEBUG_ENTITY_TRACKING = false;
  public static final boolean DEBUG_ATTACK_DAMAGE_MODIFIERS = false;
  public static final boolean DEBUG_MOVEMENT_BLOCK_FALLEN_UPON = false; // spams on testing
  public static final boolean GIVE_RIPTIDE_V_TRIDENT_ON_JOIN = false;
  public static final boolean DEBUG_FEEDBACK_PACKETS = false;
  public static final boolean DEBUG_AMOUNT_FEEDBACK_XP = false;
  public static final boolean DEBUG_ITEM_USAGE = false;
  public static final boolean DEBUG_VELOCITY_RECEIVE = false;
  public static final boolean DEBUG_PLACE_AND_BREAK_PERMISSIONS = false;
  public static final boolean DEBUG_SERVER_VERSION = false;
  public static final boolean CLOUD_LOCALHOST_MASTER_SHARD = !PRODUCTION && !GOMME;
  public static final boolean REPLACE_JOAP_SETBACK_WITH_CM = true;
  public static final boolean DISALLOW_ALL_BLOCK_PLACEMENTS = false;
  public static final boolean DISALLOW_ALL_BLOCK_PLACEMENTS_WITH_EVENT = false;
  public static final boolean ENABLE_MOVEMENT_DEBUGGER_COLLECTOR = false;
  public static final boolean MOVEMENT_DEBUGGER_COLLECTOR_POSTTICK_OUTPUT = false;
  public static final boolean AUTHENTICATION_DEBUG_MODE = AUTHTEST;

  public static final OperationalMode SAMPLE_OPERATIONAL_MODE = GOMME ? /*GOMME_UPLOAD*/CLOUD_STORAGE : (PRODUCTION ? CLOUD_STORAGE : LOCAL_STORAGE);

  public static final boolean USE_EXTERNAL_CONFIGURATION_FILE = !PRODUCTION;
  public static final boolean GOMME_MODE = GOMME;
  public static final boolean DISABLE_LICENSE_CHECK = !PRODUCTION;
  public static final boolean AUTHENTICATION_INSPECTION_MODE = AUTHTEST;
}