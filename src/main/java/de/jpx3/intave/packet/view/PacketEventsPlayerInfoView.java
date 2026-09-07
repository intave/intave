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

package de.jpx3.intave.packet.view;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * {@link PlayerInfoView} backed by PacketEvents.
 * <p>
 * PacketEvents splits what ProtocolLib exposes as two packet types into three wrappers, because it
 * models the pre and post 1.19.3 player info packets separately rather than normalising them:
 * <ul>
 *   <li>{@link WrapperPlayServerPlayerInfo} - legacy, one action and a list of profile entries,</li>
 *   <li>{@link WrapperPlayServerPlayerInfoUpdate} - 1.19.3+, a set of actions and a list of entries,</li>
 *   <li>{@link WrapperPlayServerPlayerInfoRemove} - 1.19.3+, a bare list of profile ids.</li>
 * </ul>
 * All three collapse back into one view here, so the vanish filter sees the same shape it sees on
 * ProtocolLib.
 * <p>
 * The entry list is copied into a mutable list at construction - a wrapper built from the varargs
 * constructor can hand out a fixed size list - and written back in {@link #release()} only when it
 * was actually modified, so an untouched packet is never re-encoded.
 * <p>
 * Beyond the interface this class also offers {@link #appendLegacyEntry(UUID, String, GameMode,
 * int)}, the one entry <i>write</i> in this family; see its comment for why it does not belong on
 * {@link PlayerInfoView}.
 */
public final class PacketEventsPlayerInfoView implements PlayerInfoView {

  private final PacketSendEvent event;
  private final Set<Action> actions;

  /** Exactly one of the three wrappers is non-null; it decides which entry list is live. */
  private final WrapperPlayServerPlayerInfo legacyWrapper;
  private final List<WrapperPlayServerPlayerInfo.PlayerData> legacyEntries;

  private final WrapperPlayServerPlayerInfoUpdate updateWrapper;
  private final List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> updateEntries;

  private final WrapperPlayServerPlayerInfoRemove removeWrapper;
  private final List<UUID> removedIds;

  private boolean dirty;

  private PacketEventsPlayerInfoView(
    PacketSendEvent event,
    Set<Action> actions,
    WrapperPlayServerPlayerInfo legacyWrapper,
    List<WrapperPlayServerPlayerInfo.PlayerData> legacyEntries,
    WrapperPlayServerPlayerInfoUpdate updateWrapper,
    List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> updateEntries,
    WrapperPlayServerPlayerInfoRemove removeWrapper,
    List<UUID> removedIds
  ) {
    this.event = event;
    this.actions = actions;
    this.legacyWrapper = legacyWrapper;
    this.legacyEntries = legacyEntries;
    this.updateWrapper = updateWrapper;
    this.updateEntries = updateEntries;
    this.removeWrapper = removeWrapper;
    this.removedIds = removedIds;
  }

  /** @return a view over the event, or null when the packet is not a player info packet. */
  public static PacketEventsPlayerInfoView of(PacketSendEvent event) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Server.PLAYER_INFO) {
      WrapperPlayServerPlayerInfo wrapper = new WrapperPlayServerPlayerInfo(event);
      return new PacketEventsPlayerInfoView(
        event,
        translateLegacy(wrapper.getAction()),
        wrapper,
        new ArrayList<>(wrapper.getPlayerDataList()),
        null, null, null, null
      );
    }
    if (type == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
      WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
      return new PacketEventsPlayerInfoView(
        event,
        translateUpdate(wrapper.getActions()),
        null, null,
        wrapper,
        new ArrayList<>(wrapper.getEntries()),
        null, null
      );
    }
    if (type == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
      WrapperPlayServerPlayerInfoRemove wrapper = new WrapperPlayServerPlayerInfoRemove(event);
      return new PacketEventsPlayerInfoView(
        event,
        Collections.emptySet(),
        null, null, null, null,
        wrapper,
        new ArrayList<>(wrapper.getProfileIds())
      );
    }
    return null;
  }

  private static Set<Action> translateLegacy(WrapperPlayServerPlayerInfo.Action action) {
    if (action == null) {
      return Collections.emptySet();
    }
    switch (action) {
      case ADD_PLAYER:
        return Collections.singleton(Action.ADD_PLAYER);
      case UPDATE_GAME_MODE:
        return Collections.singleton(Action.UPDATE_GAME_MODE);
      case UPDATE_LATENCY:
        return Collections.singleton(Action.UPDATE_LATENCY);
      case REMOVE_PLAYER:
        return Collections.singleton(Action.REMOVE_PLAYER);
      default:
        return Collections.singleton(Action.OTHER);
    }
  }

  private static Set<Action> translateUpdate(Set<WrapperPlayServerPlayerInfoUpdate.Action> read) {
    if (read == null || read.isEmpty()) {
      return Collections.emptySet();
    }
    // Insertion ordered so the action order the packet carried survives the translation.
    Set<Action> actions = new LinkedHashSet<>(read.size());
    for (WrapperPlayServerPlayerInfoUpdate.Action action : read) {
      switch (action) {
        case ADD_PLAYER:
          actions.add(Action.ADD_PLAYER);
          break;
        case UPDATE_GAME_MODE:
          actions.add(Action.UPDATE_GAME_MODE);
          break;
        case UPDATE_LATENCY:
          actions.add(Action.UPDATE_LATENCY);
          break;
        default:
          // Display name, listed flag, chat initialisation: nothing branches on these. The update
          // packet has no remove action - removals travel in their own packet since 1.19.3.
          actions.add(Action.OTHER);
          break;
      }
    }
    return actions;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public boolean isRemoval() {
    return removeWrapper != null;
  }

  @Override
  public Set<Action> actions() {
    return actions;
  }

  @Override
  public List<UUID> entryIds() {
    if (removeWrapper != null) {
      return Collections.unmodifiableList(removedIds);
    }
    if (updateWrapper != null) {
      List<UUID> ids = new ArrayList<>(updateEntries.size());
      for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : updateEntries) {
        ids.add(entry.getProfileId());
      }
      return ids;
    }
    List<UUID> ids = new ArrayList<>(legacyEntries.size());
    for (WrapperPlayServerPlayerInfo.PlayerData entry : legacyEntries) {
      ids.add(entry.getUserProfile().getUUID());
    }
    return ids;
  }

  /**
   * @return true when this view sits on the pre 1.19.3 player info packet, the only one of the
   * three that carries a single action and full profile entries. Consumers that write an entry
   * rather than just dropping one have to branch on this, because the three wrappers do not share
   * an entry type.
   */
  public boolean isLegacy() {
    return legacyWrapper != null;
  }

  /**
   * Appends one entry to the legacy player info packet - the counterpart of dropping entries, and
   * the only write this family performs on an entry rather than on the entry list.
   * <p>
   * Deliberately outside {@link PlayerInfoView}: the 1.19.3+ wrappers model an entry completely
   * differently, and the only caller (the fake player's tab list latency row) is legacy only
   * anyway, so there is nothing for the other backends to implement.
   * <p>
   * The display name is passed as a plain text component, mirroring the
   * {@code WrappedChatComponent.fromText(name)} the ProtocolLib caller builds. Texture properties
   * are not carried: the packet body of every action except {@code ADD_PLAYER} is profile id plus
   * that action's own field, so nothing but the id and the entry's own payload ever reaches
   * the wire here.
   *
   * @return true when the entry was appended, false when this is not the legacy packet.
   */
  public boolean appendLegacyEntry(UUID profileId, String name, GameMode gameMode, int latency) {
    if (legacyWrapper == null || profileId == null) {
      return false;
    }
    legacyEntries.add(new WrapperPlayServerPlayerInfo.PlayerData(
      Component.text(name == null ? "" : name),
      new UserProfile(profileId, name),
      gameMode,
      latency
    ));
    dirty = true;
    return true;
  }

  @Override
  public void removeEntriesIf(Predicate<UUID> filter) {
    if (removeWrapper != null) {
      dirty |= removedIds.removeIf(filter);
    } else if (updateWrapper != null) {
      dirty |= updateEntries.removeIf(entry -> filter.test(entry.getProfileId()));
    } else {
      dirty |= legacyEntries.removeIf(entry -> filter.test(entry.getUserProfile().getUUID()));
    }
  }

  @Override
  public void shuffleEntries() {
    List<?> entries = entries();
    if (entries.size() < 2) {
      return;
    }
    Collections.shuffle(entries);
    dirty = true;
  }

  @Override
  public boolean entriesEmpty() {
    return entries().isEmpty();
  }

  private List<?> entries() {
    if (removeWrapper != null) {
      return removedIds;
    }
    return updateWrapper != null ? updateEntries : legacyEntries;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public void release() {
    if (!dirty) {
      return;
    }
    if (removeWrapper != null) {
      removeWrapper.setProfileIds(removedIds);
    } else if (updateWrapper != null) {
      updateWrapper.setEntries(updateEntries);
    } else {
      legacyWrapper.setPlayerDataList(legacyEntries);
    }
    event.markForReEncode(true);
    dirty = false;
  }
}
