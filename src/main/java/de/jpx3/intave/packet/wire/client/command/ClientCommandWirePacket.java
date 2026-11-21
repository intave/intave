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
import java.util.List;
import java.util.function.Function;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.share.ClientCommand.*;

public class ClientCommandWirePacket extends AbstractMutable implements MutableClientCommandPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ClientCommandWirePacket> STREAM_CODEC =
		Format.LEGACY.codec(ClientCommandWirePacket::new);

	private final Format format;
	protected ClientCommand command;

	public ClientCommandWirePacket() {
		this(PERFORM_RESPAWN);
	}

	public ClientCommandWirePacket(ClientCommand command) {
		this(Format.LEGACY, command);
	}

	private ClientCommandWirePacket(Format format, ClientCommand command) {
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

		private <P extends ClientCommandWirePacket> StreamCodec<ByteBuf, ByteBuf, P> codec(Function<ClientCommand, P> factory) {
			return VARINT.beforeAndAfter(this::byId, this::idOf).beforeAndAfter(factory, ClientCommandWirePacket::command);
		}
	}

	public static final class Basic extends ClientCommandWirePacket {
		public static final StreamCodec<ByteBuf, ByteBuf, Basic> STREAM_CODEC = Format.BASIC.codec(Basic::new);

		public Basic() {
			this(PERFORM_RESPAWN);
		}

		private Basic(ClientCommand command) {
			super(Format.BASIC, command);
		}
	}

	public static final class WithGameRuleRequest extends ClientCommandWirePacket {
		public static final StreamCodec<ByteBuf, ByteBuf, WithGameRuleRequest> STREAM_CODEC =
			Format.GAME_RULES.codec(WithGameRuleRequest::new);

		public WithGameRuleRequest() {
			this(PERFORM_RESPAWN);
		}

		private WithGameRuleRequest(ClientCommand command) {
			super(Format.GAME_RULES, command);
		}
	}
}
