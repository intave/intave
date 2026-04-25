package de.jpx3.intave.user;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.fluid.FluidFlow;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.connect.customclient.CustomClientSupportConfig;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.module.actionbar.DisplayType;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.feedback.FeedbackObserver;
import de.jpx3.intave.module.feedback.FeedbackOptions;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.placeholder.Placeholders;
import de.jpx3.intave.module.violation.placeholder.PlayerContext;
import de.jpx3.intave.module.violation.placeholder.UserContext;
import de.jpx3.intave.player.collider.complex.Collider;
import de.jpx3.intave.player.collider.simple.SimpleCollider;
import de.jpx3.intave.trustfactor.TrustFactorConfiguration;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.permission.PermissionCache;
import de.jpx3.intave.user.storage.Storage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A {@link User} serves as a {@link Player}-oriented extension companion, providing meta storage,
 * a permission cache, player-specified computationals and player-specific attribute accessors and mutators.
 * Every player has a {@link User} storage assigned, accessible via {@link UserRepository#userOf(Player)}.
 *
 * @see UserFactory
 * @see UserRepository
 * @see PlayerUser
 * @see FallbackUser
 */
public interface User {
  UUID id();

  /**
   * Retrieve the player's "handle", the NMS-container-object of a player entity
   *
   * @return the players "handle"
   * @throws UnsupportedFallbackOperationException when no player is present
   */
  Object playerHandle();

  /**
   * Retrieve the player's connection object
   *
   * @return the player's connection
   * @throws UnsupportedFallbackOperationException when no player is present
   */
  Object playerConnection();

  /**
   * Retrieve the {@link User}-associated player
   *
   * @return the player
   * @throws UnsupportedFallbackOperationException when no player is present
   */
  Player player();

  /**
   * Retrieve a user's {@link MetadataBundle}
   *
   * @return a users {@link MetadataBundle}
   */
  MetadataBundle meta();

  /**
   * Internal setup function, do not call
   */
  void delayedSetup();

  /**
   * Let the user know that his protocol version has changed
   */
  void applyNewProtocolVersion();

  /**
   * Retrieve whether the associated player joined in the last 5 seconds
   *
   * @return whether the player joined recently
   */
  boolean justJoined();

  /**
   * Retrieve the join-timestamp of the associated player
   * When no player is present, return System.currentTimeMillies()
   *
   * @return the join-timestamp of the associated player
   */
  long joined();

  /**
   * Retrieve if this {@link User} is {@link Player}-associated or a fallback
   *
   * @return if a player is present
   */
  boolean hasPlayer();

  /**
   * Tick-synchronization aka. feedback.
   * Sends a transaction packet immediately, callback is triggered when response is received.
   * If sent whilst an outbound packet is in outbound queue, the callback is in the correct inbound packet order - before.
   * Can't be replayed or dropped by the client.
   *
   * @param callback the callback
   */
  void tickFeedback(EmptyFeedbackCallback callback);

  /**
   * Same as {@link #tickFeedback(EmptyFeedbackCallback)}, but with options
   * @param callback the callback
   * @param options the options, as defined in {@link FeedbackOptions}
   */
  default void tickFeedback(EmptyFeedbackCallback callback, int options) {
    tickFeedback(callback);
  }

  /**
   * Same as {@link #tickFeedback(EmptyFeedbackCallback)}, but will bundle the transaction packet with the given event
   */
  void packetTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback);

  /**
   * Same as {@link #packetTickFeedback(ProtocolPacketEvent, EmptyFeedbackCallback)}, but with options
   * @param event the packet event
   * @param callback the callback
   * @param options the options, as defined in {@link FeedbackOptions}
   */
  default void packetTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, int options) {
    packetTickFeedback(event, callback);
  }

  /**
   * Same as {@link #tickFeedback(EmptyFeedbackCallback)}, but with a {@link FeedbackObserver}
   * Feedback observer is notified when the packet is sent and when the response is received.
   * @param callback the callback
   * @param tracker a tracker
   */
  void tracedTickFeedback(EmptyFeedbackCallback callback, FeedbackObserver tracker);

  /**
   * Same as {@link #tracedTickFeedback(EmptyFeedbackCallback, FeedbackObserver)}, but with options
   * @param callback the callback
   * @param tracker a tracker
   * @param options the options, as defined in {@link FeedbackOptions}
   */
  default void tracedTickFeedback(EmptyFeedbackCallback callback, FeedbackObserver tracker, int options) {
    tracedTickFeedback(callback, tracker);
  }

  void tracedPacketTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, FeedbackObserver tracker);

  default void tracedPacketTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, FeedbackObserver tracker, int options) {
    tracedPacketTickFeedback(event, callback, tracker);
  }

  /**
   * Double tick-synchronization.
   * Sandwiches a packet between two feedback packets.
   * @param event the packet event
   * @param before first before
   * @param after second before
   */
  void doubleTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback before, EmptyFeedbackCallback after);

  /**
   * Same as {@link #doubleTickFeedback(ProtocolPacketEvent, EmptyFeedbackCallback, EmptyFeedbackCallback)}, but with options
   * @param event the packet event
   * @param callback first callback
   * @param callback2 second callback
   * @param options the options, as defined in {@link FeedbackOptions}
   */
  default void doubleTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, EmptyFeedbackCallback callback2, int options) {
    doubleTickFeedback(event, callback, callback2);
  }

  /**
   * Same as {@link #doubleTickFeedback(ProtocolPacketEvent, EmptyFeedbackCallback, EmptyFeedbackCallback)}, but with a {@link FeedbackObserver}
   * Feedback observer is notified when the packet is sent and when the response is received.
   * @param event the packet event
   * @param callback first callback
   * @param callback2 second callback
   * @param tracker a tracker
   */
  void doubleTracedTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, EmptyFeedbackCallback callback2, FeedbackObserver tracker);

  /**
   * Same as {@link #doubleTracedTickFeedback(ProtocolPacketEvent, EmptyFeedbackCallback, EmptyFeedbackCallback, FeedbackObserver)}, but with options
   * @param event the packet event
   * @param callback first callback
   * @param callback2 second callback
   * @param tracker a tracker
   * @param options the options, as defined in {@link FeedbackOptions}
   */
  default void doubleTracedTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, EmptyFeedbackCallback callback2, FeedbackObserver tracker, int options) {
    doubleTracedTickFeedback(event, callback, callback2, tracker);
  }

  /**
   * Generate-if-absent and retrieve custom check metadata
   *
   * @param classTarget the metadata class
   * @return custom check metadata
   * @see MetaCheck
   * @see MetaCheckPart
   */
  default CheckCustomMetadata checkMetadata(Class<? extends CheckCustomMetadata> classTarget) {
    return checkMetadata(classTarget, user -> newMetadata(classTarget));
  }

  default CheckCustomMetadata newMetadata(Class<? extends CheckCustomMetadata> initializeMe) {
    try {
      return initializeMe.newInstance();
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  /**
   * Generate-if-absent and retrieve custom check metadata
   *
   * @param classTarget the metadata class
   * @return custom check metadata
   * @see MetaCheck
   * @see MetaCheckPart
   */
  CheckCustomMetadata checkMetadata(Class<? extends CheckCustomMetadata> classTarget, @Nullable Function<? super User, ? extends CheckCustomMetadata> generator);

  /**
   * Retrieve custom client settings
   *
   * @return custom client settings
   */
  CustomClientSupportConfig customClientSupport();

  /**
   * Set custom client settings settings
   *
   * @param customClientSupportConfig the new client settings
   */
  void setCustomClientSupport(CustomClientSupportConfig customClientSupportConfig);

  /**
   * Retrieve the player's permission cache
   *
   * @return the player's permission cache
   */
  PermissionCache permissionCache();

  /**
   * Returns whether the next inbound packet will be ignored
   *
   * @return true if the next inbound packet is to be ignored, false if not
   */
  boolean shouldIgnoreNextInboundPacket();

  /**
   * Returns whether the next outbound packet should be ignored
   *
   * @return true if the next outbound packet is to be ignored, false if not
   */
  boolean shouldIgnoreNextOutboundPacket();

  /**
   * Ignore the next inbound packet.
   * This option will reset automatically once the inbound packet has been ignored.
   */
  void ignoreNextInboundPacket();

  /**
   * Ignore the next outbound packet.
   * This option will reset automatically once the outbound packet has been ignored.
   */
  void ignoreNextOutboundPacket();

  /**
   * Do not ignore the next inbound packet.
   * This usually happens automatically after an inbound packet has been ignored.
   */
  void receiveNextInboundPacketAgain();

  /**
   * Do not ignore the next outbound packet.
   * This usually happens automatically after an outbound packet has been ignored.
   */
  void receiveNextOutboundPacketAgain();

  Storage mainStorage();

  void onStorageReady(Consumer<? super Storage> consumer);

  void notifyStorageLoadSubscribers();

  <T extends Storage> T storageOf(Class<T> storageClass);

  /**
   * Retrieve the player's block state cache
   *
   * @return the player's block state cache
   * @see BlockCache
   */
  BlockCache blockCache();

  /**
   * Retrieve the {@link User}-associated {@link Collider}
   *
   * @return the complex collider processor
   */
  Collider collider();

  /**
   * Retrieve the {@link User}-associated {@link FluidFlow}
   * @return
   */
  FluidFlow waterflow();

  /**
   * Retrieve the {@link User}-associated {@link SimpleCollider}
   *
   * @return the simple collider processor
   */
  SimpleCollider simplifiedCollider();

  /**
   * Retrieve the placeholder associated with the present {@link Player}
   * If no player is present, a fallback placeholder is available.
   *
   * @return the player-associated placeholder
   * @see Placeholders
   */
  PlayerContext playerContext();

  /**
   * Retrieve the placeholder associated with the present {@link User}
   *
   * @return the user-associated placeholder
   * @see Placeholders
   */
  UserContext userContext();

  /**
   * Retrieve the users {@link TrustFactor}
   *
   * @return the users trustfactor
   * @see TrustFactor
   * @see TrustFactorResolver
   */
  TrustFactor trustFactor();

  /**
   * Override the users {@link TrustFactor}
   *
   * @param trustFactor the new trustfactor
   * @see TrustFactor
   * @see TrustFactorResolver
   */
  void setTrustFactor(TrustFactor trustFactor);

  /**
   * Retrieve a trustfactor setting of a specific key
   *
   * @param key a unique identifier
   * @return the settings value, as {@link Integer}
   * @see TrustFactorConfiguration#resolveSetting(String, TrustFactor)
   */
  int trustFactorSetting(String key);

  /**
   * Retrieves whether a player is subscribed to a {@link MessageChannel}
   *
   * @param channel the message channel to check
   * @return true when the player is subscribed, false when not
   */
  boolean receives(MessageChannel channel);

  /**
   * Toggles whether a player is subscribed to a {@link MessageChannel}
   *
   * @param channel the message channel to toggle
   */
  void toggleReceive(MessageChannel channel);

  /**
   * Overrides the constraint for a message-channel.
   * It constrains the messages of the message-channel to only be sent
   * when the player causing the message obeys certain constrains, specified via {@link Predicate} of type {@link Player}.
   *
   * @param channel    the selected channel
   * @param constraint the player contraint
   */
  void setChannelConstraint(MessageChannel channel, Predicate<Player> constraint);

  /**
   * Returns whether a message-channel has been constraint
   *
   * @param channel the selected channel
   * @return true if a constraint is present, false if not
   */
  boolean hasChannelConstraint(MessageChannel channel);

  /**
   * Retrieve the constraint of a selected message channel
   *
   * @param channel the selected channel
   * @return the channels constraint
   */
  Predicate<Player> channelPlayerConstraint(MessageChannel channel);

  /**
   * Removes a channels constraint
   *
   * @param channel the selected channel
   */
  void removeChannelConstraint(MessageChannel channel);

  /**
   * Apply an {@link AttackNerfStrategy} to a player
   *
   * @param strategy the strategy to apply
   * @param checkId  the check id (for debug purposes)
   */
  void nerf(AttackNerfStrategy strategy, String checkId);

  /**
   * Apply a one time use {@link AttackNerfStrategy} to a player
   *
   * @param strategy the strategy to apply
   * @param checkId  the check id (for debug purposes)
   */
  void nerfOnce(AttackNerfStrategy strategy, String checkId);

  /**
   * Apply a one time use {@link AttackNerfStrategy} to a player
   *
   * @param strategy the strategy to apply
   * @param checkId  the check id (for debug purposes)
   */
  void nerfPermanently(AttackNerfStrategy strategy, String checkId);

  /**
   * Retrieve a player's packet latency
   *
   * @return a player's packet latency
   */
  int latency();

  /**
   * Retrieve a player's packet latency jitter
   * The jitter describes the amount of fluctuations in a players latency
   *
   * @return a player's packet latency jitter
   */
  int latencyJitter();

  /**
   * Retrieve a player's protocol version.
   * See <a href="https://wiki.vg/Protocol_version_numbers">protocol versions</a> for more information.
   *
   * @return a player's protocol version
   */
  int protocolVersion();

  /**
   * Retrieve the {@link HitboxSize} of a {@link Pose}.
   * This is {@link User}-dependant due to changes of boundaries between Minecraft versions.
   *
   * @param pose the selected pose
   * @return the hitbox boundaries
   */
  HitboxSize sizeOf(Pose pose);

  /**
   * Clear any relevant type translations of the user
   */
  void clearTypeTranslations();

  /**
   * Apply a Material-type translation from a source to a target material
   * All systems will reinterpret the material, effectively replacing it
   *
   * @param from the source material
   * @param to   the new, target material
   */
  void applyTypeTranslation(Material from, Material to);

  /**
   * Lookup a type translation
   *
   * @param source the material source
   * @return the translated {@link Material}
   */
  Material typeTranslationOf(Material source);

  String actionDisplayOf(DisplayType type);

  @Deprecated
  String currentActionDisplay();

  @Deprecated
  void setCurrentActionDisplay(String currentActionDisplay);

  @Deprecated
  String overrideActionDisplay();

  @Deprecated
  void setOverrideActionDisplay(String overrideActionDisplay);

  void pushActionDisplayToSubscribers(DisplayType type, String message);

  UUID actionTarget();

  void setActionTarget(UUID target);

  boolean anyActionSubscriptions();

  void addActionReceiver(UUID subscriber, DisplayType type);

  void removeActionSubscription(UUID id);

  /**
   * Note a hard transaction response
   */
  void noteFeedbackFault();

  /**
   * Disconnect a player
   *
   * @param reason the reason
   */
  void kick(String reason);

  /**
   * Send a localized message to the player
   *
   * @param key  the key
   * @param args the arguments
   */
  void message(String key, Object... args);

  /**
   * Unregister a user
   */
  void unregister();

  /**
   * Resets a players sprint variant synchronously
   */
  void refreshSprintState(Consumer<Void> callback);

  default void refreshSprintState() {
    refreshSprintState(null);
  }
}
