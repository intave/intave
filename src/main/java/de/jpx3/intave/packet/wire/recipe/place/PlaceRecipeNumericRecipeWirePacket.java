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

package de.jpx3.intave.packet.wire.recipe.place;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class PlaceRecipeNumericRecipeWirePacket extends PlaceRecipeWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlaceRecipeNumericRecipeWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.SIGNED_BYTE, PlaceRecipeWirePacket::containerId,
			ByteBufStreamCodecs.VARINT, PlaceRecipeWirePacket::recipeId,
			ByteBufStreamCodecs.BOOLEAN, PlaceRecipeWirePacket::placeAll,
			PlaceRecipeNumericRecipeWirePacket::new
		);

	public PlaceRecipeNumericRecipeWirePacket() {
		super();
	}

	public PlaceRecipeNumericRecipeWirePacket(int containerId, int recipeId, boolean placeAll) {
		super(containerId, recipeId, placeAll);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 335 && protocolVersion < 393;
	}
}
