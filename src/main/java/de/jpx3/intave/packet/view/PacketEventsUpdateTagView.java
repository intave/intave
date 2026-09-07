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
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTags;
import de.jpx3.intave.registry.BlockRegistry;
import de.jpx3.intave.share.MinecraftKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link UpdateTagView} backed by PacketEvents.
 * <p>
 * Where the ProtocolLib reader has to walk the native packet reflectively to find the block
 * section, PacketEvents hands over an already decoded {@code registry -> tags} map, so this view
 * only has to pick the {@code minecraft:block} registry out of it and resolve the tag members.
 * PacketEvents synthesises the same registry key on legacy versions - the pre 1.17 packet has four
 * fixed sections and the wrapper stores them under {@code minecraft:block}, {@code minecraft:item},
 * {@code minecraft:fluid} and {@code minecraft:entity_type} - so a single lookup covers every
 * protocol version.
 * <p>
 * Tag members are always block ids here, on every version, because the wire encodes var ints;
 * the legacy native-block-handle case the ProtocolLib reader has to cope with cannot occur.
 */
public final class PacketEventsUpdateTagView implements UpdateTagView {

  private static final String BLOCK_REGISTRY_NAMESPACE = "minecraft";
  private static final String BLOCK_REGISTRY_PATH = "block";

  private final WrapperPlayServerTags wrapper;

  private PacketEventsUpdateTagView(WrapperPlayServerTags wrapper) {
    this.wrapper = wrapper;
  }

  /** @return a view over the event, or null when the packet is not an update tags packet. */
  public static PacketEventsUpdateTagView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.TAGS) {
      return null;
    }
    return new PacketEventsUpdateTagView(new WrapperPlayServerTags(event));
  }

  @Override
  public Map<MinecraftKey, List<MinecraftKey>> readTags() {
    Map<MinecraftKey, List<MinecraftKey>> tags = new HashMap<>();

    Map<ResourceLocation, List<WrapperPlayServerTags.Tag>> tagMap = wrapper.getTagMap();
    if (tagMap == null || tagMap.isEmpty()) {
      return tags;
    }

    List<WrapperPlayServerTags.Tag> blockTags = blockTags(tagMap);
    if (blockTags == null || blockTags.isEmpty()) {
      return tags;
    }

    // Resolved lazily, exactly like the ProtocolLib reader: the registry is only touched once the
    // packet is known to carry block tags.
    BlockRegistry blockRegistry = BlockRegistry.global();
    for (WrapperPlayServerTags.Tag tag : blockTags) {
      if (tag == null) {
        continue;
      }
      MinecraftKey tagKey = minecraftKey(tag.getKey());
      if (tagKey == null) {
        continue;
      }

      List<MinecraftKey> values = new ArrayList<>();
      List<Integer> members = tag.getValues();
      if (members != null) {
        for (Integer member : members) {
          if (member == null) {
            continue;
          }
          MinecraftKey value = blockRegistry.keyById(member);
          if (value != null) {
            values.add(value);
          }
        }
      }
      tags.put(tagKey, values);
    }
    return tags;
  }

  private static List<WrapperPlayServerTags.Tag> blockTags(
    Map<ResourceLocation, List<WrapperPlayServerTags.Tag>> tagMap
  ) {
    for (Map.Entry<ResourceLocation, List<WrapperPlayServerTags.Tag>> registry : tagMap.entrySet()) {
      if (isBlockRegistryKey(registry.getKey())) {
        return registry.getValue();
      }
    }
    return null;
  }

  private static boolean isBlockRegistryKey(ResourceLocation registry) {
    return registry != null
      && BLOCK_REGISTRY_NAMESPACE.equals(registry.getNamespace())
      && BLOCK_REGISTRY_PATH.equals(registry.getKey());
  }

  private static MinecraftKey minecraftKey(ResourceLocation location) {
    if (location == null) {
      return null;
    }
    String namespace = location.getNamespace();
    String path = location.getKey();
    if (namespace == null || path == null) {
      return null;
    }
    return new MinecraftKey(namespace, path);
  }
}
