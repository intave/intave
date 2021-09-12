package de.jpx3.intave.player.fake;

public final class FakePlayerAttribute {
  public static int INVISIBLE = 1;
  public static int IN_TABLIST = 2;
  public static int ARMORED = 2;
  public static int ITEM_IN_HAND = 2;

  public static boolean hasAttribute(int optionInt, int option) {
    return (optionInt & option) != 0;
  }
}
