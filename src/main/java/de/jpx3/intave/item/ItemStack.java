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

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.nbt.tag.Tag;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.item.codec.ComponentItemStackCodec;
import de.jpx3.intave.item.codec.NbtItemStackProtocol393Codec;
import de.jpx3.intave.item.codec.NbtItemStackProtocol404Codec;
import de.jpx3.intave.item.codec.NbtItemStackProtocol47Codec;
import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.version.ServerProtocolVersion;
import io.netty.buffer.ByteBuf;
import org.bukkit.Material;

import java.util.*;

public final class ItemStack {
	private static final int FIRST_COMPONENT_PROTOCOL = 766;
	private static final MinecraftKey AIR = MinecraftKey.withDefaultNamespace("air");
	private static final MinecraftKey ENCHANTED_BOOK = MinecraftKey.withDefaultNamespace("enchanted_book");
	private static final MinecraftKey ENCHANTMENTS = MinecraftKey.withDefaultNamespace("enchantments");
	private static final MinecraftKey STORED_ENCHANTMENTS = MinecraftKey.withDefaultNamespace("stored_enchantments");
	private static final MinecraftKey PROFILE = MinecraftKey.withDefaultNamespace("profile");

	private final int protocol;
	private MinecraftKey type;
	private int amount;
	private @Nullable Short legacyData;
	private @Nullable CompoundTag tag;
	private ItemComponentPatch components = ItemComponentPatch.EMPTY;

	/** The protocol determines the NBT or component format used by metadata getters and setters. */
	public ItemStack(MinecraftKey type, int amount, int protocol) {
		this.protocol = protocol;
		setType(type);
		setAmount(amount);
	}

	public static StreamCodec<ByteBuf, ByteBuf, ItemStack> codecFor(int protocol) {
		if (protocol < 393) {
			return new NbtItemStackProtocol47Codec(protocol);
		}
		if (protocol < 404) {
			return new NbtItemStackProtocol393Codec(protocol);
		}
		if (protocol < FIRST_COMPONENT_PROTOCOL) {
			return new NbtItemStackProtocol404Codec(protocol);
		}
		return new ComponentItemStackCodec(protocol);
	}

	public int protocol() {
		return protocol;
	}

	private boolean usesComponents() {
		return protocol >= FIRST_COMPONENT_PROTOCOL;
	}

	public MinecraftKey type() {
		return type;
	}

	/** Resolves this item's type against the running server's Bukkit materials. */
	public Optional<Material> bukkitMaterial() {
		Material material = MinecraftVersions.VER1_13_0.below()
			? Material.getMaterial(ItemRegistry.forProtocol(ServerProtocolVersion.current()).idOf(type))
			: Material.getMaterial(type.path().toUpperCase(Locale.ROOT));
		return Optional.ofNullable(material);
	}

	public void setType(MinecraftKey type) {
		Objects.requireNonNull(type, "type");
		if (AIR.equals(type)) {
			throw new IllegalArgumentException("An item stack must contain an item");
		}
		this.type = type;
	}

	public int amount() {
		return amount;
	}

	public void setAmount(int amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("Item amount must be positive");
		}
		this.amount = amount;
	}

	public ItemEnchantments enchantments() {
		return usesComponents() ? enchantments(EnchantmentRegistry.forProtocol(protocol)) : nbtEnchantments();
	}

	public ItemEnchantments enchantments(EnchantmentRegistry registry) {
		return usesComponents()
			? ItemEnchantments.fromComponent(components.get(enchantmentComponent()), registry)
			: nbtEnchantments();
	}

	private ItemEnchantments nbtEnchantments() {
		ListTag<CompoundTag> list = tag == null ? null : tag.getListTag(enchantmentTag(), CompoundTag.class);
		return ItemEnchantments.fromNbt(list, protocol);
	}

	public void setEnchantments(ItemEnchantments enchantments) {
		Objects.requireNonNull(enchantments, "enchantments");
		if (usesComponents()) {
			setEnchantments(enchantments, EnchantmentRegistry.forProtocol(protocol));
		} else {
			setNbtEnchantments(enchantments);
		}
	}

	/** Encodes enchantment references using the connection's registry order. */
	public void setEnchantments(ItemEnchantments enchantments, EnchantmentRegistry registry) {
		Objects.requireNonNull(enchantments, "enchantments");
		Objects.requireNonNull(registry, "registry");
		if (usesComponents()) {
			MinecraftKey key = enchantmentComponent();
			ItemComponentValue previous = components.get(key);
			Boolean showInTooltip = null;
			if (protocol < 770) {
				showInTooltip = previous == null || previous.field("show_in_tooltip").bool();
			}
			components = components.with(key, enchantments.toComponent(registry, showInTooltip));
		} else {
			setNbtEnchantments(enchantments);
		}
	}

	private void setNbtEnchantments(ItemEnchantments enchantments) {
		String name = enchantmentTag();
		if (enchantments.hasEnchantments()) {
			ListTag<CompoundTag> list = enchantments.toNbt(protocol);
			getOrCreateTag().put(name, list);
		} else if (tag != null) {
			tag.remove(name);
		}
	}

	private MinecraftKey enchantmentComponent() {
		return ENCHANTED_BOOK.equals(type) ? STORED_ENCHANTMENTS : ENCHANTMENTS;
	}

	private String enchantmentTag() {
		if (ENCHANTED_BOOK.equals(type)) {
			return "StoredEnchantments";
		}
		return protocol < 393 ? "ench" : "Enchantments";
	}

	public boolean hasSkullOwnerName() {
		return skullOwnerName().isPresent();
	}

	public Optional<String> skullOwnerName() {
		if (usesComponents()) {
			return Optional.ofNullable(components.get(PROFILE))
				.map(profile -> profile.field("name"))
				.map(ItemComponentValue::string);
		}
		Tag owner = tag == null ? null : tag.get("SkullOwner");
		if (owner instanceof StringTag) {
			return Optional.of(((StringTag) owner).getValue());
		}
		if (owner instanceof CompoundTag) {
			return Optional.ofNullable(((CompoundTag) owner).getString("Name", null));
		}
		return Optional.empty();
	}

	public void setSkullOwnerName(@Nullable String name) {
		if (usesComponents()) {
			setProfileComponentName(name);
			return;
		}
		CompoundTag profile = tag == null ? null : tag.getCompoundTag("SkullOwner");
		if (name != null) {
			if (profile == null) {
				getOrCreateTag().putString("SkullOwner", name);
			} else {
				profile.putString("Name", name);
			}
		} else if (profile != null) {
			profile.remove("Name");
			if (profile.isEmpty()) {
				tag.remove("SkullOwner");
			}
		} else if (tag != null) {
			tag.remove("SkullOwner");
		}
	}

	private void setProfileComponentName(@Nullable String name) {
		ItemComponentValue profile = components.get(PROFILE);
		if (profile == null && name == null) {
			return;
		}
		Map<String, ItemComponentValue> fields = new LinkedHashMap<>();
		if (profile == null) {
			fields.put("properties", ItemComponentValue.list(Collections.emptyList()));
		} else {
			fields.putAll(profile.fields());
		}
		if (name == null) {
			fields.remove("name");
			if (fields.containsKey("complete")) {
				fields.put("complete", ItemComponentValue.of(false));
			}
		} else {
			fields.put("name", ItemComponentValue.of(name));
		}
		components = components.with(PROFILE, ItemComponentValue.fields(fields));
	}

	private CompoundTag getOrCreateTag() {
		if (tag == null) {
			tag = new CompoundTag();
		}
		return tag;
	}

	/** Returns a detached copy of the stack's NBT. */
	public @Nullable CompoundTag nbt() {
		return tag == null ? null : tag.copy();
	}

	/** Replaces the stack's NBT with an independent copy. */
	public void setTag(@Nullable CompoundTag tag) {
		if (usesComponents()) {
			throw new IllegalStateException("Component stacks carry a component patch, not legacy NBT");
		}
		this.tag = tag == null ? null : tag.copy();
	}

	/** Returns the explicit changes to the item's default components. */
	public ItemComponentPatch components() {
		return components;
	}

	public void setComponents(ItemComponentPatch components) {
		if (!usesComponents()) {
			throw new IllegalStateException("NBT stacks do not carry data components");
		}
		this.components = Objects.requireNonNull(components, "components");
	}

	public boolean hasLegacyData() {
		return legacyData != null;
	}

	public @Nullable Short legacyData() {
		return legacyData;
	}

	public void setLegacyData(@Nullable Short legacyData) {
		this.legacyData = legacyData;
	}

	/**
	 * Returns an independent copy, including all data outside the exposed fields.
	 */
	public ItemStack copy() {
		ItemStack copy = new ItemStack(type, amount, protocol);
		copy.legacyData = legacyData;
		copy.tag = nbt();
		copy.components = components;
		return copy;
	}
}
