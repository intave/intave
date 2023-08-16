package de.jpx3.intave.module.cloud.protocol;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.*;

public interface JsonSerializable extends Serializable {
  void serialize(JsonWriter writer);

  void deserialize(JsonReader reader);

  default void serialize(DataOutput output) {
    StringWriter jsonString = new StringWriter();
    JsonWriter writer = new JsonWriter(new BufferedWriter(jsonString));
    serialize(writer);
    try {
      writer.close();
      output.writeUTF(jsonString.toString());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  default void deserialize(DataInput input) {
    try {
      deserialize(new JsonReader(new StringReader(input.readUTF())));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
