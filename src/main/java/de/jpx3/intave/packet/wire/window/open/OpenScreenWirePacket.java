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

package de.jpx3.intave.packet.wire.window.open;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.immutable.OpenScreenPacket;
import de.jpx3.intave.packet.contract.mutable.MutableWindowAddressed;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

public abstract class OpenScreenWirePacket extends AbstractMutable implements OpenScreenPacket, MutableWindowAddressed {
	static final StreamCodec<ByteBuf, ByteBuf, String> JSON_TITLE = ByteBufStreamCodecs.stringCodec(262144);
	private int containerId;

	OpenScreenWirePacket(int containerId) {
		this.containerId = containerId;
	}

	@Override
	public final int containerId() {
		return containerId;
	}

	@Override
	public final void setContainerId(int containerId) {
		markMutated();
		this.containerId = containerId;
	}
}
