/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.block.store;

import de.jpx3.intave.share.BlockCacheEntry;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

public final class SynchronizedBlockStore implements BlockStore {
	private final BlockStore delegate;

	private final Lock readLock;
	private final Lock writeLock;

	private SynchronizedBlockStore(ReadWriteLock lock, BlockStore delegate) {
		this.delegate = delegate;
		this.readLock = lock.readLock();
		this.writeLock = lock.writeLock();
	}

	@Override
	public BlockCacheEntry get(int x, int y, int z) {
		readLock.lock();
		try {
			return delegate.get(x, y, z);
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public boolean put(int x, int y, int z, BlockCacheEntry state) {
		writeLock.lock();
		try {
			return delegate.put(x, y, z, state);
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public int size() {
		readLock.lock();
		try {
			return delegate.size();
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public void removeIf(Predicate<BlockCacheEntry> predicate) {
		writeLock.lock();
		try {
			delegate.removeIf(predicate);
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public void clear() {
		writeLock.lock();
		try {
			delegate.clear();
		} finally {
			writeLock.unlock();
		}
	}

	public static SynchronizedBlockStore of(BlockStore delegate) {
		return new SynchronizedBlockStore(new ReentrantReadWriteLock(), delegate);
	}
}
