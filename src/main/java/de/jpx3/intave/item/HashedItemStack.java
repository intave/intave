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

package de.jpx3.intave.item;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import java.util.*;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.count;

public final class HashedItemStack {
	private final MinecraftKey type;
	private final int amount;
	private final Map<MinecraftKey, Integer> componentHashes;
	private final Set<MinecraftKey> removedComponents;

	public HashedItemStack(MinecraftKey type, int amount, Map<MinecraftKey, Integer> hashes, Set<MinecraftKey> removed) {
		this.type = Objects.requireNonNull(type);
		this.amount = amount;
		Map<MinecraftKey, Integer> copy = new LinkedHashMap<>();
		hashes.forEach((key, hash) -> copy.put(Objects.requireNonNull(key), Objects.requireNonNull(hash)));
		componentHashes = Collections.unmodifiableMap(copy);
		Set<MinecraftKey> removals = new LinkedHashSet<>(removed);
		removals.forEach(Objects::requireNonNull);
		removedComponents = Collections.unmodifiableSet(removals);
	}

	public MinecraftKey type() {
		return type;
	}

	public int amount() {
		return amount;
	}

	public Map<MinecraftKey, Integer> componentHashes() {
		return componentHashes;
	}

	public Set<MinecraftKey> removedComponents() {
		return removedComponents;
	}

	@Override
	public String toString() {
		return "HashedItemStack{" +
			"type=" + type +
			", amount=" + amount +
			", componentHashes=" + componentHashes +
			", removedComponents=" + removedComponents +
			'}';
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, amount, componentHashes, removedComponents);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof HashedItemStack)) return false;
		HashedItemStack other = (HashedItemStack) obj;
		return type.equals(other.type)
			&& amount == other.amount
			&& componentHashes.equals(other.componentHashes)
			&& removedComponents.equals(other.removedComponents);
	}

	public static StreamCodec<ByteBuf, ByteBuf, HashedItemStack> codecFor(int protocol) {
		if (protocol < 770) {
			throw new IllegalArgumentException("Hashed clicks require protocol 770 or later");
		}
		ItemRegistry items = ItemRegistry.forProtocol(protocol);
		ItemComponentTypeRegistry components = ItemComponentTypeRegistry.forProtocol(protocol);
		return StreamCodec.of((output, stack) -> {
			output.writeBoolean(stack != null);
			if (stack == null) {
				return;
			}
			int id = items.idOf(stack.type);
			if (id < 0) {
				throw new IllegalArgumentException("Unknown item " + stack.type);
			}
			VARINT.encode(output, id);
			VARINT.encode(output, stack.amount);
			if (stack.componentHashes.size() > 256 || stack.removedComponents.size() > 256) {
				throw new IllegalArgumentException("Too many hashed components");
			}
			VARINT.encode(output, stack.componentHashes.size());
			stack.componentHashes.forEach((key, hash) -> {
				VARINT.encode(output, componentId(components, key));
				output.writeInt(hash);
			});
			VARINT.encode(output, stack.removedComponents.size());
			stack.removedComponents.forEach(key -> VARINT.encode(output, componentId(components, key)));
		}, input -> {
			if (!input.readBoolean()) {
				return null;
			}
			MinecraftKey type = items.keyById(VARINT.decode(input));
			if (type == null) {
				throw new IllegalArgumentException("Unknown hashed item ID");
			}
			int amount = VARINT.decode(input);
			Map<MinecraftKey, Integer> hashes = new LinkedHashMap<>();
			int added = count(input, 256);
			for (int i = 0; i < added; i++) {
				hashes.put(componentType(components, input), input.readInt());
			}
			Set<MinecraftKey> removed = new LinkedHashSet<>();
			int removals = count(input, 256);
			for (int i = 0; i < removals; i++) {
				removed.add(componentType(components, input));
			}
			return new HashedItemStack(type, amount, hashes, removed);
		});
	}

	private static int componentId(ItemComponentTypeRegistry registry, MinecraftKey key) {
		int id = registry.idOf(key);
		if (id < 0) throw new IllegalArgumentException("Unknown component " + key);
		return id;
	}

	private static MinecraftKey componentType(ItemComponentTypeRegistry registry, ByteBuf input) {
		MinecraftKey key = registry.keyById(VARINT.decode(input));
		if (key == null) throw new IllegalArgumentException("Unknown hashed component ID");
		return key;
	}
}
