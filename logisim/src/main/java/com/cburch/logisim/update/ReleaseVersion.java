package com.cburch.logisim.update;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stable LoongSim versions; legacy distribution suffixes and previews are not updates. */
public final class ReleaseVersion implements Comparable<ReleaseVersion> {
  private static final Pattern FORMAT = Pattern.compile("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\+[A-Za-z0-9.-]+)?");
  private final BigInteger[] parts;
  private final String text;

  private ReleaseVersion(Matcher match) {
    parts = new BigInteger[]{new BigInteger(match.group(1)), new BigInteger(match.group(2)), new BigInteger(match.group(3))};
    text = parts[0] + "." + parts[1] + "." + parts[2];
  }

  public static Optional<ReleaseVersion> parse(String text) {
    if (text == null || text.length() > 100) return Optional.empty();
    Matcher match = FORMAT.matcher(text);
    return match.matches() ? Optional.of(new ReleaseVersion(match)) : Optional.empty();
  }

  @Override public int compareTo(ReleaseVersion other) {
    for (int i = 0; i < parts.length; i++) {
      int result = parts[i].compareTo(other.parts[i]);
      if (result != 0) return result;
    }
    return 0;
  }

  @Override public String toString() { return text; }
}
