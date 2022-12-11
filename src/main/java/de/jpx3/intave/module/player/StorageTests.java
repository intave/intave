package de.jpx3.intave.module.player;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import de.jpx3.intave.access.player.storage.EmptyStorageGateway;
import de.jpx3.intave.access.player.storage.MemoryStorageGateway;
import de.jpx3.intave.access.player.storage.StorageGateway;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.test.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.Storage;
import org.bukkit.entity.Player;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class StorageTests extends Tests {
  private static final UUID ZERO_UUID = new UUID(0, 0);
  private static final UUID ONE_UUID = new UUID(1, 1);
  private static final String EXAMPLE_TEXT = generateExampleText();

  private static String generateExampleText() {
    String theWord = "intave";
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 16; i++) {
      for (int j = 0; j < theWord.length(); j++) {
        char c = theWord.charAt(j);
        if (ThreadLocalRandom.current().nextBoolean()) {
          c = Character.toUpperCase(c);
        }
        builder.append(c);
      }
      builder.append(" ");
    }
    return builder.toString();
  }

  private User user;
  private Player player;
  private StorageGateway exampleGateway;

  public StorageTests() {
    super("ST");
  }

  @Before
  public void setup() {
    StorageGateway remoteGateway = Modules.storage().storageGateway();
    exampleGateway = remoteGateway == null || remoteGateway instanceof EmptyStorageGateway ? new MemoryStorageGateway() : remoteGateway;
    player = FakePlayerFactory.createPlayer((s, objects) -> s.equals("getUniqueId") ? ZERO_UUID : null);
    user = UserFactory.createTestUserFor(player, 47);
    UserRepository.manuallyRegisterUser(player, user);
  }

  private ByteBuffer gateawayReturn;

  @Test(
    testCode = "A",
    severity = Severity.WARNING
  )
  public void testBasicIO() {
    ByteBuffer invalidByteBuffer = ByteBuffer.wrap(EXAMPLE_TEXT.toUpperCase().getBytes(StandardCharsets.UTF_8));
    ByteBuffer validByteBuffer = ByteBuffer.wrap(EXAMPLE_TEXT.getBytes(StandardCharsets.UTF_8));
    exampleGateway.saveStorage(ZERO_UUID, invalidByteBuffer);
    exampleGateway.saveStorage(ZERO_UUID, validByteBuffer);

    CountDownLatch latch = new CountDownLatch(1);
    exampleGateway.requestStorage(ZERO_UUID, byteBuffer -> {
      gateawayReturn = byteBuffer;
      latch.countDown();
    });

    try {
      boolean awaited = latch.await(3, TimeUnit.SECONDS);
      if (!awaited) {
        fail("Storage lookup took too long");
      }
    } catch (InterruptedException ignored) {}

    if (gateawayReturn == null || gateawayReturn.remaining() == 0) {
      fail("Empty return buffer for request");
    }
    if (!gateawayReturn.equals(validByteBuffer)) {
      fail("Does not support overriding or is fundamentally broken");
    }
  }

  private ByteBuffer returnA, returnB;

  @Test(
    testCode = "B",
    severity = Severity.WARNING
  )
  public void testMultipleIds() {
    ByteBuffer byteBufferA = ByteBuffer.wrap(EXAMPLE_TEXT.getBytes(StandardCharsets.UTF_8));
    ByteBuffer byteBufferB = ByteBuffer.wrap(EXAMPLE_TEXT.toLowerCase().getBytes(StandardCharsets.UTF_8));

    exampleGateway.saveStorage(ZERO_UUID, byteBufferA);
    exampleGateway.saveStorage(ONE_UUID, byteBufferB);

    CountDownLatch latch = new CountDownLatch(2);
    exampleGateway.requestStorage(ZERO_UUID, byteBuffer -> {
      returnA = byteBuffer;
      latch.countDown();
    });
    exampleGateway.requestStorage(ONE_UUID, byteBuffer -> {
      returnB = byteBuffer;
      latch.countDown();
    });

    try {
      boolean awaited = latch.await(3, TimeUnit.SECONDS);
      if (!awaited) {
        fail("Storage lookup took too long");
      }
    } catch (InterruptedException ignored) {}

    if (!byteBufferA.equals(returnA)) {
      fail("Incorrect storage return, check if you are managing IDs properly");
    }
    if (!byteBufferB.equals(returnB)) {
      fail("Incorrect storage return, check if you are managing IDs properly");
    }
  }

  @Test(
    testCode = "C",
    severity = Severity.ERROR
  )
  public void testDecryption() {
    MockStorage mockStorage = new MockStorage();
    mockStorage.setData(5);
    ByteBuffer theBuffer = StorageIOProcessor.outputFrom(mockStorage);
    mockStorage = new MockStorage();
    StorageIOProcessor.inputTo(mockStorage, theBuffer);
    assertEquals(mockStorage.data(), 5);
  }

  @After
  public void teardown() {
    UserRepository.unregisterUser(player);
  }

  static class MockStorage implements Storage {
    private int data;

    @Override
    public void writeTo(ByteArrayDataOutput output) {
      output.writeInt(data);
    }

    @Override
    public void readFrom(ByteArrayDataInput input) {
      data = input.readInt();
    }

    public int data() {
      return data;
    }

    public void setData(int data) {
      this.data = data;
    }
  }
}
