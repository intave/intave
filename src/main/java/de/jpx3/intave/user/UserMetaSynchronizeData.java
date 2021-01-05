package de.jpx3.intave.user;

import com.google.common.collect.Maps;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.event.service.transaction.TransactionCallBackData;

import java.util.List;
import java.util.Map;

public final class UserMetaSynchronizeData {
  private final Map<Short, TransactionCallBackData<?>> transactionFeedBackMap = Maps.newConcurrentMap();
  private final Map<Integer, WrappedEntity> synchronizedEntityMap = Maps.newConcurrentMap();

  public short transactionCounter = Short.MIN_VALUE;

  public Map<Short, TransactionCallBackData<?>> transactionFeedBackMap() {
    return transactionFeedBackMap;
  }

  public Map<Integer, WrappedEntity> synchronizedEntityMap() {
    return synchronizedEntityMap;
  }
}