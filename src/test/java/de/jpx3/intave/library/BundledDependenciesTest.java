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

package de.jpx3.intave.library;

import com.github.luben.zstd.Zstd;
import de.jpx3.intave.share.Certificate;
import de.jpx3.intave.module.patcher.DSILongSetWrapper;
import de.jpx3.intave.module.patcher.SynchronizedDSILongHashSet;
import de.jpx3.intave.module.patcher.SynchronizedLongArraySet;
import de.jpx3.intave.test.FakePlayerFactory;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.bytebuddy.ByteBuddy;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class BundledDependenciesTest {
  @Test
  void packagedClassesComeFromThePluginJar() throws Exception {
    String jar = System.getProperty("intave.test.shadedJar");
    assumeTrue(jar != null, "Only applies to testShadedJar");
    assertTrue(Libraries.isBundled());
    Libraries.setupLibraries(message -> fail("The bundled build must not download libraries: " + message));
    File expected = new File(jar).getCanonicalFile();
    for (Class<?> type : new Class<?>[] {
      Certificate.class, ByteBuddy.class, Long2ObjectOpenHashMap.class,
      X500Name.class, X509CertificateHolder.class, OIWObjectIdentifiers.class, Zstd.class
    }) {
      assertEquals(expected,
        new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalFile(),
        type.getName());
    }
  }

  @Test
  void nativeCompressionRoundTrips() {
    byte[] payload = "Intave bundled native compression".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(payload, Zstd.decompress(Zstd.compress(payload), payload.length));
  }

  @Test
  void generatedPlayerRetainsItsDelegatedIdentity() {
    Player player = FakePlayerFactory.createPlayer();
    assertEquals("TESTPLAYER", player.getName());
    assertEquals(new UUID(0, 0), player.getUniqueId());
  }

  @Test
  void primitiveMapSurvivesGrowthAndRemoval() {
    Long2ObjectOpenHashMap<String> map = new Long2ObjectOpenHashMap<>();
    for (long key = 0; key < 100; key++) {
      map.put(key, Long.toString(key));
    }
    for (long key = 0; key < 100; key++) {
      assertEquals(Long.toString(key), map.remove(key));
    }
    assertTrue(map.isEmpty());
  }

  @Test
  void serverUnloadQueuesRetainInheritedCollectionOperations() {
    for (LongSet queue : new LongSet[] {
      new SynchronizedLongArraySet(), new SynchronizedDSILongHashSet(),
      new DSILongSetWrapper(new SynchronizedLongArraySet())
    }) {
      queue.add(41L);
      assertTrue(queue.contains(41L));
      assertArrayEquals(new long[] {41L}, queue.toLongArray());
      assertEquals(41L, queue.iterator().nextLong());
      assertEquals(1L, queue.spliterator().estimateSize());
      assertTrue(queue.remove(41L));
      assertTrue(queue.isEmpty());
    }
  }
}
