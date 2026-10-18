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

package de.jpx3.intave.packet.wire;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.codec.VarIntCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableHandshakePacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public final class HandshakeWirePacket implements MutableHandshakePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, HandshakeWirePacket> STREAM_CODEC = StreamCodec.of(
		HandshakeWirePacket::encode,
		HandshakeWirePacket::decode
	);

	private int protocolVersion;
	private String serverAddress;
	private int serverPort;
	private ConnectionState nextConnectionState;

	public HandshakeWirePacket() {
		this.protocolVersion = 0;
		this.serverAddress = "";
		this.serverPort = 0;
		this.nextConnectionState = ConnectionState.STATUS;
	}

	public HandshakeWirePacket(
		int protocolVersion,
		String serverAddress,
		int serverPort,
		ConnectionState nextConnectionState
	) {
		this.protocolVersion = protocolVersion;
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
		this.nextConnectionState = nextConnectionState;
	}

	@Override
	public int protocolVersion() {
		return this.protocolVersion;
	}

	@Override
	public String serverAddress() {
		return this.serverAddress;
	}

	@Override
	public int serverPort() {
		return this.serverPort;
	}

	@Override
	public ConnectionState nextConnectionState() {
		return this.nextConnectionState;
	}

	@Override
	public void setProtocolVersion(int protocolVersion) {
		this.protocolVersion = protocolVersion;
	}

	@Override
	public void setServerAddress(String serverAddress) {
		this.serverAddress = serverAddress;
	}

	@Override
	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}

	@Override
	public void setNextConnectionState(ConnectionState nextConnectionState) {
		this.nextConnectionState = nextConnectionState;
	}

	@Override
	public String toString() {
		return "HandshakeWirePacket{" +
			"protocolVersion=" + protocolVersion +
			", serverAddress='" + serverAddress + '\'' +
			", serverPort=" + serverPort +
			", nextConnectionState=" + nextConnectionState +
			'}';
	}

	@Override
	public int hashCode() {
		int result = Integer.hashCode(this.protocolVersion);
		result = 31 * result + this.serverAddress.hashCode();
		result = 31 * result + Integer.hashCode(this.serverPort);
		result = 31 * result + this.nextConnectionState.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof HandshakeWirePacket)) {
			return false;
		}
		HandshakeWirePacket other = (HandshakeWirePacket) obj;
		return this.protocolVersion == other.protocolVersion &&
			this.serverAddress.equals(other.serverAddress) &&
			this.serverPort == other.serverPort &&
			this.nextConnectionState == other.nextConnectionState;
	}

	private static HandshakeWirePacket decode(ByteBuf input) {
		int protocolVersion = VarIntCodec.INSTANCE.decode(input);
		int addressLength = VarIntCodec.INSTANCE.decode(input);
		if (addressLength < 0 || addressLength > input.readableBytes() - 3) {
			throw new IllegalArgumentException("Invalid handshake server address length: " + addressLength);
		}
		byte[] addressBytes = new byte[addressLength];
		input.readBytes(addressBytes);
		String serverAddress = new String(addressBytes, StandardCharsets.UTF_8);
		int serverPort = input.readUnsignedShort();
		ConnectionState nextConnectionState = decodeNextConnectionState(VarIntCodec.INSTANCE.decode(input));
		return new HandshakeWirePacket(protocolVersion, serverAddress, serverPort, nextConnectionState);
	}

	private static void encode(ByteBuf output, HandshakeWirePacket packet) {
		VarIntCodec.INSTANCE.encode(output, packet.protocolVersion);
		byte[] serverAddress = packet.serverAddress.getBytes(StandardCharsets.UTF_8);
		VarIntCodec.INSTANCE.encode(output, serverAddress.length);
		output.writeBytes(serverAddress);
		output.writeShort(packet.serverPort);
		VarIntCodec.INSTANCE.encode(output, encodeNextConnectionState(packet.nextConnectionState));
	}

	private static ConnectionState decodeNextConnectionState(int intention) {
		if (intention == 1) {
			return ConnectionState.STATUS;
		}
		if (intention == 2 || intention == 3) {
			return ConnectionState.LOGIN;
		}
		throw new IllegalArgumentException("Invalid handshake intention: " + intention);
	}

	private static int encodeNextConnectionState(ConnectionState connectionState) {
		if (connectionState == ConnectionState.STATUS) {
			return 1;
		}
		if (connectionState == ConnectionState.LOGIN) {
			return 2;
		}
		throw new IllegalArgumentException("Invalid handshake connection state: " + connectionState);
	}
}
