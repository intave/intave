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

package de.jpx3.intave.packet;

// Before = what we see from client/server
// After = what we send to client/server
public enum ListenerSlot {
	BEFORE_IMMUTABLE,
	PROCESSING_MUTABLE,
	AFTER_IMMUTABLE;

	public boolean allowMutations() {
		return this == PROCESSING_MUTABLE;
	}
}
