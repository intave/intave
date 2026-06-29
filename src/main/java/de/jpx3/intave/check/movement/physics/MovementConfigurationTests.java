package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;

import java.util.concurrent.ThreadLocalRandom;

public final class MovementConfigurationTests extends IntegrationTests {
  public MovementConfigurationTests() {
    super("MC");
  }

  @Test(
    severity = Severity.ERROR
  )
  public void testKeys() {
    MovementConfiguration conf = MovementConfiguration.blank();
    conf = conf.withReduceTicks(1);
    conf = conf.withForward(1);
    conf = conf.withSprinting();

    assertEquals(1, conf.forward());
    assertEquals(0, conf.strafe());
    assertTrue(conf.isReducing());
    assertEquals(1, conf.reduceTicks());
    assertTrue(conf.isSprinting());

    conf = conf.withoutSprinting();
    assertFalse(conf.isSprinting());
  }

  @Test(
    severity = Severity.ERROR
  )
  public void testSprint() {
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withSprinting();
      assertTrue(value.isSprinting());
    }
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withoutSprinting();
      assertFalse(value.isSprinting());
    }
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withForward(ThreadLocalRandom.current().nextInt(-1, 2));
      value = value.withStrafe(ThreadLocalRandom.current().nextInt(-1, 2));
      value = value.withReduceTicks(ThreadLocalRandom.current().nextInt(0, 2));
      value = value.withJump();

      value = value.withSprintingSetTo(true);
      assertTrue(value.isSprinting());

      value = value.withSprintingSetTo(false);
      assertFalse(value.isSprinting());
    }
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withJump();
      assertTrue(value.isJumping());
    }
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withoutJump();
      assertFalse(value.isJumping());
    }
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withStrafe(1);
      assertEquals(1, value.strafe());
    }
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withStrafe(-1);
      assertEquals(-1, value.strafe());
    }
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withStrafe(0);
      assertEquals(0, value.strafe());
    }
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withForward(1);
      assertEquals(1, value.forward());
    }
    for (MovementConfiguration value : MovementConfiguration.values()) {
      value = value.withForward(-1);
      assertEquals(-1, value.forward());
    }
  }

  @Test(
    severity = Severity.ERROR
  )
  public void testReduce() {
    for (MovementConfiguration value : MovementConfiguration.values()) {
      int randomTicks = ThreadLocalRandom.current().nextInt(0, 2);
      value = value.withReduceTicks(randomTicks);
      assertEquals(randomTicks, value.reduceTicks());
      value = value.withReduceTicks(0);
      assertEquals(0, value.reduceTicks());
    }

    MovementConfiguration configuration = MovementConfiguration.blank();
    configuration = configuration.withForward(1);
//    System.out.println(configuration.bitString());
    configuration = configuration.withStrafe(1);
//    System.out.println(configuration.bitString());
    configuration = configuration.withReduceTicks(0);
//    System.out.println(configuration.bitString());
    configuration = configuration.withSprintingSetTo(false);
//    System.out.println(configuration.bitString());
    configuration = configuration.withJumped(true);
//    System.out.println(configuration.bitString());
    configuration = configuration.withHandActive(false);
//    System.out.println(configuration.bitString());


  }
}
