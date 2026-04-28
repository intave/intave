package de.jpx3.intave.packet.converter;

import com.comphenix.protocol.reflect.EquivalentConverter;
import de.jpx3.intave.codec.CodecTranslator;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.FriendlyByteBuf;
import de.jpx3.intave.share.Input;
import io.netty.buffer.ByteBuf;

public final class InputConverter implements EquivalentConverter<Input> {
	public static final InputConverter INSTANCE = new InputConverter();
	public static final Class<?> inputClass = inputClass();
	private static final ThreadLocal<ByteBuf> caches = ThreadLocal.withInitial(FriendlyByteBuf::from256Unpooled);
	private static final StreamCodec<ByteBuf, ByteBuf, Input> intaveCodec = Input.STREAM_CODEC;
	private static final StreamCodec<ByteBuf, ByteBuf, Object> nativeCodec = (StreamCodec<ByteBuf, ByteBuf, Object>)
		CodecTranslator.translatedCodecOf(inputClass);

	@Override
	public Object getGeneric(Input input) {
		ByteBuf cache = caches.get();
		cache.clear();
		intaveCodec.encode(cache, input);
		return nativeCodec.decode(cache);
	}

	@Override
	public Input getSpecific(Object o) {
	  ByteBuf cache = caches.get();
		cache.clear();
		nativeCodec.encode(cache, o);
		return intaveCodec.decode(cache);
	}

	@Override
	public Class<Input> getSpecificType() {
		return Input.class;
	}

	private static Class<?> inputClass() {
		try {
			return Class.forName("net.minecraft.world.entity.player.Input");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Cannot find PositionMoveRotation class", e);
		}
	}
}
