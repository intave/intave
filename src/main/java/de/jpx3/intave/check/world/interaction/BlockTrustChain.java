package de.jpx3.intave.check.world.interaction;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.share.BlockPosition;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BlockTrustChain {
  public static final int MAXIMUM_CHAIN_LENGTH = 5;
  public static final int MAXIMUM_CHAIN_AGE = 5000;

  private final Map<BlockPosition, Queue<BlockTrust>> pendingMap = new ConcurrentHashMap<>();
  private final ReentrantLock lock = new ReentrantLock();

  public boolean tryAction(BlockPosition position, BlockPosition anchor) {
    lock.lock();
    try {
      BlockTrust anchorTrust = pendingMap.get(anchor) == null ? null : pendingMap.get(anchor).peek();
      if (IntaveControl.DEBUG_INTERACTION_TRUST_CHAIN) {
        System.out.println("tryAction(" + position + ", " + anchor + ")");
      }
      if (anchorTrust != null) {
        if (IntaveControl.DEBUG_INTERACTION_TRUST_CHAIN) {
          System.out.println("  anchorTrust = " + anchorTrust);
        }
        int chainLength = anchorTrust.chainLength();
        if (!anchorTrust.hasBenefitOfDoubt() || chainLength > MAXIMUM_CHAIN_LENGTH) {
          if (IntaveControl.DEBUG_INTERACTION_TRUST_CHAIN) {
            System.out.println("  return false, benefitOfDoubt = " + anchorTrust.hasBenefitOfDoubt() + ", chainLength = " + (chainLength));
          }
          return false;
        }
      }
      clearExpired();
      // if anchor is not pending, placement is not speculative
      pendingMap
        .computeIfAbsent(position, x -> new LinkedList<>())
        .add(new BlockTrust(anchorTrust, nextTick()));
      if (IntaveControl.DEBUG_INTERACTION_TRUST_CHAIN) {
        System.out.println("  return true");
      }
      return true;
    } finally {
      lock.unlock();
    }
  }

  public boolean collapseState(
    BlockPosition position, boolean valid
  ) {
    lock.lock();
    try {
      Queue<BlockTrust> queue = pendingMap.get(position);
      BlockTrust trust = queue == null ? null : queue.peek();
      if (IntaveControl.DEBUG_INTERACTION_TRUST_CHAIN) {
        System.out.println("collapseState(" + position + ", " + valid + ")");
        System.out.println("  trust = " + trust);
      }
      if (trust == null) {
        return false;
      }
      for (Queue<BlockTrust> value : pendingMap.values()) {
        for (BlockTrust blockTrust : value) {
          if (blockTrust.anchor != null && blockTrust.anchor.equals(trust)) {
            if (!valid) {
              blockTrust.invalidate();
            }
            // we clear that to avoid memory leaks,
            // trust-knot is still invalidated
            blockTrust.clearAnchor();
          }
        }
      }
      if (!valid) {
        // We keep the invalidated block-trust in the chain,
        // until the client has received and acknowledged the
        // block change packet to AIR by the server.
        trust.invalidate();
        return true;
      } else {
        queue.remove();
        return false;
      }
    } finally {
      lock.unlock();
    }
  }

  public void removeCollapsedState(BlockPosition position) {
    lock.lock();
    try {
      Queue<BlockTrust> queue = pendingMap.get(position);
      if (queue != null && !queue.isEmpty()) {
        queue.remove();
      }
      if (queue != null && queue.isEmpty()) {
        pendingMap.remove(position);
      }
    } finally {
      lock.unlock();
    }
  }

  private long currentTick;

  private long nextTick() {
    return currentTick++;
  }

  private void clearExpired() {
    pendingMap.values().removeIf(queue -> {
      queue.removeIf(BlockTrust::expired);
      return queue.isEmpty();
    });
  }

  public static class BlockTrust {
    // trust anchor, null if placement not speculative
    private BlockTrust anchor;
    private final long birth = System.currentTimeMillis();
    private final long tick;
    private boolean benefitOfDoubt = true;

    public BlockTrust(BlockTrust anchor, long tick) {
      this.anchor = anchor;
      this.tick = tick;
    }

    public void clearAnchor() {
      if (!anchor.hasBenefitOfDoubt()) {
        benefitOfDoubt = false;
      }
      anchor = null;
    }

    public void invalidate() {
      benefitOfDoubt = false;
    }

    public boolean hasBenefitOfDoubt() {
      return benefitOfDoubt && System.currentTimeMillis() - birth < MAXIMUM_CHAIN_AGE &&
        (anchor == null || anchor.hasBenefitOfDoubt());
    }

    public int chainLength() {
      return anchor == null ? 0 : 1 + anchor.chainLength();
    }

    public boolean isSpeculative() {
      return anchor != null;
    }

    public long tick() {
      return tick;
    }

    public boolean expired() {
      return System.currentTimeMillis() - birth > MAXIMUM_CHAIN_AGE;
    }

    @Override
    public String toString() {
      return "BlockTrust{" +
        "trustsIn=" + anchor +
        ", benefitOfDoubt=" + benefitOfDoubt +
        '}';
    }
  }
}
