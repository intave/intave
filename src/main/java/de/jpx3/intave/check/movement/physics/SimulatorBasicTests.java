package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.share.Motion;
import de.jpx3.intave.test.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

public final class SimulatorBasicTests extends Tests {
  private User testUser;
  private Player player;
  private TestWorld testWorld;

  public SimulatorBasicTests() {
    super("PHYBA");
  }

  @Before
  public void setupMovementTest() {
    player = FakePlayerFactory.createPlayer(
      (s, objects) -> {
        if (s.equals("getWorld")) {
          return testWorld.bukkitWorld();
        }
        return null;
      }
    );
    testUser = UserFactory.createTestUserFor(player, 47);
    testWorld = TestWorld.createLoaded();
  }

  @Test(
    testCode = "A",
    severity = Severity.ERROR
  )
  public void simpleFallingTest() {
    double[][] relativeMotion = new double[60][3];
    for (int i = 1; i < relativeMotion.length; i++) {
      relativeMotion[i][0] = 0;
      relativeMotion[i][1] = (relativeMotion[i - 1][1] - 0.08) * 0.98;
      relativeMotion[i][2] = 0;
    }
    Simulator simulator = Simulators.PLAYER;
    MovementConfiguration configuration = MovementConfiguration.empty();
    TestSimulationEnvironment environment = new TestSimulationEnvironment();

    for (int i = 1; i < relativeMotion.length; i++) {
      double lastMotionX = relativeMotion[i - 1][0];
      double lastMotionY = relativeMotion[i - 1][1];
      double lastMotionZ = relativeMotion[i - 1][2];
      Motion lastMotion = new Motion(lastMotionX, lastMotionY, lastMotionZ);

      double motionX = relativeMotion[i][0];
      double motionY = relativeMotion[i][1];
      double motionZ = relativeMotion[i][2];
      Motion motion = new Motion(motionX, motionY, motionZ);

      Simulation simulation = simulator.simulate(
        testUser,
        lastMotion,
        environment,
        configuration
      );

      double accuracy = simulation.accuracy(motion);
      if (accuracy > 0.001) {
        throw new IllegalStateException("Simulation accuracy deviation: " + accuracy);
      }
    }
  }

  @After
  public void tearDownMovementTest() {
    UserRepository.unregisterUser(player);
    testWorld.erase();
  }
}
