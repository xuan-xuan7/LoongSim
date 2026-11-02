package com.cburch.logisim.util;

import java.awt.AWTEvent;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

/** Shared LoongSim identity for windows and the macOS Dock, including JAR launches. */
public final class ApplicationIcon {
  private static final String PATH = "resources/logisim/img/";
  private static final List<Image> IMAGES = loadImages();
  private static boolean installed;

  private ApplicationIcon() {}

  private static List<Image> loadImages() {
    List<Image> images = new ArrayList<>();
    for (int size : new int[]{16, 20, 24, 32, 48, 64, 128, 256}) {
      load(images, "logisim-icon-" + size + ".png");
    }
    load(images, "LoongSim.png");
    return Collections.unmodifiableList(images);
  }

  private static void load(List<Image> images, String name) {
    URL resource = ApplicationIcon.class.getClassLoader().getResource(PATH + name);
    if (resource == null) return;
    try {
      Image image = ImageIO.read(resource);
      if (image != null) images.add(image);
    } catch (IOException ignored) {
      // Other resolutions remain available if a resource cannot be read.
    }
  }

  public static Image getImage() {
    return IMAGES.isEmpty() ? null : IMAGES.get(IMAGES.size() - 1);
  }

  public static void attach(Window window) {
    if (!IMAGES.isEmpty()) window.setIconImages(IMAGES);
  }

  public static synchronized void install() {
    if (installed || GraphicsEnvironment.isHeadless()) return;
    installed = true;
    Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
      if (event instanceof WindowEvent && event.getID() == WindowEvent.WINDOW_OPENED) {
        attach(((WindowEvent) event).getWindow());
      }
    }, AWTEvent.WINDOW_EVENT_MASK);
    Image image = getImage();
    if (image == null) return;
    try {
      if (Taskbar.isTaskbarSupported()) {
        Taskbar taskbar = Taskbar.getTaskbar();
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.setIconImage(image);
      }
    } catch (UnsupportedOperationException | SecurityException ignored) {
      // Platforms without Dock icon support still use the same window icons.
    }
  }
}
