package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.adapter.MinecraftVersions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Inspired from work by lukas81298
// See https://pastebin.com/EuprZGHe for the blueprint
public final class EntityMetadataReader extends EntityReader {
  private static final boolean IT_WAS_NICE_WHILE_IT_LASTED = !MinecraftVersions.VER1_19_3.atOrAbove();

  public List<WrappedWatchableObject> metadataObjects() {
    if (IT_WAS_NICE_WHILE_IT_LASTED) {
      return packet().getWatchableCollectionModifier().read(0);
    }
    List<WrappedWatchableObject> list = new ArrayList<>();
    for (WrappedDataValue value : metadataValues()) {
      list.add(watchableObjectFromDataValue(value));
    }
    return list;
  }

  public List<WrappedDataValue> metadataValues() {
    return packet().getDataValueCollectionModifier().read(0);
  }

  private static WrappedWatchableObject watchableObjectFromDataValue(WrappedDataValue value) {
    WrappedDataWatcherObject dataWatcherObject = new WrappedDataWatcherObject(
      value.getIndex(), value.getSerializer()
    );
    return new WrappedWatchableObject(dataWatcherObject, value.getValue()) {
      @Override
      public Object getRawValue() {
        return value.getRawValue();
      }
    };
  }

  public void setMetadataObjects(List<WrappedWatchableObject> watchables) {
    if (IT_WAS_NICE_WHILE_IT_LASTED) {
      packet().getWatchableCollectionModifier().write(0, watchables);
    } else {
      packet().getDataValueCollectionModifier().write(0, watchables.stream()
        .map(EntityMetadataReader::dataValueFromWatchableObject)
        .collect(Collectors.toList()));
    }
  }

  private static WrappedDataValue dataValueFromWatchableObject(WrappedWatchableObject object) {
    return new WrappedDataValue(
      object.getIndex(),
      object.getWatcherObject().getSerializer(),
      object.getValue()
    );
  }
}