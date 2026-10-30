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

package de.jpx3.intave.packet.wire.player.experience;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableExperiencePacket;
import io.netty.buffer.ByteBuf;

public class ExperienceWirePacket implements MutableExperiencePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ExperienceWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.FLOAT, ExperienceWirePacket::progress,
			ByteBufStreamCodecs.VARINT, ExperienceWirePacket::level,
			ByteBufStreamCodecs.VARINT, ExperienceWirePacket::totalExperience,
			ExperienceWirePacket::new
		);

	protected float progress = 0;

	protected int level = 0;

	protected int totalExperience = 0;

	public ExperienceWirePacket() {
		this(0, 0, 0);
	}

	public ExperienceWirePacket(float progress, int level, int totalExperience) {
		this.progress = progress;
		this.level = level;
		this.totalExperience = totalExperience;
	}

	@Override
	public float progress() {
		return this.progress;
	}

	@Override
	public void setProgress(float value) {
		this.progress = value;
	}

	@Override
	public int level() {
		return this.level;
	}

	@Override
	public void setLevel(int value) {
		this.level = value;
	}

	@Override
	public int totalExperience() {
		return this.totalExperience;
	}

	@Override
	public void setTotalExperience(int value) {
		this.totalExperience = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("EXPERIENCE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}
}
