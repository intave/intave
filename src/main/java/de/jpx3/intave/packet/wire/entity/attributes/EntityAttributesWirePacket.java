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

package de.jpx3.intave.packet.wire.entity.attributes;

import de.jpx3.intave.codec.JsonStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableEntityAttributesPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;
import de.jpx3.intave.player.attribute.AttributeModifier.Operation;
import de.jpx3.intave.registry.ProtocolRegistryFiles;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import java.util.*;
import java.util.function.Supplier;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;

public final class EntityAttributesWirePacket {
	private EntityAttributesWirePacket() {}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class, Protocol735.class, Protocol755.class, Protocol766.class,
			Protocol767.class, Protocol768.class, Protocol771.class, Protocol776.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableEntityAttributesPacket {
		// Built-in attribute registration order in each client's Attributes class.
		private static final ProtocolRegistryFiles<List<String>> KEYS = new ProtocolRegistryFiles<>(
			"attributes", JsonStreamCodecs.listCodecOf(JsonStreamCodecs.STRING), Collections::unmodifiableList
		);
		protected int entityId;
		protected List<Attribute> attributes = Collections.emptyList();

		@Override
		public int entityId() {
			return entityId;
		}

		@Override
		public void setEntityId(int entityId) {
			markMutated();
			this.entityId = entityId;
		}

		@Override
		public List<Attribute> attributes() {
			return attributes;
		}

		@Override
		public void setAttributes(List<Attribute> attributes) {
			markMutated();
			this.attributes = Collections.unmodifiableList(new ArrayList<>(attributes));
		}

		protected static <P extends Base> StreamCodec<ByteBuf, ByteBuf, P> codec(int protocol, Supplier<P> factory) {
			StreamCodec<ByteBuf, ByteBuf, Integer> sizeCodec = protocol < 755 ? INTEGER : VARINT;
			StreamCodec<ByteBuf, ByteBuf, String> keyCodec = attributeKeyCodec(protocol);
			int maxAttributes = protocol >= 776 ? 128 : Integer.MAX_VALUE;
			return StreamCodec.of((out, packet) -> {
				VARINT.encode(out, packet.entityId());
				List<Attribute> attributes = packet.attributes();
				if (attributes.size() > maxAttributes) {
					throw new IllegalArgumentException("Too many attributes");
				}
				sizeCodec.encode(out, attributes.size());
				for (Attribute attribute : attributes) {
					keyCodec.encode(out, attribute.attributeKey());
					out.writeDouble(attribute.baseValue());
					VARINT.encode(out, attribute.modifiers().size());
					for (AttributeModifier modifier : attribute.modifiers()) {
						writeModifier(out, protocol, modifier);
					}
				}
			}, in -> {
				P packet = factory.get();
				packet.entityId = VARINT.decode(in);
				int size = sizeCodec.decode(in);
				// Every entry has at least a one-byte key, a double and a modifier count.
				if (size < 0 || size > maxAttributes || size > in.readableBytes() / 10) {
					throw new IllegalArgumentException("Invalid attribute count " + size);
				}
				List<Attribute> attributes = new ArrayList<>(size);
				for (int i = 0; i < size; i++) {
					String key = keyCodec.decode(in);
					double value = in.readDouble();
					int modifierCount = count(in, in.readableBytes() / (protocol < 767 ? 25 : 10));
					Set<AttributeModifier> modifiers = new LinkedHashSet<>();
					for (int j = 0; j < modifierCount; j++) {
						modifiers.add(readModifier(in, protocol));
					}
					attributes.add(Attribute.newBuilder().withAttributeKey(key).withBaseValue(value)
						.withDefaultBaseValue(value).withAttributeModifiers(modifiers).build());
				}
				packet.attributes = Collections.unmodifiableList(attributes);
				return packet;
			});
		}

		private static StreamCodec<ByteBuf, ByteBuf, String> attributeKeyCodec(int protocol) {
			if (protocol < 735) {
				return stringCodec(64);
			}
			if (protocol < 766) {
				return VARINT_STRING;
			}
			List<String> keys = KEYS.forProtocol(protocol);
			return VARINT.beforeAndAfter(id -> {
				if (id < 0 || id >= keys.size()) {
					throw new IllegalArgumentException("Unknown attribute ID " + id);
				}
				return keys.get(id);
			}, key -> {
				int id = keys.indexOf(MinecraftKey.parse(key).fullKey());
				if (id < 0) {
					throw new IllegalArgumentException("Unknown attribute key " + key);
				}
				return id;
			});
		}

		private static AttributeModifier readModifier(ByteBuf in, int protocol) {
			UUID uuid = protocol < 767 ? UUID.decode(in) : null;
			MinecraftKey key = protocol >= 767 ? MinecraftKey.STREAM_CODEC.decode(in) : null;
			double amount = in.readDouble();
			int id = protocol < 766 ? in.readByte() : VARINT.decode(in);
			// The registry-era operation codec maps unknown IDs to operation zero.
			Operation operation = protocol >= 766 && (id < 0 || id > 2) ? Operation.ADD_NUMBER : Operation.fromId(id);
			return new AttributeModifier(key, uuid, protocol < 767 ? "Unknown synced attribute modifier" : null, operation, amount);
		}

		private static void writeModifier(ByteBuf out, int protocol, AttributeModifier modifier) {
			if (protocol < 767) {
				UUID.encode(out, modifier.id());
			} else {
				MinecraftKey.STREAM_CODEC.encode(out, modifier.key());
			}
			out.writeDouble(modifier.amount());
			int operation = modifier.operation().getId();
			if (protocol < 766) {
				out.writeByte(operation);
			} else {
				VARINT.encode(out, operation);
			}
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = codec(47, Protocol47::new);

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 47 && protocol < 735;
		}
	}

	static final class Protocol735 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol735> STREAM_CODEC = codec(735, Protocol735::new);

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 735 && protocol < 755;
		}
	}

	static final class Protocol755 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755> STREAM_CODEC = codec(755, Protocol755::new);

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 755 && protocol < 766;
		}
	}

	static final class Protocol766 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol766> STREAM_CODEC = codec(766, Protocol766::new);

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 766 && protocol < 767;
		}
	}

	static final class Protocol767 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol767> STREAM_CODEC = codec(767, Protocol767::new);

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 767 && protocol < 768;
		}
	}

	static final class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC = codec(768, Protocol768::new);

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 768 && protocol < 771;
		}
	}

	static final class Protocol771 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol771> STREAM_CODEC = codec(771, Protocol771::new);

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 771 && protocol < 776;
		}
	}

	static final class Protocol776 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol776> STREAM_CODEC = codec(776, Protocol776::new);

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 776;
		}
	}
}
