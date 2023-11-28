package de.jpx3.intave.connect.cloud.protocol.packets;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.connect.cloud.protocol.Direction;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.JsonPacket;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;

public final class ClientboundSampleTransmissionAcknowledgement extends JsonPacket<Clientbound> {
	private Identity identity;
	private AcceptedState state = AcceptedState.ACCEPTED;

	public ClientboundSampleTransmissionAcknowledgement() {
		super(Direction.CLIENTBOUND, "SAMPLE_TRANSMISSION_ACKNOWLEDGEMENT", "1");
	}

	public Identity identity() {
		return identity;
	}

	public AcceptedState state() {
		return state;
	}

	@Override
	public void serialize(JsonWriter writer) {
		try {
			writer.beginObject();
			writer.name("id");
			identity.serialize(writer);
			writer.name("state");
			writer.value(state.name());
			writer.endObject();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void deserialize(JsonReader reader) {
		try {
			reader.beginObject();
			while (reader.hasNext()) {
				while (reader.peek() == JsonToken.NAME) {
					switch (reader.nextName()) {
						case "id":
							identity = Identity.from(reader);
							break;
						case "state":
							state = AcceptedState.valueOf(reader.nextString());
							break;
					}
				}
				if (reader.hasNext()) {
					reader.skipValue();
				}
			}
			reader.endObject();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@KeepEnumInternalNames
	public enum AcceptedState {
		ACCEPTED,
		REJECTED
	}

	@KeepEnumInternalNames
	public enum Classification {
		LEGIT, CHEAT,
		UNKNOWN
	}
}
