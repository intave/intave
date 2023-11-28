package de.jpx3.intave.connect.cloud.protocol.packets;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.connect.cloud.protocol.Direction;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.JsonPacket;
import de.jpx3.intave.connect.cloud.protocol.listener.Serverbound;
import de.jpx3.intave.module.nayoro.Classifier;

public final class ServerboundSampleTransmissionRequest extends JsonPacket<Serverbound> {
	private Identity identity;

	// -- only when recording --
	private Classifier classifier;
	private String scenarioOrCheat;
	private String version;
	// -- only when recording --

	public ServerboundSampleTransmissionRequest() {
		super(Direction.SERVERBOUND, "SAMPLE_TRANSMISSION_REQUEST", "1");
	}

	public ServerboundSampleTransmissionRequest(Identity identity) {
		this();
		this.identity = identity;
	}

	public ServerboundSampleTransmissionRequest(Identity identity, Classifier classifier, String scenarioOrCheat, String version) {
		this(identity);
		this.classifier = classifier;
		this.scenarioOrCheat = scenarioOrCheat;
		this.version = version;
	}

	public Identity identity() {
		return identity;
	}

	@Override
	public void serialize(JsonWriter writer) {
		try {
			writer.beginObject();
			writer.name("id");
			identity.serialize(writer);
			if (classifier != null) {
				writer.name("classifier");
				writer.value(classifier.name());
			}
			if (scenarioOrCheat != null) {
				writer.name("scenario");
				writer.value(scenarioOrCheat);
			}
			if (version != null) {
				writer.name("version");
				writer.value(version);
			}
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
						case "classifier":
							classifier = Classifier.valueOf(reader.nextString());
							break;
						case "scenario":
							scenarioOrCheat = reader.nextString();
							break;
						case "version":
							version = reader.nextString();
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

	public boolean isRecording() {
		return classifier != null && scenarioOrCheat != null && version != null;
	}
}
