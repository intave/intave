package de.jpx3.intave.module.cloud.protocol;

import java.io.DataInput;
import java.io.DataOutput;

public interface Serializable {
  void serialize(DataOutput buffer);

  void deserialize(DataInput buffer);
}
