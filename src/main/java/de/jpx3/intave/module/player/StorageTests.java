package de.jpx3.intave.module.player;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import de.jpx3.intave.access.player.storage.MemoryStorageGateway;
import de.jpx3.intave.access.player.storage.StorageGateway;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.test.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.*;
import org.bukkit.entity.Player;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class StorageTests extends IntegrationTests {
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
    StorageLoader storageLoader = Modules.storage();
    exampleGateway = storageLoader.hasStorageGateway() ? storageLoader.storageGateway() : new MemoryStorageGateway();
    player = FakePlayerFactory.createPlayer((s, objects) -> s.equals("getUniqueId") ? ZERO_UUID : null);
    user = UserFactory.createTestUserFor(player);
    UserRepository.manuallyRegisterUser(player, user);
  }

  private ByteBuffer gateawayReturn;

  @Test(
    testCode = "A",
    severity = Severity.ERROR
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
      boolean awaited = latch.await(6, TimeUnit.SECONDS);
      if (!awaited) {
        fail("Storage lookup took too long");
      }
    } catch (InterruptedException ignored) {}

    if (gateawayReturn == null || gateawayReturn.remaining() == 0) {
      fail("Empty return buffer for request");
    }
    if (!gateawayReturn.equals(validByteBuffer)) {
      fail("Does not support override or is fundamentally broken");
    }
  }

  private ByteBuffer returnA, returnB;

  @Test(
    testCode = "B",
    severity = Severity.ERROR
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

    if (!bufferContentsEqual(byteBufferA, returnA)) {
      fail("Incorrect storage return, check if you are managing IDs properly");
    }
    if (!bufferContentsEqual(byteBufferB, returnB)) {
      fail("Incorrect storage return, check if you are managing IDs properly");
    }
  }

  private boolean bufferContentsEqual(ByteBuffer a, ByteBuffer b) {
    byte[] array = a.array();
    byte[] array1 = b.array();
    if (array.length != array1.length) {
      return false;
    }
    for (int i = 0; i < array.length; i++) {
      if (array[i] != array1[i]) {
        return false;
      }
    }
    return true;
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

  @Test(
    testCode = "D",
    severity = Severity.ERROR
  )
  public void testSerialization() {
    UUID playerId = UUID.randomUUID();
    PlayerStorage storage = Storages.emptyPlayerStorageFor(playerId);
    PlaytimeStorage playtimeStorage = storage.storageOf(PlaytimeStorage.class);
    playtimeStorage.incrementMinutesPlayedBy(531);
    playtimeStorage.setDebugTag();
    LongTermViolationStorage violationStorage = storage.storageOf(LongTermViolationStorage.class);
    violationStorage.noteViolation("attackraytrace", 500);
    violationStorage.noteViolation("physics", 800);
    HeuristicsStorage heuristicsStorage = storage.storageOf(HeuristicsStorage.class);
    heuristicsStorage.confidenceNote(103);
    NerferStorage nerferStorage = storage.storageOf(NerferStorage.class);
    nerferStorage.addNerfer("CC", System.currentTimeMillis() + 5_000);
    nerferStorage.addNerfer("DD", System.currentTimeMillis() + 10_000);
    AccountDataStorage accountDataStorage = storage.storageOf(AccountDataStorage.class);
    accountDataStorage.setVerified();
    accountDataStorage.setBlocked();
    FeedbackAnalysisStorage feedbackAnalysisStorage = storage.storageOf(FeedbackAnalysisStorage.class);
    long[] counts = new long[100];
    for (int i = 0; i < counts.length; i++) {
      counts[i] = ThreadLocalRandom.current().nextLong(6, 800);
    }
    feedbackAnalysisStorage.setCounts(counts);
    feedbackAnalysisStorage.setAccumulatedLatencies(counts);
    ViolationBufferStorage violationBufferStorage = storage.storageOf(ViolationBufferStorage.class);
    violationBufferStorage.trySpendPoint("physics", 8000, 2);
    LatencyStorage latencyStorage = storage.storageOf(LatencyStorage.class);
    latencyStorage.backtrackVL = 64;
    latencyStorage.buckets = 100;
    latencyStorage.latencyBuckets = new long[100];
    latencyStorage.lastUpdate = System.currentTimeMillis();
    for (int i = 0; i < latencyStorage.latencyBuckets.length; i++) {
      latencyStorage.latencyBuckets[i] = ThreadLocalRandom.current().nextLong(6, 800);
    }
    ShortTermViolationStorage shortTermViolationStorage = storage.storageOf(ShortTermViolationStorage.class);
    shortTermViolationStorage.setViolation("physics", "404", 8000);

    ByteBuffer buffer = StorageIOProcessor.outputFrom(storage);

    PlayerStorage storage1 = Storages.emptyPlayerStorageFor(playerId);
    StorageIOProcessor.inputTo(storage1, buffer);

    if (!storage1.sameContentsAs((Storage) storage)) {
      fail("Storage serialization failed");
    }
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

    @Override
    public int version() {
      return 0;
    }

    @Override
    public boolean sameContentsAs(Storage other) {
      if (!(other instanceof MockStorage)) {
        return false;
      }
      MockStorage otherStorage = (MockStorage) other;
      return otherStorage.data == data;
    }

    public int data() {
      return data;
    }

    public void setData(int data) {
      this.data = data;
    }
  }
}
