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

package de.jpx3.intave.packet.event;

import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.mutable.Mutable;
import de.jpx3.intave.user.User;
import org.bukkit.event.Cancellable;

public abstract class PacketEvent implements AutoCloseable, Cancellable {
	private User user;
	private PacketContract packet;
	private boolean dirty;
	private boolean cancelled;

	public PacketEvent(User user, PacketContract packet) {
		this.user = user;
		this.packet = packet;
	}

	public abstract PacketDirection direction();

	public User user() {
		return user;
	}

	public PacketContract packet() {
		return packet;
	}

	public void setPacket(PacketContract newPacket) {
		this.packet = newPacket;
		this.dirty = true;
	}

	public boolean dirty() {
		return dirty || packet instanceof Mutable && ((Mutable) packet).couldHaveBeenMutated();
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean newCancelled) {
		cancelled = newCancelled;
	}

	@Override
	public void close() throws Exception {
		user = null;
		packet = null;
	}
}
