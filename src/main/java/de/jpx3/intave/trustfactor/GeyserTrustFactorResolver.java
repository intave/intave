package de.jpx3.intave.trustfactor;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.function.Consumer;

public final class GeyserTrustFactorResolver implements TrustFactorResolver {
	private final TrustFactorResolver parent;

	public GeyserTrustFactorResolver(TrustFactorResolver parent) {
		this.parent = parent;
	}

	@Override
	public void resolve(Player player, Consumer<TrustFactor> callback) {
		if (FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
			callback.accept(TrustFactor.BYPASS);
		} else {
			parent.resolve(player, callback);
		}
	}
}
