package de.jpx3.intave.tools.placeholder;

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.tools.MathHelper;

import java.util.Map;

public final class ViolationContext extends PlaceholderContext {
  private final String check;
  private final String message;
  private final String details;
  private final double preVL;
  private final double postVL;

  public ViolationContext(
    String check,
    String message,
    String details,
    double preVL, double postVL
  ) {
    this.check = check;
    this.message = message;
    this.details = details;
    this.preVL = preVL;
    this.postVL = postVL;
  }

  @Override
  public Map<String, String> replacements() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    builder.put("cheatdetected", check);
    builder.put("check", check);

    builder.put("message", message);

    String details = this.details;
    if(!details.isEmpty()) {
      details = "(" + details + ")";
    }
    builder.put("details", details);
    builder.put("details-raw", this.details);

    builder.put("vlbefore", MathHelper.formatDouble(preVL, 2));
    builder.put("vl", MathHelper.formatDouble(postVL, 2));
    builder.put("vladded", MathHelper.formatDouble(postVL - preVL, 2));

    return builder.build();
  }
}
