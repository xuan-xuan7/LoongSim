package com.cburch.logisim.gui.main;

import com.cburch.logisim.fpga.loongsim.FpgaPort;
import com.cburch.logisim.fpga.loongsim.FpgaResource;
import com.cburch.logisim.fpga.loongsim.LoongSimBoardCatalog;
import com.cburch.logisim.fpga.loongsim.LoongSimFpgaFlow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Window;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** Port-to-board mapping dialog. Incompatible board resources remain disabled. */
final class FpgaMappingDialog extends JDialog {
  private static final long serialVersionUID = 1L;
  private final List<FpgaPort> ports;
  private final List<FpgaResource> resources = LoongSimBoardCatalog.resources();
  private final Map<String, String> mappings = new LinkedHashMap<>();
  private final DefaultListModel<FpgaPort> portModel = new DefaultListModel<>();
  private final JList<FpgaPort> portList = new JList<>(portModel);
  private final JPanel resourcePanel = new JPanel(new GridLayout(0, 2, 6, 6));
  private JScrollPane resourceScroll;
  private final JPanel boardImagePanel = new JPanel(null);
  private final JLabel selectionHint = new JLabel("请选择左侧的电路端口");
  private final JLabel status = new JLabel("尚未完成映射");
  private boolean confirmed;

  private FpgaMappingDialog(Window owner, List<FpgaPort> ports) {
    super(owner, "LoongSim · FPGA 端口映射", ModalityType.APPLICATION_MODAL);
    this.ports = new ArrayList<>(ports);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(1080, 700));
    setSize(1180, 760);
    setContentPane(buildContent());
    setLocationRelativeTo(owner);
  }

  static Map<String, String> show(Window owner, List<FpgaPort> ports) {
    FpgaMappingDialog dialog = new FpgaMappingDialog(owner, ports);
    dialog.setVisible(true);
    return dialog.confirmed ? new LinkedHashMap<>(dialog.mappings) : null;
  }

  private JPanel buildContent() {
    JPanel root = new JPanel(new BorderLayout(12, 12));
    root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
    root.add(buildHeader(), BorderLayout.NORTH);
    root.add(buildPortPanel(), BorderLayout.WEST);
    root.add(buildBoardPanel(), BorderLayout.CENTER);
    root.add(buildFooter(), BorderLayout.SOUTH);
    return root;
  }

  private JPanel buildHeader() {
    JPanel panel = new JPanel(new BorderLayout(10, 0));
    JLabel title = new JLabel("把电路端口连接到 FPGA 板卡资源");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
    panel.add(title, BorderLayout.WEST);
    panel.add(new JLabel("A7 · xc7a200tfbgb676-1"), BorderLayout.EAST);
    return panel;
  }

  private JPanel buildPortPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.setPreferredSize(new Dimension(280, 0));
    panel.add(new JLabel("电路顶层端口"), BorderLayout.NORTH);
    for (FpgaPort port : ports) portModel.addElement(port);
    portList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    portList.setCellRenderer((list, value, index, selected, focus) -> {
      JLabel label = new JLabel(value.toString());
      label.setOpaque(true);
      label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
      label.setBackground(selected ? new Color(219, 234, 254) : Color.WHITE);
      label.setForeground(isMapped(value) ? new Color(22, 101, 52) : new Color(15, 23, 42));
      return label;
    });
    portList.addListSelectionListener(event -> {
      if (!event.getValueIsAdjusting()) refreshResources();
    });
    panel.add(new JScrollPane(portList), BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildBoardPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.add(selectionHint, BorderLayout.NORTH);
    JPanel imagePanel = boardImagePanel;
    imagePanel.setPreferredSize(new Dimension(554, 443));
    ImageIcon image = loadBoardImage();
    if (image != null) {
      Image scaled = image.getImage().getScaledInstance(554, 443, Image.SCALE_SMOOTH);
      JLabel imageLabel = new JLabel(new ImageIcon(scaled));
      imageLabel.setBounds(0, 0, 554, 443);
      imagePanel.add(imageLabel, Integer.valueOf(0));
    } else {
      JLabel missing = new JLabel("未找到板卡图片，请检查 fpga/boards/loongson-a7/board-front.png");
      missing.setBounds(10, 10, 520, 30);
      imagePanel.add(missing, Integer.valueOf(0));
    }
    addHotspots();
    resourcePanel.setBorder(BorderFactory.createTitledBorder("可选板卡资源"));
    resourceScroll = new JScrollPane(resourcePanel);
    resourceScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    resourceScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    resourceScroll.setPreferredSize(new Dimension(0, 260));
    resourceScroll.getVerticalScrollBar().setUnitIncrement(18);
    JPanel boardContent = new JPanel(new BorderLayout(0, 8));
    boardContent.add(imagePanel, BorderLayout.NORTH);
    boardContent.add(resourceScroll, BorderLayout.CENTER);
    panel.add(boardContent, BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildFooter() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(status, BorderLayout.WEST);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton cancel = new JButton("取消");
    cancel.addActionListener(event -> dispose());
    JButton confirm = new JButton("确认映射并生成 XDC");
    confirm.setForeground(Color.WHITE);
    confirm.setBackground(new Color(220, 38, 38));
    confirm.addActionListener(event -> confirmMapping());
    buttons.add(cancel);
    buttons.add(confirm);
    panel.add(buttons, BorderLayout.EAST);
    return panel;
  }

  private void refreshResources() {
    resourcePanel.removeAll();
    refreshHotspots();
    FpgaPort port = portList.getSelectedValue();
    if (port == null) {
      selectionHint.setText("请选择左侧的电路端口");
    } else {
      selectionHint.setText("当前端口: " + port + "。图片中的资源仅在兼容时可选择。");
    }
    Set<String> used = new HashSet<>(mappings.values());
    for (FpgaResource resource : resources) {
      JButton button = new JButton("<html>" + resource.getLabel() + "<br><small>"
          + resource.getGroup() + " · " + resource.getDirection() + " · "
          + resource.getWidth() + " bit</small></html>");
      boolean compatible = port != null && resource.accepts(port)
          && (!isConflicting(resource, used) || resource.getId().equals(mappingFor(port)));
      button.setEnabled(compatible);
      if (resource.getId().equals(mappingFor(port))) button.setBackground(new Color(220, 252, 231));
      button.setToolTipText(compatible ? "点击建立映射" : "方向、位宽或资源占用不兼容");
      button.addActionListener(event -> assign(resource));
      resourcePanel.add(button);
    }
    resourcePanel.revalidate();
    resourcePanel.repaint();
    if (resourceScroll != null) {
      javax.swing.SwingUtilities.invokeLater(() ->
          resourceScroll.getVerticalScrollBar().setValue(0));
    }
  }

  private void addHotspots() {
    for (FpgaResource resource : resources) {
      if (resource.getWidth() == 1 && resource.getId().indexOf('[') >= 0) continue;
      JButton hotspot = createHotspot(resource);
      boardImagePanel.add(hotspot, Integer.valueOf(1));
    }
  }

  private JButton createHotspot(FpgaResource resource) {
    // The board photo already contains the authoritative red resource boxes
    // and labels. Keep only transparent click targets on top of it.
    JButton hotspot = new JButton();
    hotspot.putClientProperty("loongsim-resource", resource);
    hotspot.setMargin(new java.awt.Insets(0, 0, 0, 0));
    hotspot.setOpaque(false);
    hotspot.setContentAreaFilled(false);
    hotspot.setBorderPainted(false);
    hotspot.setFocusPainted(false);
    hotspot.setToolTipText(resource.getLabel() + " · " + resource.getDirection()
        + " · " + resource.getWidth() + " bit");
    hotspot.setBounds(resource.getX(), resource.getY(), Math.max(52, resource.getImageWidth()),
        Math.max(20, resource.getImageHeight()));
    hotspot.addActionListener(event -> assign(resource));
    return hotspot;
  }

  private void refreshHotspots() {
    for (java.awt.Component component : boardImagePanel.getComponents()) {
      if (!(component instanceof JButton)) continue;
      JButton button = (JButton) component;
      Object property = button.getClientProperty("loongsim-resource");
      FpgaResource resource = property instanceof FpgaResource
          ? (FpgaResource) property : null;
      if (resource == null) continue;
      FpgaPort port = portList.getSelectedValue();
      boolean compatible = port != null && resource.accepts(port)
          && (!isConflicting(resource, new HashSet<>(mappings.values()))
              || resource.getId().equals(mappingFor(port)));
      button.setEnabled(compatible);
    }
  }

  private void assign(FpgaResource resource) {
    FpgaPort port = portList.getSelectedValue();
    if (port == null || !resource.accepts(port)) return;
    mappings.entrySet().removeIf(entry -> resource.conflictsWith(entry.getValue())
        || entry.getKey().equals(port.getName()));
    mappings.put(port.getName(), resource.getId());
    status.setText("已映射 " + mappings.size() + " / " + ports.size() + " 个端口");
    portList.repaint();
    refreshResources();
  }

  private void confirmMapping() {
    if (mappings.size() != ports.size()) {
      status.setText("仍有端口未映射，不能生成 XDC");
      return;
    }
    confirmed = true;
    dispose();
  }

  private boolean isMapped(FpgaPort port) { return mappings.containsKey(port.getName()); }

  private String mappingFor(FpgaPort port) { return mappings.get(port.getName()); }

  private boolean isConflicting(FpgaResource candidate, Set<String> usedIds) {
    for (String usedId : usedIds) {
      if (candidate.conflictsWith(usedId)) return true;
    }
    return false;
  }

  private ImageIcon loadBoardImage() {
    java.net.URL bundled = getClass().getResource("/resources/loongsim/board-front.png");
    if (bundled != null) return new ImageIcon(bundled);
    for (Path root : roots()) {
      Path candidate = root.resolve(LoongSimBoardCatalog.IMAGE_RESOURCE).normalize();
      if (candidate.toFile().isFile()) return new ImageIcon(candidate.toString());
    }
    return null;
  }

  private List<Path> roots() {
    List<Path> roots = new ArrayList<>();
    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    for (int i = 0; i < 8 && current != null; i++) {
      roots.add(current);
      current = current.getParent();
    }
    return roots;
  }
}
