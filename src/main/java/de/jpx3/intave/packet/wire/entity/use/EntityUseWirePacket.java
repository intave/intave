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

package de.jpx3.intave.packet.wire.entity.use;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableEntityUsePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.EntityUseAction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class EntityUseWirePacket {
	private EntityUseWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol735.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableEntityUsePacket {
		protected int entityId;
		protected EntityUseAction action;
		protected RawVector3d hitPosition;

		protected Base() {
			this(0, EntityUseAction.INTERACT, null);
		}

		protected Base(int entityId, EntityUseInteraction interaction) {
			this(entityId, interaction.action(), interaction.hitPosition());
		}

		protected Base(int entityId, EntityUseAction action, RawVector3d hitPosition) {
			this.entityId = entityId;
			this.action = Objects.requireNonNull(action, "action");
			this.hitPosition = hitPosition;
		}

		public EntityUseInteraction interaction() {
			markMutated();
			return new EntityUseInteraction(action(), hitPosition(), hand());
		}

		@Override
		public int entityId() {
			return entityId;
		}

		@Override
		public void setEntityId(int value) {
			markMutated();
			entityId = value;
		}

		@Override
		public EntityUseAction action() {
			return action;
		}

		@Override
		public void setAction(EntityUseAction value) {
			markMutated();
			action = Objects.requireNonNull(value, "action");
		}

		@Override
		public RawVector3d hitPosition() {
			markMutated();
			return action == EntityUseAction.INTERACT_AT ? hitPosition : null;
		}

		@Override
		public void setHitPosition(RawVector3d value) {
			markMutated();
			hitPosition = value;
		}

		@Override
		public abstract PlayerHand hand();

		@Override
		public abstract void setHand(PlayerHand value);

		@Override
		public boolean appliesTo(ConnectionState state, PacketType type) {
			return state == ConnectionState.PLAY && type == PacketType.Client.USE_ENTITY;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol47::entityId,
				EntityUseInteraction.WITHOUT_HAND_STREAM_CODEC, Protocol47::interaction,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(int entityId, EntityUseInteraction interaction) {
			super(entityId, interaction);
		}

		public Protocol47(int entityId, EntityUseAction action, RawVector3d hitPosition) {
			super(entityId, action, hitPosition);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
		}

		@Override
		public PlayerHand hand() {
			return null;
		}

		@Override
		public void setHand(PlayerHand value) {
			markMutated();
			if (value != null) throw new UnsupportedOperationException("This packet does not support a hand");
		}
	}

	static class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::entityId,
				EntityUseInteraction.STREAM_CODEC, Base::interaction,
				Protocol107::new
			);

		private PlayerHand hand;

		public Protocol107() {
			this(0, EntityUseAction.INTERACT, null, PlayerHand.MAIN_HAND);
		}

		public Protocol107(int entityId, EntityUseInteraction interaction) {
			this(entityId, interaction.action(), interaction.hitPosition(), interaction.hand());
		}

		public Protocol107(
			int entityId, EntityUseAction action, RawVector3d hitPosition, PlayerHand hand
		) {
			super(entityId, action, hitPosition);
			this.hand = hand;
		}

		@Override
		public PlayerHand hand() {
			return action() == EntityUseAction.ATTACK ? null : hand;
		}

		@Override
		public void setHand(PlayerHand value) {
			markMutated();
			hand = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 735;
		}
	}

	static final class Protocol735 extends EntityUseWirePacket.Protocol107 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol735> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::entityId,
				EntityUseInteraction.STREAM_CODEC, Base::interaction,
				ByteBufStreamCodecs.BOOLEAN, Protocol735::secondary,
				Protocol735::new
			);

		private boolean secondary;

		public Protocol735() {
			super();
		}

		public Protocol735(int entityId, EntityUseInteraction interaction, boolean secondary) {
			super(entityId, interaction);
			this.secondary = secondary;
		}

		public Protocol735(
			int entityId, EntityUseAction action, RawVector3d hitPosition, PlayerHand hand, boolean secondary
		) {
			super(entityId, action, hitPosition, hand);
			this.secondary = secondary;
		}

		public boolean secondary() {
			return secondary;
		}

		public void setSecondary(boolean value) {
			markMutated();
			secondary = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 735 && protocolVersion < 775;
		}
	}
}
