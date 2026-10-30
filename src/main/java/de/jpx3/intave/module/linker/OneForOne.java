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

package de.jpx3.intave.module.linker;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.Function;

public class OneForOne<T extends LISTENER, LISTENER> implements SubscriptionInstanceProvider<User, T, LISTENER> {
  private final Function<? super User, ? extends T> supplier;
  private final T fallback;
  private final Class<T> subscriberClass;

  public OneForOne(Function<? super User, ? extends T> supplier) {
    this.supplier = supplier;
    this.fallback = supplier.apply(UserRepository.userOf((Player) null));
	  //noinspection unchecked
	  this.subscriberClass = (Class<T>) fallback.getClass();
  }

  @Override
  public Class<T> type() {
    return subscriberClass;
  }

  @Override
  public void apply(User user, Consumer<? super T> consumer) {
    consumer.accept(supplier.apply(user));
  }

  @Override
  public T fallback() {
    return fallback;
  }
}
