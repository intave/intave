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

import de.jpx3.intave.share.MinecraftKey;

import java.util.List;
import java.util.Map;

/**
 * Engine neutral view of an outbound update tags packet.
 * <p>
 * Mirrors {@link MovementView} and {@link EntityVelocityView}: the only thing the tag consumer ever
 * asks for is the decoded block tag table, so that is the only operation exposed here. Both
 * backends normalise the wire differences away - the packet carries whole registry sections on
 * modern versions and four fixed sections on legacy ones, and the tag members are block ids on the
 * wire but native block handles in the legacy ProtocolLib decode - so the consumer always sees
 * resolved {@link MinecraftKey}s.
 */
public interface UpdateTagView {

  /**
   * Decodes the {@code minecraft:block} tag section.
   *
   * @return tag key to the block keys that tag contains; empty when the packet carries no block
   * tags or the section could not be decoded. Members that do not resolve against the block
   * registry are dropped.
   */
  Map<MinecraftKey, List<MinecraftKey>> readTags();
}
