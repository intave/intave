package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class ReaderTests extends IntegrationTests {
  private static final Set<PacketType> EXCLUDED_TYPES = new HashSet<>();
  static {
    EXCLUDED_TYPES.add(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
  }

  public ReaderTests() {
    super("PR");
  }

  @Test(
    testCode = "A",
    severity = Severity.ERROR
  )
  public void testAllPlayerInfoReaders() {
    for (PacketType value : PacketType.values()) {
      if (PacketReaders.hasReader(value) && !EXCLUDED_TYPES.contains(value)) {
        PacketContainer packet;
	      try {
          packet = new PacketContainer(value);
        } catch (Throwable exception) {
          exception.printStackTrace();
          throw new IllegalStateException("Failed to create packet container for " + value);
        }
	      PacketReader reader = PacketReaders.readerOf(packet);
        Method[] declaredMethods;
        try {
          declaredMethods = reader.getClass().getDeclaredMethods();
        } catch (Throwable exception) {
          continue;
        }
        for (Method declaredMethod : declaredMethods) {
          if (!declaredMethod.isAccessible()) {
            continue;
          }
          if (declaredMethod.getParameterCount() != 0) {
            continue;
          }
          try {
            declaredMethod.invoke(reader);
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke " + declaredMethod.getName() + " on " + reader.getClass().getSimpleName(), e);
          }
        }
        reader.release();
      }
    }
  }
}
