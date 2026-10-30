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

public final class PlaceRecipeKeyRecipeWirePacket extends PlaceRecipeWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlaceRecipeKeyRecipeWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.SIGNED_BYTE, PlaceRecipeWirePacket::containerId,
			ByteBufStreamCodecs.VARINT_STRING, PlaceRecipeKeyRecipeWirePacket::recipeKey,
			ByteBufStreamCodecs.BOOLEAN, PlaceRecipeWirePacket::placeAll,
			PlaceRecipeKeyRecipeWirePacket::new
		);

	private String recipeKey = "minecraft:air";

	public PlaceRecipeKeyRecipeWirePacket() {
		super();
	}

	public PlaceRecipeKeyRecipeWirePacket(int containerId, String recipeKey, boolean placeAll) {
		super(containerId, 0, placeAll);
		this.recipeKey = recipeKey;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 393 && protocolVersion < 768;
	}

	public String recipeKey() {
		return recipeKey;
	}
}
