/*
 * LoongSim Java 11 compatibility layer.
 * Based on Logisim-evolution and released under GNU GPLv3.
 */

package com.cburch.logisim.gui.start;

/** Optional Java 9 macOS desktop hooks are disabled in the Java 11 build. */
class MacOsAdapter {
  public static void addListeners() {
    // Linux and Windows require no action here.
  }
}
