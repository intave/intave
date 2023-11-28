package de.jpx3.intave.connect.cloud.protocol.packets;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.connect.cloud.protocol.Direction;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.JsonPacket;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;
import org.bukkit.entity.Player;

public final class ClientboundSetTrustfactor extends JsonPacket<Clientbound> {
  private Identity id;
  private TrustFactor trustFactor;

  public ClientboundSetTrustfactor() {
    super(Direction.CLIENTBOUND, "SET_TRUSTFACTOR", "1");
  }

  public ClientboundSetTrustfactor(Player player, TrustFactor trustFactor) {
    super(Direction.CLIENTBOUND, "SET_TRUSTFACTOR", "1");
    this.id = Identity.from(player);
    this.trustFactor = trustFactor;
  }

  @Override
  public void serialize(JsonWriter writer) {
    try {
      writer.beginObject();
      writer.name("id");
      id.serialize(writer);
      writer.name("factor").value(trustFactor.name());
      writer.endObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void deserialize(JsonReader reader) {
    try {
      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        switch (reader.nextName()) {
          case "id":
            id = Identity.from(reader);
            break;
          case "factor":
            trustFactor = TrustFactor.valueOf(reader.nextString());
            break;
        }
      }
      while (reader.hasNext()) {
        reader.skipValue();
      }
      reader.endObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Identity id() {
    return id;
  }

  public TrustFactor trustFactor() {
    return trustFactor;
  }
}
