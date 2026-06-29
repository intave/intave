package de.jpx3.intave.codec.smart;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class SmartCodecDecodeResult {
	private final Map<String, Object> values;
	private final Map<String, byte[]> unknownFields;

	SmartCodecDecodeResult(
		Map<String, Object> values,
		Map<String, byte[]> unknownFields
	) {
		this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		this.unknownFields = Collections.unmodifiableMap(new LinkedHashMap<>(unknownFields));
	}

	@SuppressWarnings("unchecked")
	<F> F get(String name) {
		if (!values.containsKey(name)) {
			throw new IllegalStateException("No smart codec value decoded for field: " + name);
		}
		return (F) values.get(name);
	}

	public boolean booleanValue(String name) {
		return get(name);
	}

	public int integerValue(String name) {
		return get(name);
	}

	public String stringValue(String name) {
		return get(name);
	}

	public byte[] byteArrayValue(String name) {
		return get(name);
	}

	public Set<String> keySet() {
		return values.keySet();
	}

	@SuppressWarnings("unchecked")
	public <F> F getOrDefault(
		String name,
		F fallback
	) {
		if (!values.containsKey(name)) {
			return fallback;
		}
		return (F) values.get(name);
	}

	public boolean contains(
		String name
	) {
		return values.containsKey(name);
	}

	public Map<String, byte[]> unknownFields() {
		return unknownFields;
	}

	public Set<String> unknownFieldNames() {
		return unknownFields.keySet();
	}
}
