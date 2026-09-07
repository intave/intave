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

package de.jpx3.intave.packet.view;

import de.jpx3.intave.packet.reader.AnimationReader;
import de.jpx3.intave.packet.reader.BedUseReader;
import de.jpx3.intave.share.BlockPosition;
import org.bukkit.entity.Player;

/**
 * {@link AnimationView} backed by ProtocolLib.
 * <p>
 * Both consumers receive their reader already injected by the subscription linker, so this wraps
 * the pooled reader rather than a raw packet event and the existing pooling and release semantics
 * stay exactly as they were. Which of the two constructors was used decides whether the view
 * reports an animation or a bed position.
 */
public final class ProtocolLibAnimationView implements AnimationView {

  private final Player player;
  private final AnimationReader animationReader;
  private final BedUseReader bedUseReader;

  public ProtocolLibAnimationView(Player player, AnimationReader reader) {
    this.player = player;
    this.animationReader = reader;
    this.bedUseReader = null;
  }

  public ProtocolLibAnimationView(Player player, BedUseReader reader) {
    this.player = player;
    this.animationReader = null;
    this.bedUseReader = reader;
  }

  /** @return the wrapped animation reader, or null for the use bed packet. */
  public AnimationReader animationReader() {
    return animationReader;
  }

  /** @return the wrapped use bed reader, or null for the entity animation packet. */
  public BedUseReader bedUseReader() {
    return bedUseReader;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public int entityId() {
    return animationReader != null ? animationReader.entityId() : bedUseReader.entityId();
  }

  @Override
  public boolean isBedUse() {
    return bedUseReader != null;
  }

  @Override
  public AnimationReader.Animation animation() {
    return animationReader == null ? null : animationReader.animation();
  }

  @Override
  public BlockPosition bedPosition() {
    return bedUseReader == null ? null : bedUseReader.bedPosition();
  }

  @Override
  public void release() {
    // The subscription linker releases the pooled reader once the subscription returns; going
    // through releaseSafe keeps this idempotent so an early release here changes nothing.
    if (animationReader != null) {
      animationReader.releaseSafe();
    } else {
      bedUseReader.releaseSafe();
    }
  }
}
