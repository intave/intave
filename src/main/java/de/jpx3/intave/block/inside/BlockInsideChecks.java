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

package de.jpx3.intave.block.inside;

import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BlockInsideChecks {
	private static final BlockInsideCheck V21 = new v21BlockInsideCheck();
	private static final BlockInsideCheck V21_2 = new v212BlockInsideCheck();
	private static final BlockInsideCheck V21_4 = new v214BlockInsideCheck();
	private static final BlockInsideCheck V21_5 = new v215BlockInsideCheck();
	private static final BlockInsideCheck V21_6 = new v216BlockInsideCheck();
	private static final BlockInsideCheck V21_9 = new v219BlockInsideCheck();
	private static final BlockInsideCheck V21_10 = new v2110BlockInsideCheck();

	private BlockInsideChecks() {
	}

	public static List<BlockInsideCheck> suitableFor(User user) {
		return suitableForProtocol(user.protocolVersion());
	}

	public static BlockInsideCheck any() {
		return V21;
	}

	public static BlockInsideCheck select(
		User user,
		List<BlockInsideCheck> checks,
		MovementConfiguration configuration
	) {
		if (checks.size() == 1) {
			return checks.get(0);
		}
		if (checks.size() == 2) {
			return configuration.usesAlternateBlockInsideCheck() ? checks.get(0) : checks.get(1);
		}
		throw new IllegalArgumentException("Expected 1 or 2 block inside checks, but got " + checks.size() + " for user " + user.player().getDisplayName());
	}

	static BlockInsideCheck suitableForProtocol(
		int protocolVersion,
		MovementConfiguration configuration
	) {
		if (protocolVersion == ProtocolMetadata.VER_1_21_9) {
			return configuration.usesAlternateBlockInsideCheck() ? V21_9 : V21_10;
		}
		return suitableForProtocol(protocolVersion).get(0);
	}

	static List<BlockInsideCheck> suitableForProtocol(int protocolVersion) {
		if (protocolVersion > ProtocolMetadata.VER_1_21_9) {
			return Collections.singletonList(V21_10);
		} else if (protocolVersion == ProtocolMetadata.VER_1_21_9) {
			// 1.21.9 and 1.21.10 share protocol 773. Default to 1.21.10 and branch to 1.21.9.
			return Arrays.asList(V21_9, V21_10);
		} else if (protocolVersion >= ProtocolMetadata.VER_1_21_6) {
			return Collections.singletonList(V21_6);
		} else if (protocolVersion >= ProtocolMetadata.VER_1_21_5) {
			return Collections.singletonList(V21_5);
		} else if (protocolVersion >= ProtocolMetadata.VER_1_21_4) {
			return Collections.singletonList(V21_4);
		} else if (protocolVersion >= ProtocolMetadata.VER_1_21_2) {
			return Collections.singletonList(V21_2);
		}
		return Collections.singletonList(V21);
	}

	static BlockInsideCheck v21_9() {
		return V21_9;
	}
}
