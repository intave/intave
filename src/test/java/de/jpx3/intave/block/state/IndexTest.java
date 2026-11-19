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

package de.jpx3.intave.block.state;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class IndexTest {
	@Test
	void sharedIndexExposesReadOnlyProtocolsAndSourceMetadata() {
		Index index = Index.get();
		String version = index.protocols().get("47");
		Map<String, Comparable<?>> metadata = index.sources().get(version);
		assertNotNull(metadata);

		assertThrows(UnsupportedOperationException.class, () -> index.protocols().remove("47"));
		assertThrows(UnsupportedOperationException.class, () -> index.sources().remove(version));
		assertThrows(UnsupportedOperationException.class, () -> metadata.put("states", 0));
	}
}
