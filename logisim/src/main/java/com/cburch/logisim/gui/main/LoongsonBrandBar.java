package com.cburch.logisim.gui.main;

import com.cburch.logisim.Main;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.net.URL;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** LoongSim brand header compatible with Java 11. */
final class LoongsonBrandBar extends JPanel {
  private static final long serialVersionUID = 1L;
  private static final Color SURFACE = new Color(250, 251, 253);
  private static final Color INK = new Color(25, 36, 53);
  private static final Color MUTED = new Color(96, 109, 128);
  private static final Color BORDER = new Color(221, 226, 234);
  private static final Color RED = new Color(218, 30, 42);
  private static final Color RED_SOFT = new Color(255, 237, 238);
  private FpgaWorkbench workbench;

  LoongsonBrandBar(final Frame owner) {
    super(new BorderLayout());
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(9, 20, 8, 20));
    setPreferredSize(new Dimension(0, AppPreferences.getScaled(66)));

    JPanel identity = new JPanel();
    identity.setOpaque(false);
    identity.setLayout(new BoxLayout(identity, BoxLayout.X_AXIS));
    URL logoUrl = LoongsonBrandBar.class.getResource("/resources/logisim/img/loong-edu.png");
    if (logoUrl != null) {
      Image source = new ImageIcon(logoUrl).getImage();
      Image logo = source.getScaledInstance(-1, AppPreferences.getScaled(38), Image.SCALE_SMOOTH);
      identity.add(new JLabel(new ImageIcon(logo)));
      identity.add(Box.createHorizontalStrut(AppPreferences.getScaled(16)));
    } else {
      JLabel mark = new JLabel("LOONGSON");
      mark.setForeground(RED);
      mark.setFont(mark.getFont().deriveFont(Font.BOLD, AppPreferences.getScaled(17f)));
      identity.add(mark);
      identity.add(Box.createHorizontalStrut(AppPreferences.getScaled(16)));
    }

    JPanel titles = new JPanel();
    titles.setOpaque(false);
    titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
    JLabel title = new JLabel("龙芯数字逻辑实验平台");
    title.setForeground(INK);
    title.setFont(title.getFont().deriveFont(Font.BOLD, AppPreferences.getScaled(17f)));
    JLabel subtitle = new JLabel("LOONGSON  /  LOONGSIM");
    subtitle.setForeground(MUTED);
    subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, AppPreferences.getScaled(10f)));
    titles.add(title);
    titles.add(Box.createVerticalStrut(2));
    titles.add(subtitle);
    identity.add(titles);
    add(identity, BorderLayout.WEST);

    JPanel actions = new JPanel();
    actions.setOpaque(false);
    actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
    JLabel version = new JLabel("LoongSim " + Main.LOONGSIM_VERSION_NAME);
    version.setForeground(RED);
    version.setOpaque(true);
    version.setBackground(RED_SOFT);
    version.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
    actions.add(version);
    actions.add(Box.createHorizontalStrut(AppPreferences.getScaled(18)));
    JButton fpgaButton = new JButton("FPGA 实现");
    fpgaButton.setForeground(Color.WHITE);
    fpgaButton.setBackground(RED);
    fpgaButton.setFocusable(false);
    fpgaButton.setBorder(BorderFactory.createEmptyBorder(6, 13, 6, 13));
    fpgaButton.addActionListener(event -> {
      if (workbench == null || !workbench.isDisplayable()) workbench = new FpgaWorkbench(owner);
      workbench.showWorkbench();
    });
    actions.add(fpgaButton);
    add(actions, BorderLayout.EAST);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setColor(SURFACE);
    g.fillRect(0, 0, getWidth(), getHeight());
    g.setColor(BORDER);
    g.fillRect(0, getHeight() - AppPreferences.getScaled(2), getWidth(), AppPreferences.getScaled(1));
    g.setColor(RED);
    g.fillRect(0, getHeight() - AppPreferences.getScaled(1), getWidth(), AppPreferences.getScaled(1));
    g.dispose();
    super.paintComponent(graphics);
  }
}
