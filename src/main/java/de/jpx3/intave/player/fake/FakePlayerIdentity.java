package de.jpx3.intave.player.fake;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataType;
import com.github.retrooper.packetevents.protocol.player.UserProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class FakePlayerIdentity {
  private final int identifier;
  private final UserProfile profile;
  private final Map<Integer, EntityData<?>> metadata = new HashMap<>();

  protected FakePlayerIdentity(int identifier, UserProfile profile) {
    this.identifier = identifier;
    this.profile = profile;
  }

  public int identifier() {
    return identifier;
  }

  public UserProfile profile() {
    return profile;
  }

  public List<EntityData<?>> metadata() {
    return new ArrayList<>(metadata.values());
  }

  public <T> void metadata(int index, EntityDataType<T> type, T value) {
    metadata.put(index, new EntityData<>(index, type, value));
  }

  public Object metadataValue(int index) {
    EntityData<?> data = metadata.get(index);
    return data == null ? null : data.getValue();
  }
}
