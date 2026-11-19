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

package de.jpx3.intave.share;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.JsonStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;

import java.util.List;

import static de.jpx3.intave.codec.JsonStreamCodecs.field;
import static de.jpx3.intave.codec.JsonStreamCodecs.object;

public final class PositionedBlockState {
  public static final StreamCodec<JsonReader, JsonWriter, PositionedBlockState> JSON_CODEC = object(
    field("position", BlockPosition.JSON_CODEC, PositionedBlockState::position, BlockPosition.ORIGIN),
    field("state", BlockCacheEntry.JSON_CODEC, PositionedBlockState::state, BlockCacheEntry.empty()),
    PositionedBlockState::new
  );
  public static final StreamCodec<JsonReader, JsonWriter, List<PositionedBlockState>> LIST_JSON_CODEC =
    JsonStreamCodecs.listCodecOf(JSON_CODEC);

  private final BlockPosition position;
  private final BlockCacheEntry state;

  public PositionedBlockState(BlockPosition position, BlockCacheEntry state) {
    this.position = position;
    this.state = state;
  }

  public BlockPosition position() {
    return position;
  }

  public BlockCacheEntry state() {
    return state;
  }
}
