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

package de.jpx3.intave.access;

import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.access.check.Check;
import de.jpx3.intave.access.check.CheckAccess;
import de.jpx3.intave.access.check.UnknownCheckException;
import de.jpx3.intave.access.player.PlayerAccess;
import de.jpx3.intave.access.player.UnknownPlayerException;
import de.jpx3.intave.access.player.storage.StorageGateway;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import de.jpx3.intave.access.server.ServerAccess;
import org.bukkit.entity.Player;

import java.io.PrintStream;
import java.lang.ref.WeakReference;

/**
 * This is the main gateway interface for access to Intave, hence the name.
 * It will be made available to the user either via {@link IntaveAccessor#unsafeAccess()}
 * or via {@link WeakReference} at {@link IntaveAccessor#weakAccess()}.
 */
public interface IntaveAccess {

  /**
   * Overrides the global trustfactor resolver with a new one.
   * See {@link TrustFactorResolver} on how they work.
   * If a <code>null</code> reference is passed, Intave will
   * fall back to its default trustfactor resolver.
   *
   * @param resolver The overriding resolver
   */
  void setTrustFactorResolver(TrustFactorResolver resolver);

  /**
   * Sets a new global default trustfactor.
   * This trustfactor is assigned to all new players,
   * unless stated differently with a {@link TrustFactorResolver}
   *
   * @param defaultTrustFactor the new default trustfactor
   */
  void setDefaultTrustFactor(TrustFactor defaultTrustFactor);

  /**
   * Subscribe a {@link PrintStream} to Intave's console output.
   *
   * @param stream the stream to receive Intave's console output.
   */
  void subscribeOutputStream(PrintStream stream);

  /**
   * Remove a subscription to Intave's console output.
   *
   * @param stream the stream to unsubscribe
   */
  void unsubscribeOutputStream(PrintStream stream);

  void setStorageGateway(StorageGateway gateway);

  /**
   * Retrieves player-specific access control in {@link PlayerAccess}.
   *
   * @param player the specified player
   * @return an access controller to enter a player-constraint context
   * @throws UnknownPlayerException in case the player can't be found
   */
  PlayerAccess player(Player player);

  /**
   * Retrieves server access control in {@link ServerAccess}
   *
   * @return an access controller to enter server context
   */
  ServerAccess server();

  /**
   * Retrieves check-specific access control in {@link CheckAccess}
   *
   * @param checkName the name of the requested check
   * @return an access controller to enter check access context
   * @throws UnknownCheckException in case the check can't be found
   */
  CheckAccess check(String checkName) throws UnknownCheckException;

  /**
   * Retrieves check-specific access control in {@link CheckAccess}
   *
   * @param check the enum type of the requested check
   * @return an access controller to enter check access context
   */
  CheckAccess check(Check check);

  void fallback(Object object);
}
