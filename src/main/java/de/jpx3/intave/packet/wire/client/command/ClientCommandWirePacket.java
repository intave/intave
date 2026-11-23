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

package de.jpx3.intave.packet.wire.client.command;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableClientCommandPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.ClientCommand;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.share.ClientCommand.*;

public final class ClientCommandWirePacket {
	private ClientCommandWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol335.class,
			Protocol775.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableClientCommandPacket {
		protected final Format format;
		protected ClientCommand command;

		protected Base() {
			this(PERFORM_RESPAWN);
		}

		protected Base(ClientCommand command) {
			this(Format.LEGACY, command);
		}

		private Base(Format format, ClientCommand command) {
			format.idOf(command);
			this.format = format;
			this.command = command;
		}

		@Override
		public ClientCommand command() {
			return this.command;
		}

		@Override
		public void setCommand(ClientCommand value) {
			markMutated();
			format.idOf(value);
			this.command = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= format.firstProtocol && protocolVersion < format.endProtocol;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			Format.LEGACY.codec(Protocol47::new);

		public Protocol47() {
			super();
		}

		public Protocol47(ClientCommand command) {
			super(command);
		}

		private Protocol47(Format format, ClientCommand command) {
			super(format, command);
		}
	}

	static final class Protocol335 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol335> STREAM_CODEC = Format.BASIC.codec(Protocol335::new);

		public Protocol335() {
			this(PERFORM_RESPAWN);
		}

		private Protocol335(ClientCommand command) {
			super(Format.BASIC, command);
		}
	}

	static final class Protocol775 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol775> STREAM_CODEC =
			Format.GAME_RULES.codec(Protocol775::new);

		public Protocol775() {
			this(PERFORM_RESPAWN);
		}

		private Protocol775(ClientCommand command) {
			super(Format.GAME_RULES, command);
		}
	}

	private enum Format {
		LEGACY(47, 335, PERFORM_RESPAWN, REQUEST_STATS, OPEN_INVENTORY),
		BASIC(335, 775, PERFORM_RESPAWN, REQUEST_STATS),
		GAME_RULES(775, Integer.MAX_VALUE, PERFORM_RESPAWN, REQUEST_STATS, REQUEST_GAMERULE_VALUES);

		private final int firstProtocol, endProtocol;
		private final List<ClientCommand> commands;

		Format(int firstProtocol, int endProtocol, ClientCommand... commands) {
			this.firstProtocol = firstProtocol;
			this.endProtocol = endProtocol;
			this.commands = Arrays.asList(commands);
		}

		private int idOf(ClientCommand command) {
			int id = commands.indexOf(command);
			if (id < 0) throw new IllegalArgumentException("Unsupported client command " + command + " for " + this);
			return id;
		}

		private ClientCommand byId(int id) {
			if (id < 0 || id >= commands.size()) throw new IllegalArgumentException("Invalid client command ID " + id + " for " + this);
			return commands.get(id);
		}

		private <P extends Base> StreamCodec<ByteBuf, ByteBuf, P> codec(Function<ClientCommand, P> factory) {
			return VARINT.beforeAndAfter(this::byId, this::idOf).beforeAndAfter(factory, Base::command);
		}
	}
}
