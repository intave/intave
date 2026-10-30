package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Teleport;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class TeleportFeedbackLockOrderingTest {
  private User user;
  private MovementMetadata movement;
  private ReentrantLock feedbackAndTeleportLock;
  private TeleportController controller;
  private final List<Teleport> sent = new ArrayList<>();
  private final Queue<Runnable> scheduled = new ArrayDeque<>();

  @BeforeEach
  void setup() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    user = UserFactory.createFallback();
    user.meta().protocol().setProtocolVersion(774);
    movement = user.meta().movement();
    movement.setVerifiedLastPosition(new Position(0, 64, 0), "lock ordering baseline");
    movement.setBaseMotion(Motion.newEmpty());
    feedbackAndTeleportLock = user.meta().connection().feedbackAndTeleportLock;
    controller = new TeleportController((u, task) -> scheduled.add(task), (u, teleport) -> {
      // Model transmitTeleport's first tickFeedback call without a live server.
      feedbackAndTeleportLock.lock();
      try {
        sent.add(teleport);
      } finally {
        feedbackAndTeleportLock.unlock();
      }
    });
  }

  @Test
  void differentUsersHaveIndependentSynchronizationLocks() throws Exception {
    User other = UserFactory.createFallback();
    assertNotSame(feedbackAndTeleportLock, other.meta().connection().feedbackAndTeleportLock);
    FutureTask<Long> task = new FutureTask<>(() -> other.meta().movement().beginRecovery());
    Thread worker = new Thread(task, "other-user-recovery-test");
    worker.setDaemon(true);
    feedbackAndTeleportLock.lock();
    try {
      worker.start();
      assertTrue(task.get(5, TimeUnit.SECONDS) >= 0);
    } finally {
      feedbackAndTeleportLock.unlock();
      task.get(5, TimeUnit.SECONDS);
      worker.join(5000);
    }
  }

  @Test
  void recoveryStateWaitsForTheFeedbackOwner() throws Exception {
    assertOutgoingTeleportCanProceed(() -> movement.beginRecovery());
    assertTrue(movement.inRecovery);
    assertTrue(sent.isEmpty());
  }

  @Test
  void timeoutRetryDoesNotBlockAnOutgoingTeleportWhileWaitingForFeedback() throws Exception {
    sendInitialTeleport();
    Teleport original = sent.get(0);

    assertOutgoingTeleportCanProceed(() -> controller.onResendTimeout(user));

    assertEquals(2, sent.size());
    Teleport retry = sent.get(1);
    assertEquals(original.id(), retry.id());
    assertEquals(original.uniqueId(), retry.uniqueId());
    assertEquals(original.change(), retry.change());
    assertSame(retry, movement.pendingTeleports.get().getFirst());
  }

  @Test
  void feedbackCallbackRetryCompletesWithNestedLockOwnership() throws Exception {
    sendInitialTeleport();
    Teleport original = sent.get(0);
    controller.beforeTeleportTransactionReceive(user, original);

    assertOutgoingTeleportCanProceed(() -> controller.afterTeleportTransactionReceive(user, original));

    assertEquals(2, sent.size());
    assertSame(sent.get(1), movement.pendingTeleports.get().getFirst());
  }

  @Test
  void newTeleportDoesNotBlockAnOutgoingTeleportWhileWaitingForFeedback() throws Exception {
    assertOutgoingTeleportCanProceed(this::sendInitialTeleport);
    assertEquals(1, sent.size());
  }

  @Test
  void scheduledCorrectionWaitsForTheFeedbackOwner() throws Exception {
    controller.movementCorrection(user, target());
    assertEquals(1, scheduled.size());

    assertOutgoingTeleportCanProceed(scheduled.remove());

    assertEquals(1, sent.size());
    assertTrue(movement.inRecovery);
  }

  @Test
  void immediateCorrectionSchedulerReentersTheSharedLock() throws Exception {
    TeleportController immediate = new TeleportController((u, task) -> task.run(), (u, teleport) -> {
      feedbackAndTeleportLock.lock();
      try {
        sent.add(teleport);
      } finally {
        feedbackAndTeleportLock.unlock();
      }
    });

    assertOutgoingTeleportCanProceed(() -> immediate.movementCorrection(user, target()));

    assertEquals(1, sent.size());
  }

  private void assertOutgoingTeleportCanProceed(Runnable operation) throws Exception {
    FutureTask<Void> task = new FutureTask<>(operation, null);
    Thread worker = new Thread(task, "teleport-retry-test");
    worker.setDaemon(true);
    // The server's feedback send holds this lock while Connection.flushQueue()
    // invokes the outgoing teleport listener. Netty concurrently starts a retry.
    feedbackAndTeleportLock.lock();
    try {
      int sentBefore = sent.size();
      long sequenceBefore = movement.teleportSequence;
      worker.start();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!feedbackAndTeleportLock.hasQueuedThread(worker) && !task.isDone() && System.nanoTime() < deadline) {
        Thread.sleep(1);
      }
      assertTrue(feedbackAndTeleportLock.hasQueuedThread(worker), "worker must reach the contended feedback lock");
      boolean acquired = user.meta().connection().feedbackAndTeleportLock.tryLock(1, TimeUnit.SECONDS);
      assertTrue(acquired, "outgoing teleport must re-enter feedbackAndTeleportLock while the retry waits");
      try {
        assertEquals(2, feedbackAndTeleportLock.getHoldCount(), "outgoing listener must re-enter the feedback owner's lock");
        assertFalse(task.isDone());
        assertEquals(sentBefore, sent.size());
        assertEquals(sequenceBefore, movement.teleportSequence);
      } finally {
        user.meta().connection().feedbackAndTeleportLock.unlock();
      }
    } finally {
      // Even the broken implementation can finish once feedback is released.
      feedbackAndTeleportLock.unlock();
      task.get(5, TimeUnit.SECONDS);
      worker.join(5000);
    }
    assertFalse(feedbackAndTeleportLock.isLocked());
    assertFalse(user.meta().connection().feedbackAndTeleportLock.isLocked());
  }

  @Test
  void teleportOwnerCanSendFeedbackWhileAnotherFeedbackSenderWaits() throws Exception {
    FutureTask<Void> task = new FutureTask<>(() -> {
      feedbackAndTeleportLock.lock();
      try {
        assertEquals(1, feedbackAndTeleportLock.getHoldCount());
        assertTrue(user.meta().connection().feedbackAndTeleportLock.isHeldByCurrentThread());
      } finally {
        feedbackAndTeleportLock.unlock();
      }
      return null;
    });
    Thread worker = new Thread(task, "feedback-sender-test");
    worker.setDaemon(true);
    user.meta().connection().feedbackAndTeleportLock.lock();
    try {
      worker.start();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!feedbackAndTeleportLock.hasQueuedThread(worker) && !task.isDone() && System.nanoTime() < deadline) {
        Thread.sleep(1);
      }
      assertTrue(feedbackAndTeleportLock.hasQueuedThread(worker));
      assertTrue(feedbackAndTeleportLock.tryLock(1, TimeUnit.SECONDS));
      try {
        assertEquals(2, user.meta().connection().feedbackAndTeleportLock.getHoldCount());
        assertFalse(task.isDone());
      } finally {
        feedbackAndTeleportLock.unlock();
      }
    } finally {
      user.meta().connection().feedbackAndTeleportLock.unlock();
      task.get(5, TimeUnit.SECONDS);
      worker.join(5000);
    }
    assertFalse(feedbackAndTeleportLock.isLocked());
  }

  @Test
  void failedNestedTransmissionReleasesTheSharedLock() {
    IllegalStateException failure = new IllegalStateException("transport failed");
    TeleportController failing = new TeleportController((u, task) -> task.run(), (u, teleport) -> {
      feedbackAndTeleportLock.lock();
      try {
        throw failure;
      } finally {
        feedbackAndTeleportLock.unlock();
      }
    });

    assertSame(failure, assertThrows(IllegalStateException.class, () -> failing.movementCorrection(user, target())));
    assertFalse(feedbackAndTeleportLock.isLocked());
    assertFalse(user.meta().connection().feedbackAndTeleportLock.isLocked());
  }

  private void sendInitialTeleport() {
    controller.teleport(user, target(), EnumSet.noneOf(Relative.class));
  }

  private PositionMoveRotation target() {
    return PositionMoveRotation.withoutRotation(new Position(0, 70, 0), Motion.newEmpty());
  }
}
