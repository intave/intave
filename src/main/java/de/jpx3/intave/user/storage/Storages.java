package de.jpx3.intave.user.storage;

import java.util.UUID;

public final class Storages {
  public static PlayerStorage emptyPlayerStorageFor(UUID id) {
    PlayerStorage storage = new PlayerStorage(id);
    storage.append(PlaytimeStorage.class);
    storage.append(ViolationStorage.class);
    storage.append(HeuristicsStorage.class);
    storage.append(NerferStorage.class);
    storage.append(AccountDataStorage.class);
    storage.append(FeedbackAnalysisStorage.class);
    return storage;
  }
}
