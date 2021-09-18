package de.jpx3.intave.module.feedback;

public interface FeedbackTracker {
  void sent(FeedbackRequest<?> request);
  void received(FeedbackRequest<?> request);
}
