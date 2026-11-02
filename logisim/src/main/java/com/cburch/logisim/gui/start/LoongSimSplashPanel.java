package com.cburch.logisim.gui.start;

import com.cburch.logisim.Main;
import com.cburch.logisim.util.ApplicationIcon;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Startup branding, separate from the upstream acknowledgements in About. */
public final class LoongSimSplashPanel extends JPanel {
  private static final long serialVersionUID = 1L;

  public LoongSimSplashPanel() {
    super(new BorderLayout(24, 0));
    setBackground(Color.WHITE);
    setBorder(BorderFactory.createEmptyBorder(28, 28, 24, 28));
    setPreferredSize(new Dimension(600, 220));
    Image image = ApplicationIcon.getImage();
    if (image != null) {
      JLabel logo = new JLabel(new ImageIcon(image.getScaledInstance(144, 144, Image.SCALE_SMOOTH)));
      logo.getAccessibleContext().setAccessibleDescription("LoongSim 龙芯图标");
      add(logo, BorderLayout.WEST);
    }
    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    text.add(label("LoongSim", 42, Font.BOLD, new Color(180, 0, 24)));
    text.add(Box.createVerticalStrut(6));
    text.add(label("龙芯数字逻辑实验平台", 18, Font.BOLD, new Color(30, 41, 59)));
    text.add(Box.createVerticalStrut(14));
    text.add(label("画电路，做仿真，在实验箱上运行", 14, Font.PLAIN, new Color(71, 85, 105)));
    text.add(Box.createVerticalGlue());
    text.add(label("LoongSim " + Main.LOONGSIM_VERSION_NAME, 12, Font.PLAIN, new Color(100, 116, 139)));
    add(text, BorderLayout.CENTER);
  }

  private static JLabel label(String text, int size, int style, Color color) {
    JLabel label = new JLabel(text);
    label.setFont(new Font(Font.SANS_SERIF, style, size));
    label.setForeground(color);
    label.setAlignmentX(LEFT_ALIGNMENT);
    return label;
  }
}
