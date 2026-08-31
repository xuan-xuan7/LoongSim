package com.cburch.logisim.gui.main;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.fpga.loongsim.LoongSimFpgaFlow;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.JFileChoosers;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/** Java 11 compatible LoongSim FPGA workflow window. */
final class FpgaWorkbench extends JFrame {
  private static final long serialVersionUID = 1L;
  private static final Color NAVY = new Color(17, 24, 39);
  private static final Color MUTED = new Color(100, 116, 139);
  private static final Color BORDER = new Color(226, 232, 240);
  private static final Color SURFACE = new Color(248, 250, 252);
  private static final Color RED = new Color(230, 0, 28);
  private final Window owner;
  private final JTextArea output = new JTextArea();
  private final JLabel verilogStatus = new JLabel("未生成");
  private final JLabel yosysStatus = new JLabel("等待 Verilog 工程");
  private final JButton yosysButton = new JButton("运行 Yosys");
  private java.nio.file.Path lastProjectDirectory;
  private String lastTopModule;

  FpgaWorkbench(Window owner) {
    super("LoongSim · FPGA 实现");
    this.owner = owner;
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(AppPreferences.getScaled(900), AppPreferences.getScaled(620)));
    setSize(AppPreferences.getScaled(1100), AppPreferences.getScaled(720));
    setContentPane(buildContent());
  }

  void showWorkbench() {
    if (!isVisible()) setLocationRelativeTo(owner);
    setVisible(true);
    toFront();
  }

  private JPanel buildContent() {
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(SURFACE);
    root.add(buildHeader(), BorderLayout.NORTH);
    root.add(buildSteps(), BorderLayout.WEST);
    root.add(buildWorkspace(), BorderLayout.CENTER);
    return root;
  }

  private JPanel buildHeader() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(Color.WHITE);
    panel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
        BorderFactory.createEmptyBorder(16, 22, 16, 22)));
    JLabel title = new JLabel("FPGA 实现工作台");
    title.setForeground(NAVY);
    title.setFont(title.getFont().deriveFont(Font.BOLD, AppPreferences.getScaled(22f)));
    panel.add(title, BorderLayout.WEST);
    JLabel target = new JLabel("目标器件  XC7A200TFBG676-1");
    target.setOpaque(true);
    target.setForeground(new Color(117, 0, 16));
    target.setBackground(new Color(255, 235, 238));
    target.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    panel.add(target, BorderLayout.EAST);
    return panel;
  }

  private JPanel buildSteps() {
    JPanel panel = new JPanel();
    panel.setBackground(NAVY);
    panel.setPreferredSize(new Dimension(AppPreferences.getScaled(220), 0));
    panel.setBorder(BorderFactory.createEmptyBorder(22, 18, 22, 18));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    JLabel caption = new JLabel("从电路到 FPGA");
    caption.setForeground(Color.WHITE);
    caption.setFont(caption.getFont().deriveFont(Font.BOLD, AppPreferences.getScaled(14f)));
    panel.add(caption);
    panel.add(Box.createVerticalStrut(20));
    String[] steps = {"01  生成 Verilog 工程", "02  XDC 引脚约束", "03  Yosys 综合",
        "04  nextpnr 布局布线", "05  生成位流", "06  烧录设备"};
    for (String step : steps) {
      JLabel label = new JLabel(step);
      label.setForeground(new Color(203, 213, 225));
      label.setBorder(BorderFactory.createEmptyBorder(10, 4, 10, 4));
      panel.add(label);
    }
    panel.add(Box.createVerticalGlue());
    JLabel hint = new JLabel("<html>统一调用开源 FPGA 工具链</html>");
    hint.setForeground(new Color(148, 163, 184));
    panel.add(hint);
    return panel;
  }

  private JPanel buildWorkspace() {
    JPanel panel = new JPanel(new BorderLayout(0, 18));
    panel.setBackground(SURFACE);
    panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
    JPanel cards = new JPanel(new GridLayout(1, 3, 12, 0));
    cards.setOpaque(false);
    cards.add(card("Verilog 工程", verilogStatus, "Logisim 电路 HDL 输出"));
    cards.add(card("目标开发板", "A7 FBG676", "xc7a200tfbgb676-1"));
    cards.add(card("Yosys", yosysStatus, "项目内置 LoongArch 运行时（读取 Verilog）"));
    panel.add(cards, BorderLayout.NORTH);

    output.setEditable(false);
    output.setLineWrap(false);
    output.setBackground(new Color(15, 23, 42));
    output.setForeground(new Color(203, 213, 225));
    output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, AppPreferences.getScaled(12)));
    output.setText("[LoongSim] FPGA 工作流已就绪\n"
        + "[阶段 1] 生成 Verilog 工程\n"
        + "[阶段 2] 根据板卡模板生成当前电路专属 XDC\n"
        + "[阶段 3] 调用 fpga/yosys-runtime 中的固定 Yosys\n"
        + "[工具链] Yosys 0.8 + Berkeley ABC 已就绪\n");
    output.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
    panel.add(new JScrollPane(output), BorderLayout.CENTER);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.setOpaque(false);
    JButton configure = new JButton("查看 XDC 说明");
    configure.addActionListener(event -> appendLog(
        "每个电路会生成 board-template.xdc（完整板卡模板）和 constraints.xdc（当前电路专属约束）。"));
    JButton check = new JButton("生成 Verilog 工程");
    check.setForeground(Color.WHITE);
    check.setBackground(RED);
    check.addActionListener(event -> generateVerilog());
    yosysButton.setEnabled(false);
    yosysButton.addActionListener(event -> runYosys());
    buttons.add(configure);
    buttons.add(check);
    buttons.add(yosysButton);
    panel.add(buttons, BorderLayout.SOUTH);
    return panel;
  }

  private JPanel card(String caption, String value, String note) {
    return card(caption, new JLabel(value), note);
  }

  private JPanel card(String caption, JLabel value, String note) {
    JPanel panel = new JPanel();
    panel.setBackground(Color.WHITE);
    panel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER),
        BorderFactory.createEmptyBorder(14, 16, 14, 16)));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    JLabel label = new JLabel(caption);
    label.setForeground(MUTED);
    value.setForeground(NAVY);
    value.setFont(value.getFont().deriveFont(Font.BOLD, AppPreferences.getScaled(17f)));
    JLabel detail = new JLabel(note);
    detail.setForeground(MUTED);
    panel.add(label);
    panel.add(Box.createVerticalStrut(7));
    panel.add(value);
    panel.add(Box.createVerticalStrut(5));
    panel.add(detail);
    return panel;
  }

  private void generateVerilog() {
    JFileChooser chooser = JFileChoosers.createAt(
        new java.io.File(AppPreferences.FPGA_Workspace.get()));
    chooser.setDialogTitle("选择 Verilog 工程保存目录");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setApproveButtonText("选择此目录");
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION
        || chooser.getSelectedFile() == null) {
      appendLog("已取消生成，未选择保存目录。");
      return;
    }
    final java.nio.file.Path outputDirectory = chooser.getSelectedFile().toPath();
    AppPreferences.FPGA_Workspace.set(outputDirectory.toString());
    setBusy(true);
    appendLog("开始生成 Verilog 工程: " + outputDirectory);
    new SwingWorker<LoongSimFpgaFlow.ExportResult, Void>() {
      @Override
      protected LoongSimFpgaFlow.ExportResult doInBackground() throws Exception {
        com.cburch.logisim.gui.main.Frame frame =
            owner instanceof com.cburch.logisim.gui.main.Frame
                ? (com.cburch.logisim.gui.main.Frame) owner : null;
        Project project = frame == null ? null : frame.getProject();
        Circuit circuit = project == null ? null : project.getCurrentCircuit();
        return new LoongSimFpgaFlow().generateVerilog(project, circuit, outputDirectory,
            FpgaWorkbench.this::appendLog);
      }

      @Override
      protected void done() {
        try {
          LoongSimFpgaFlow.ExportResult result = get();
          lastProjectDirectory = result.getProjectDirectory();
          lastTopModule = result.getTopModule();
          verilogStatus.setText("已生成");
          appendLog("顶层模块: " + lastTopModule);
          startPortMapping(result);
        } catch (Exception exception) {
          verilogStatus.setText("失败");
          appendLog("生成失败: " + rootMessage(exception));
        } finally {
          setBusy(false);
        }
      }
    }.execute();
  }

  private void startPortMapping(LoongSimFpgaFlow.ExportResult result) {
    if (result.getPorts().isEmpty()) {
      yosysStatus.setText("缺少顶层端口");
      yosysButton.setEnabled(false);
      appendLog("未找到可映射的顶层端口，无法生成 constraints.xdc。");
      return;
    }
    java.util.Map<String, String> mappings = FpgaMappingDialog.show(owner, result.getPorts());
    if (mappings == null) {
      yosysStatus.setText("等待端口映射");
      yosysButton.setEnabled(false);
      appendLog("已取消端口映射，Yosys 暂不可用。");
      return;
    }
    setBusy(true);
    yosysStatus.setText("生成 XDC");
    appendLog("端口映射已确认，正在生成 constraints.xdc...");
    new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() throws Exception {
        new LoongSimFpgaFlow().completeMapping(result.getProjectDirectory(), result.getTopModule(),
            result.getPorts(), mappings, FpgaWorkbench.this::appendLog);
        return null;
      }

      @Override
      protected void done() {
        try {
          get();
          yosysStatus.setText("可以综合");
          yosysButton.setEnabled(true);
        } catch (Exception exception) {
          yosysStatus.setText("映射失败");
          yosysButton.setEnabled(false);
          appendLog("XDC 生成失败: " + rootMessage(exception));
        } finally {
          setBusy(false);
        }
      }
    }.execute();
  }

  private void runYosys() {
    if (lastProjectDirectory == null || lastTopModule == null) return;
    setBusy(true);
    yosysStatus.setText("综合中");
    appendLog("开始 Yosys 综合...");
    final java.nio.file.Path projectDirectory = lastProjectDirectory;
    final String topModule = lastTopModule;
    new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() throws Exception {
        new LoongSimFpgaFlow().runYosys(projectDirectory, topModule, FpgaWorkbench.this::appendLog);
        return null;
      }

      @Override
      protected void done() {
        try {
          get();
          yosysStatus.setText("已完成");
        } catch (Exception exception) {
          yosysStatus.setText("失败");
          appendLog("Yosys 失败: " + rootMessage(exception));
        } finally {
          setBusy(false);
        }
      }
    }.execute();
  }

  private void setBusy(boolean busy) {
    yosysButton.setEnabled(!busy && lastProjectDirectory != null);
  }

  private void appendLog(String message) {
    SwingUtilities.invokeLater(() -> {
      output.append(message + "\n");
      output.setCaretPosition(output.getDocument().getLength());
    });
  }

  private static String rootMessage(Exception exception) {
    Throwable current = exception;
    while (current.getCause() != null) current = current.getCause();
    return current.getMessage() == null ? current.toString() : current.getMessage();
  }
}
