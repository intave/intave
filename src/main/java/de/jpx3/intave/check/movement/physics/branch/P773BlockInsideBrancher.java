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

package de.jpx3.intave.check.movement.physics.branch;

import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.util.Collection;

final class P773BlockInsideBrancher extends MovementSearchBrancher {
	@Override
	public void branch(
		MovementSearchInput input,
		MovementSearchBranch inputBranch,
		Collection<MovementSearchBranch> outputBranches
	) {
		int protocolVersion = input.user().protocolVersion();
		if (protocolVersion != ProtocolMetadata.VER_1_21_9 || !input.blockInsideCheckBranchNecessary()) {
			outputBranches.add(inputBranch);
			return;
		}

		outputBranches.add(inputBranch.withAlternativeBlockInsideCheck(false));
		outputBranches.add(inputBranch.withAlternativeBlockInsideCheck(true));
	}
}
