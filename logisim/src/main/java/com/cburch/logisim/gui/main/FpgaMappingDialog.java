package com.cburch.logisim.gui.main;

import com.cburch.logisim.fpga.loongsim.FpgaPort;
import com.cburch.logisim.fpga.loongsim.FpgaResource;
import com.cburch.logisim.fpga.loongsim.FpgaResourcePosition;
import com.cburch.logisim.fpga.loongsim.LoongSimBoardCatalog;
import com.cburch.logisim.fpga.loongsim.LoongSimFpgaFlow;
import com.cburch.logisim.util.ApplicationIcon;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import javax.swing.JComboBox;
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
  private final List<FpgaResource> resources = LoongSimBoardCatalog.mappingResources();
  private final List<FpgaPort> mappingPorts = new ArrayList<>();
  private LoongSimBoardCatalog.Area selectedArea;
  private final JButton backButton = new JButton("返回类别");
  private final JLabel resourcePath = new JLabel("选择资源类别");
  private final JLabel resourceHelp = new JLabel();
  private final JLabel locationHint = new JLabel();
  private final JPanel displayOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
  private final JComboBox<String> displayPosition = new JComboBox<>();
  private final JComboBox<String> displayMode = new JComboBox<>(new String[] {
      "七段段选（a～g / 7位）", "16进制数值（4位）"});
  private final JPanel segmentGuide = new SegmentGuide();
  private final Map<String, String> mappings = new LinkedHashMap<>();
  private final Set<String> splitPorts = new HashSet<>();
  private final JButton splitButton = new JButton("拆分映射");
  private boolean rebuildingPorts;
  private final DefaultListModel<FpgaPort> portModel = new DefaultListModel<>();
  private final JList<FpgaPort> portList = new JList<>(portModel);
  private final JPanel resourcePanel = new JPanel(new GridLayout(0, 4, 6, 6));
  private JScrollPane resourceScroll;
  private FpgaBoardView boardView;
  private final JLabel selectionHint = new JLabel("请选择左侧的电路端口");
  private final JLabel status = new JLabel("尚未完成映射");
  private boolean confirmed;

  private FpgaMappingDialog(Window owner, List<FpgaPort> ports) {
    super(owner, "LoongSim · FPGA 端口映射", ModalityType.APPLICATION_MODAL);
    ApplicationIcon.attach(this);
    this.ports = new ArrayList<>(ports);
    for (FpgaPort port : ports) mappingPorts.addAll(port.componentParts());
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(1080, 700));
    setSize(1180, 760);
    setContentPane(buildContent());
    if (!ports.isEmpty()) portList.setSelectedIndex(0);
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
    JLabel title = new JLabel("把电路端口映射到FPGA板卡资源");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
    panel.add(title, BorderLayout.WEST);
    panel.add(new JLabel("A7 · " + LoongSimBoardCatalog.DEVICE), BorderLayout.EAST);
    return panel;
  }

  private JPanel buildPortPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.setPreferredSize(new Dimension(280, 0));
    JPanel heading = new JPanel(new BorderLayout(0, 6));
    heading.add(new JLabel("电路顶层端口"), BorderLayout.NORTH);
    splitButton.setEnabled(false);
    splitButton.addActionListener(event -> toggleSplit());
    heading.add(splitButton, BorderLayout.SOUTH);
    panel.add(heading, BorderLayout.NORTH);
    for (FpgaPort port : mappingPorts) portModel.addElement(port);
    portList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    portList.setCellRenderer((list, value, index, selected, focus) -> {
      String mappedLabel = "未映射";
      for (FpgaResource resource : resources) {
        if (resource.getId().equals(mappingFor(value))) mappedLabel = resource.getLabel();
      }
      JLabel label = new JLabel("<html><b>" + html(value.getDisplayName())
          + "</b><br><small>" + value.getDirectionLabel() + " · " + value.getWidth()
          + " 位<br>→ " + html(mappedLabel) + "</small></html>");
      if (!value.getBitLabels().isEmpty()) {
        StringBuilder detail = new StringBuilder("<html>");
        value.getBitLabels().forEach((bit, name) -> detail.append("位 ").append(bit)
            .append("：").append(html(name)).append("<br>"));
        label.setToolTipText(detail.append("</html>").toString());
      }
      label.setOpaque(true);
      label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
      label.setBackground(selected ? new Color(219, 234, 254) : Color.WHITE);
      label.setForeground(isMapped(value) ? new Color(22, 101, 52) : new Color(15, 23, 42));
      return label;
    });
    portList.addListSelectionListener(event -> {
      if (!event.getValueIsAdjusting() && !rebuildingPorts) {
        selectedArea = null;
        FpgaPort selected = portList.getSelectedValue();
        displayMode.setSelectedIndex(selected != null && selected.getWidth() == 4 ? 1 : 0);
        String mapped = mappingFor(selected);
        if (LoongSimBoardCatalog.isDisplay(mapped)) {
          displayPosition.setSelectedIndex(LoongSimBoardCatalog.displayPosition(mapped));
        }
        refreshResources();
      }
    });
    panel.add(new JScrollPane(portList), BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildBoardPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.add(selectionHint, BorderLayout.NORTH);
    ImageIcon image = loadBoardImage();
    boardView = new FpgaBoardView(image == null ? null : image.getImage());
    resourceScroll = new JScrollPane(resourcePanel);
    resourceScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    resourceScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    resourceScroll.setPreferredSize(new Dimension(0, 260));
    resourceScroll.getVerticalScrollBar().setUnitIncrement(18);
    JPanel resourceChooser = new JPanel(new BorderLayout(0, 6));
    resourceChooser.setBorder(BorderFactory.createTitledBorder("可选板卡资源（编号对应上图）"));
    JPanel navigation = new JPanel(new BorderLayout(8, 4));
    JPanel path = new JPanel(new BorderLayout(8, 4));
    backButton.addActionListener(event -> { selectedArea = null; refreshResources(); });
    path.add(backButton, BorderLayout.WEST);
    path.add(resourcePath, BorderLayout.CENTER);
    navigation.add(path, BorderLayout.NORTH);
    for (int position = 0; position < 8; position++) displayPosition.addItem("数码管 " + position + " · 左起" + (position + 1));
    displayOptions.add(new JLabel("显示位置："));
    displayOptions.add(displayPosition);
    displayOptions.add(new JLabel("接收信号："));
    displayOptions.add(displayMode);
    displayPosition.addActionListener(event -> { if (selectedArea != null) refreshResources(); });
    displayMode.addActionListener(event -> { if (selectedArea != null) refreshResources(); });
    navigation.add(displayOptions, BorderLayout.CENTER);
    JPanel hints = new JPanel(new GridLayout(0, 1, 0, 2));
    hints.add(resourceHelp);
    locationHint.setForeground(new Color(185, 28, 28));
    locationHint.setFont(locationHint.getFont().deriveFont(Font.BOLD));
    hints.add(locationHint);
    navigation.add(hints, BorderLayout.SOUTH);
    resourceChooser.add(navigation, BorderLayout.NORTH);
    resourceChooser.add(resourceScroll, BorderLayout.CENTER);
    resourceChooser.add(segmentGuide, BorderLayout.EAST);
    JPanel boardContent = new JPanel(new BorderLayout(0, 8));
    boardContent.add(boardView, BorderLayout.NORTH);
    boardContent.add(resourceChooser, BorderLayout.CENTER);
    panel.add(boardContent, BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildFooter() {
    JPanel panel = new JPanel(new BorderLayout());
    status.setFont(status.getFont().deriveFont(Font.BOLD));
    updateStatus();
    panel.add(status, BorderLayout.WEST);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton cancel = new JButton("取消");
    cancel.addActionListener(event -> dispose());
    JButton confirm = new JButton("完成端口映射");
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
    highlightMapping();
    FpgaPort port = portList.getSelectedValue();
    FpgaPort parent = parentPort(port);
    splitButton.setEnabled(parent != null && parent.getWidth() > 1
        && (!parent.hasNamedSegments() || parent.getWidth() == 7));
    boolean split = parent != null && splitPorts.contains(parent.getName());
    splitButton.setText(split ? "清除拆分，恢复整组" : "拆分映射（逐位分配）");
    splitButton.setToolTipText(split ? "恢复整组映射；无法合并的逐位分配将被清除"
        : "把多位端口展开，每一位分别选择兼容的板卡资源");
    if (port == null) {
      selectionHint.setText("请选择左侧的电路端口");
    } else {
      boolean needsSplit = port.getWidth() > 1 && resources.stream().noneMatch(resource -> resource.accepts(port));
      selectionHint.setText("当前端口: " + port + (needsSplit
          ? "。可先拆分映射，再分别选择资源。" : "。请在下方选择资源。"));
    }
    Set<String> used = new HashSet<>(mappings.values());
    backButton.setVisible(selectedArea != null);
    resourcePath.setText(selectedArea == null ? "第一步：选择资源类别" : "第二步：选择" + categoryTitle(selectedArea));
    boolean display = selectedArea == LoongSimBoardCatalog.Area.DISPLAY;
    displayOptions.setVisible(display);
    segmentGuide.setVisible(display && displayMode.getSelectedIndex() == 0);
    resourceHelp.setText(display
        ? (displayMode.getSelectedIndex() == 0
            ? "逐段连接 a～g，或连接7位段选；未连接的段不亮。位选由 LoongSim 完成。"
            : "4位数值译码为 0～F；位选由 LoongSim 完成。")
        : selectedArea == LoongSimBoardCatalog.Area.KEYPAD
            ? "按实物从上到下、从左到右选择按键。按下为1，松开为0；扫描和防抖由 LoongSim 完成。"
            : "灰色资源表示方向、位宽或资源占用不兼容。");
    if (selectedArea == null) {
      for (LoongSimBoardCatalog.Area area : LoongSimBoardCatalog.Area.values()) {
        long count = resources.stream().filter(resource -> LoongSimBoardCatalog.areaFor(resource.getId()) == area)
            .filter(resource -> port != null && resource.accepts(port)
                && (!isConflicting(resource, used) || resource.getId().equals(mappingFor(port))))
            .map(resource -> LoongSimBoardCatalog.isDisplay(resource.getId())
                ? "digit[" + LoongSimBoardCatalog.displayPosition(resource.getId()) + "]" : resource.getId())
            .distinct().count();
        JButton category = new JButton("<html><b>" + area.getNumber() + " · " + categoryTitle(area)
            + "</b><br><small>可选 " + count + " 项</small></html>");
        category.addActionListener(event -> {
          selectedArea = area;
          boardView.highlight(area);
          refreshResources();
        });
        resourcePanel.add(category);
      }
    } else {
      for (FpgaResource resource : resources) {
      if (LoongSimBoardCatalog.areaFor(resource.getId()) != selectedArea) continue;
      if (display && (LoongSimBoardCatalog.displayPosition(resource.getId()) != displayPosition.getSelectedIndex()
          || LoongSimBoardCatalog.isHexDisplay(resource.getId()) != (displayMode.getSelectedIndex() == 1))) continue;
      String number = LoongSimBoardCatalog.areaFor(resource.getId()).getNumber();
      String title = resource.getLabel();
      String detail = (resource.getDirection() == FpgaResource.Direction.INPUT ? "输入" : "输出")
          + " · " + resource.getWidth() + " 位";
      if (display) {
        int segment = LoongSimBoardCatalog.displaySegment(resource.getId());
        title = segment >= 0 ? "段 " + (char) ('a' + segment)
            : LoongSimBoardCatalog.isHexDisplay(resource.getId()) ? "连接4位数值" : "连接7位段选";
        detail = segment >= 0 ? "1 = 亮，0 = 灭"
            : LoongSimBoardCatalog.isHexDisplay(resource.getId()) ? "显示 0～F" : "a～g 的亮灭信号";
      } else {
        FpgaResourcePosition position = FpgaResourcePosition.forResource(resource.getId());
        if (position != null) detail = position.getBrief();
      }
      JButton button = new JButton("<html><b>" + number + "</b> · "
          + title + "<br><small>" + detail + "</small></html>");
      if (LoongSimBoardCatalog.isKeypadKey(resource.getId())) {
        int key = LoongSimBoardCatalog.keypadIndex(resource.getId());
        button.setText("第" + (key / 4 + 1) + "行 · 第" + (key % 4 + 1) + "列");
      }
      boolean compatible = port != null && resource.accepts(port)
          && (!isConflicting(resource, used) || resource.getId().equals(mappingFor(port)));
      button.setEnabled(compatible);
      if (resource.getId().equals(mappingFor(port))) button.setBackground(new Color(220, 252, 231));
      FpgaResourcePosition position = FpgaResourcePosition.forResource(resource.getId());
      String positionText = position == null ? "" : position.getDescription() + "。";
      button.setToolTipText(positionText + (compatible ? "点击建立映射" : "方向、位宽或资源占用不兼容"));
      button.addActionListener(event -> assign(resource));
      button.addMouseListener(new MouseAdapter() {
        @Override public void mouseEntered(MouseEvent event) {
          showResourceLocation(resource.getId());
        }
        @Override public void mouseExited(MouseEvent event) {
          highlightMapping();
        }
      });
      button.addFocusListener(new java.awt.event.FocusAdapter() {
        @Override public void focusGained(java.awt.event.FocusEvent event) { showResourceLocation(resource.getId()); }
        @Override public void focusLost(java.awt.event.FocusEvent event) { highlightMapping(); }
      });
      resourcePanel.add(button);
      }
    }
    resourcePanel.revalidate();
    resourcePanel.repaint();
    if (resourceScroll != null) {
      javax.swing.SwingUtilities.invokeLater(() ->
          resourceScroll.getVerticalScrollBar().setValue(0));
    }
  }

  private void highlightMapping() {
    String mapped = mappingFor(portList.getSelectedValue());
    if (selectedArea == LoongSimBoardCatalog.Area.DISPLAY) {
      showResourceLocation("digit[" + displayPosition.getSelectedIndex() + "]");
    } else if (mapped != null && (selectedArea == null || selectedArea == LoongSimBoardCatalog.areaFor(mapped))) {
      showResourceLocation(mapped);
    } else {
      boardView.highlight(selectedArea);
      locationHint.setText("位置参考：按上图朝向摆放板卡，移到具体资源上可查看位置。");
    }
  }

  private void showResourceLocation(String id) {
    boardView.highlightResource(id);
    FpgaResourcePosition position = FpgaResourcePosition.forResource(id);
    locationHint.setText(position == null
        ? "位置参考：按上图朝向摆放板卡，图中突出显示对应的资源区域。"
        : "位置：" + position.getLabel() + " — " + position.getDescription() + "（以上图朝向为准）");
  }

  private void updateStatus() {
    int total = portModel.size();
    int remaining = total - mappings.size();
    status.setForeground(remaining == 0 ? new Color(22, 101, 52) : new Color(185, 28, 28));
    status.setText(remaining == 0 ? "已完成全部 " + total + " 项映射"
        : "仍有 " + remaining + " 项未映射（已映射 " + mappings.size()
            + " / " + total + "）");
  }

  private void assign(FpgaResource resource) {
    FpgaPort port = portList.getSelectedValue();
    if (port == null || !resource.accepts(port)) return;
    for (Map.Entry<String, String> entry : mappings.entrySet()) {
      if (!entry.getKey().equals(port.getName()) && resource.conflictsWith(entry.getValue())) return;
    }
    mappings.put(port.getName(), resource.getId());
    updateStatus();
    portList.repaint();
    refreshResources();
  }

  private void confirmMapping() {
    if (mappings.size() != portModel.size()) {
      status.setForeground(new Color(185, 28, 28));
      status.setText("仍有 " + (portModel.size() - mappings.size()) + " 项未映射，不能生成 XDC");
      return;
    }
    confirmed = true;
    dispose();
  }

  private boolean isMapped(FpgaPort port) { return mappings.containsKey(port.getName()); }

  private FpgaPort parentPort(FpgaPort selected) {
    if (selected == null) return null;
    for (FpgaPort port : mappingPorts) {
      if (port.contains(selected)) return port;
    }
    return null;
  }

  private void toggleSplit() {
    FpgaPort parent = parentPort(portList.getSelectedValue());
    if (parent == null || parent.getWidth() <= 1) return;
    boolean splitting = !splitPorts.contains(parent.getName());
    Map<String, String> replacement = new LinkedHashMap<>();
    if (splitting) {
      String whole = mappings.get(parent.getName());
      if (whole != null) {
        for (int bit = 0; bit < parent.getWidth(); bit++) {
          String resourceId = whole + "[" + bit + "]";
          if (LoongSimBoardCatalog.isDisplay(whole) && !LoongSimBoardCatalog.isHexDisplay(whole)) {
            resourceId = whole + "." + (char) ('a' + LoongSimBoardCatalog.segmentForBit(parent, bit));
          }
          final String candidateId = resourceId;
          if (resources.stream().anyMatch(resource -> resource.getId().equals(candidateId))) {
            replacement.put(parent.bit(bit).getName(), resourceId);
          }
        }
      }
      splitPorts.add(parent.getName());
    } else {
      String first = mappings.get(parent.bit(0).getName());
      String whole = first == null ? "" : LoongSimBoardCatalog.isDisplay(first)
          ? first.substring(0, first.indexOf(']') + 1) : first.replaceFirst("\\[.*$", "");
      boolean merge = resources.stream().anyMatch(resource -> resource.getId().equals(whole)
          && resource.accepts(parent));
      for (int bit = 0; bit < parent.getWidth(); bit++) {
        String expected = LoongSimBoardCatalog.isDisplay(whole)
            ? whole + "." + (char) ('a' + LoongSimBoardCatalog.segmentForBit(parent, bit))
            : whole + "[" + bit + "]";
        merge &= expected.equals(mappings.get(parent.bit(bit).getName()));
      }
      if (merge) replacement.put(parent.getName(), whole);
      splitPorts.remove(parent.getName());
    }
    mappings.keySet().removeIf(parent::containsReference);
    mappings.putAll(replacement);
    rebuildingPorts = true;
    int selectedIndex = 0;
    try {
      portModel.clear();
      for (FpgaPort port : mappingPorts) {
        if (port == parent) selectedIndex = portModel.size();
        if (splitPorts.contains(port.getName())) {
          for (int bit = 0; bit < port.getWidth(); bit++) portModel.addElement(port.bit(bit));
        } else portModel.addElement(port);
      }
      portList.setSelectedIndex(selectedIndex);
    } finally { rebuildingPorts = false; }
    updateStatus();
    refreshResources();
  }

  private static String html(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /** Segment letters use the conventional positions, independent of HDL bus bit order. */
  private static final class SegmentGuide extends JPanel {
    private static final long serialVersionUID = 1L;
    SegmentGuide() { setPreferredSize(new Dimension(115, 100)); }
    @Override protected void paintComponent(java.awt.Graphics graphics) {
      super.paintComponent(graphics);
      java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
      try {
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int x = 32, y = Math.max(16, (getHeight() - 90) / 2), w = 45, h = 34;
        g.setColor(new Color(100, 116, 139));
        g.setStroke(new java.awt.BasicStroke(5, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        for (int row = 0; row < 3; row++) g.drawLine(x + 5, y + row * h, x + w - 5, y + row * h);
        for (int column = 0; column < 2; column++) {
          for (int row = 0; row < 2; row++) g.drawLine(x + column * w, y + row * h + 5, x + column * w, y + (row + 1) * h - 5);
        }
        g.setColor(new Color(15, 23, 42));
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g.drawString("a", x + 18, y - 5);
        g.drawString("b", x + w + 9, y + 20);
        g.drawString("c", x + w + 9, y + h + 20);
        g.drawString("d", x + 18, y + 2 * h + 18);
        g.drawString("e", x - 18, y + h + 20);
        g.drawString("f", x - 18, y + 20);
        g.drawString("g", x + 18, y + h - 5);
      } finally { g.dispose(); }
    }
  }

  private static String categoryTitle(LoongSimBoardCatalog.Area area) {
    switch (area) {
      case CLOCK: return "系统时钟";
      case RESET: return "复位键";
      case SWITCH: return "拨码开关";
      case STEP: return "按钮";
      case LED: return "单色 LED";
      case COLOR_LED: return "双色 LED";
      case DISPLAY: return "数码管";
      case KEYPAD: return "矩阵键盘";
      default: return area.getLabel();
    }
  }

  private String mappingFor(FpgaPort port) {
    return port == null ? null : mappings.get(port.getName());
  }

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
