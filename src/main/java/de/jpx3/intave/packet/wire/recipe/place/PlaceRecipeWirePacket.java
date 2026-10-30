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
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutablePlaceRecipePacket;
import io.netty.buffer.ByteBuf;

public class PlaceRecipeWirePacket implements MutablePlaceRecipePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlaceRecipeWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, PlaceRecipeWirePacket::containerId,
			ByteBufStreamCodecs.VARINT, PlaceRecipeWirePacket::recipeId,
			ByteBufStreamCodecs.BOOLEAN, PlaceRecipeWirePacket::placeAll,
			PlaceRecipeWirePacket::new
		);

	protected int containerId = 0;

	protected boolean placeAll = false;

	protected int recipeId;

	public PlaceRecipeWirePacket() {
		this(0, 0, false);
	}

	public PlaceRecipeWirePacket(int containerId, int recipeId, boolean placeAll) {
		this.containerId = containerId;
		this.recipeId = recipeId;
		this.placeAll = placeAll;
	}

	@Override
	public int containerId() {
		return this.containerId;
	}

	@Override
	public void setContainerId(int value) {
		this.containerId = value;
	}

	@Override
	public boolean placeAll() {
		return this.placeAll;
	}

	@Override
	public void setPlaceAll(boolean value) {
		this.placeAll = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("AUTO_RECIPE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768;
	}

	public int recipeId() {
		return recipeId;
	}
}
