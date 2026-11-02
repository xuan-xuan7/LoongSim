package com.cburch.logisim.gui.main;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.fpga.loongsim.LoongSimFpgaFlow;
import com.cburch.logisim.fpga.loongsim.FpgaImplementation;
import com.cburch.logisim.fpga.loongsim.FpgaToolchain;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.util.ApplicationIcon;
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
  private final JLabel mappingStatus = new JLabel("未映射");
  private final JLabel yosysStatus = new JLabel("等待端口映射");
  private final JLabel implementationStatus = new JLabel("等待端口映射");
  private final JButton mappingButton = new JButton("1. 端口映射");
  private final JButton generateButton = new JButton("2. 生成位流");
  private final JButton detectButton = new JButton("检测设备");
  private final JButton programButton = new JButton("3. 下载到 FPGA");
  private boolean busy;
  private boolean mappingComplete;
  private boolean synthesisComplete;
  private boolean bitstreamComplete;
  private boolean buildingBitstream;
  private volatile boolean designChanged;
  private Project sourceProject;
  private boolean listening;
  private final ProjectListener designListener = event -> {
    int action = event.getAction();
    if (action == ProjectEvent.ACTION_START || action == ProjectEvent.ACTION_MERGE
        || action == ProjectEvent.UNDO_START || action == ProjectEvent.REDO_START
        || action == ProjectEvent.ACTION_SET_CURRENT || action == ProjectEvent.ACTION_SET_FILE) {
      invalidateDesign();
    }
  };
  private java.nio.file.Path lastProjectDirectory;
  private String lastTopModule;

  FpgaWorkbench(Window owner) {
    super("LoongSim · FPGA 实现");
    ApplicationIcon.attach(this);
    this.owner = owner;
    if (owner instanceof com.cburch.logisim.gui.main.Frame) {
      sourceProject = ((com.cburch.logisim.gui.main.Frame) owner).getProject();
    }
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(AppPreferences.getScaled(900), AppPreferences.getScaled(620)));
    setSize(AppPreferences.getScaled(1100), AppPreferences.getScaled(720));
    setContentPane(buildContent());
  }

  void showWorkbench() {
    if (!listening && sourceProject != null) {
      sourceProject.addProjectListener(designListener);
      listening = true;
    }
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
    addStep(panel, "01  端口映射", "为电路的输入和输出端口，<br>指定要映射的实验箱资源");
    addStep(panel, "02  生成位流", "完成综合、布局布线<br>和比特流生成");
    addStep(panel, "03  下载到 FPGA", "将电路下载到实验箱");
    panel.add(Box.createVerticalGlue());
    return panel;
  }

  private void addStep(JPanel panel, String title, String description) {
    JLabel label = new JLabel(title);
    label.setForeground(new Color(203, 213, 225));
    label.setFont(label.getFont().deriveFont(Font.BOLD, AppPreferences.getScaled(14f)));
    label.setBorder(BorderFactory.createEmptyBorder(10, 4, 6, 4));
    label.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(label);
    JLabel detail = new JLabel("<html>" + description + "</html>");
    detail.setForeground(new Color(148, 163, 184));
    detail.setFont(detail.getFont().deriveFont(AppPreferences.getScaled(12f)));
    detail.setBorder(BorderFactory.createEmptyBorder(0, 4, 18, 4));
    detail.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(detail);
  }

  private JPanel buildWorkspace() {
    JPanel panel = new JPanel(new BorderLayout(0, 18));
    panel.setBackground(SURFACE);
    panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
    JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
    cards.setOpaque(false);
    cards.add(card("端口映射", mappingStatus, "连接电路与板卡资源"));
    cards.add(card("目标开发板", "A7 FBG676", "xc7a200tfbg676-1"));
    cards.add(card("综合", yosysStatus, "生成 Xilinx 综合网表"));
    cards.add(card("位流", implementationStatus, "布局布线与位流生成"));
    panel.add(cards, BorderLayout.NORTH);

    output.setEditable(false);
    output.setLineWrap(false);
    output.setBackground(new Color(15, 23, 42));
    output.setForeground(new Color(203, 213, 225));
    output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, AppPreferences.getScaled(12)));
    output.setText("[LoongSim] FPGA 工作流已就绪\n"
        + "[1. 端口映射] 选择工程目录，把电路端口连接到板卡资源。\n"
        + "[2. 生成位流] 自动执行综合、布局布线和位流生成。\n"
        + "[3. 下载到 FPGA] 自动检查下载器和目标芯片，将电路下载到实验箱。\n"
        + "[工具链] " + FpgaToolchain.discover().describe() + "\n");
    output.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
    panel.add(new JScrollPane(output), BorderLayout.CENTER);

    JPanel actions = new JPanel(new BorderLayout());
    actions.setOpaque(false);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.setOpaque(false);
    JButton help = new JButton("流程说明");
    help.addActionListener(event -> javax.swing.JOptionPane.showMessageDialog(this,
        "1. 端口映射：选择工程目录，设置电路端口与板卡资源的对应关系。\n"
            + "2. 生成位流：自动完成综合、布局布线和位流生成，无需逐项操作。\n"
            + "3. 下载到 FPGA：检查目标芯片，并把生成的电路下载到实验箱。\n\n"
            + "端口映射会自动生成引脚约束（XDC），不需要单独编辑。\n"
            + "修改电路后，需要重新映射端口并生成位流。\n"
            + "比特流将被下载到FPGA的临时配置区，实验箱断电后清除。",
        "FPGA 实现流程", javax.swing.JOptionPane.INFORMATION_MESSAGE));
    generateButton.setForeground(Color.WHITE);
    generateButton.setBackground(RED);
    mappingButton.setToolTipText("设置电路端口与板卡资源的对应关系");
    generateButton.setToolTipText("端口配置完成后，自动执行综合、布局布线并生成位流");
    programButton.setToolTipText("位流生成成功后，自动检查设备并下载到 FPGA");
    mappingButton.addActionListener(event -> configurePorts());
    generateButton.addActionListener(event -> generateBitstream());
    detectButton.addActionListener(event -> detectDevice());
    programButton.addActionListener(event -> programDevice());
    buttons.add(mappingButton);
    buttons.add(generateButton);
    buttons.add(programButton);
    JPanel utilities = new JPanel(new FlowLayout(FlowLayout.LEFT));
    utilities.setOpaque(false);
    utilities.add(help);
    utilities.add(detectButton);
    actions.add(utilities, BorderLayout.WEST);
    actions.add(buttons, BorderLayout.CENTER);
    panel.add(actions, BorderLayout.SOUTH);
    setBusy(false);
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

  private void configurePorts() {
    JFileChooser chooser = JFileChoosers.createAt(
        new java.io.File(AppPreferences.FPGA_Workspace.get()));
    chooser.setDialogTitle("选择 FPGA 工程保存目录");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setApproveButtonText("选择此目录");
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION
        || chooser.getSelectedFile() == null) {
      appendLog("已取消端口映射，未选择工程目录。");
      return;
    }
    final java.nio.file.Path outputDirectory = chooser.getSelectedFile().toPath();
    designChanged = false;
    buildingBitstream = false;
    lastProjectDirectory = null;
    lastTopModule = null;
    mappingComplete = false;
    synthesisComplete = false;
    bitstreamComplete = false;
    mappingStatus.setText("导出电路中");
    yosysStatus.setText("等待端口映射");
    implementationStatus.setText("等待端口映射");
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
        LoongSimFpgaFlow.ExportResult completed = null;
        try {
          LoongSimFpgaFlow.ExportResult result = get();
          ensureDesignCurrent();
          lastProjectDirectory = result.getProjectDirectory();
          lastTopModule = result.getTopModule();
          mappingStatus.setText("等待映射");
          appendLog("顶层模块: " + lastTopModule);
          completed = result;
        } catch (Exception exception) {
          buildingBitstream = false;
          mappingStatus.setText("导出失败");
          appendLog("生成失败: " + rootMessage(exception));
        } finally {
          setBusy(false);
        }
        if (completed != null) startPortMapping(completed);
      }
    }.execute();
  }

  private void startPortMapping(LoongSimFpgaFlow.ExportResult result) {
    if (result.getPorts().isEmpty()) {
      buildingBitstream = false;
      mappingStatus.setText("缺少端口");
      yosysStatus.setText("缺少顶层端口");
      appendLog("未找到可映射的顶层端口，无法生成 constraints.xdc。");
      return;
    }
    java.util.Map<String, String> mappings = FpgaMappingDialog.show(owner, result.getPorts());
    if (mappings == null) {
      buildingBitstream = false;
      mappingStatus.setText("未完成映射");
      yosysStatus.setText("等待端口映射");
      appendLog("已取消端口映射。请先完成映射，再生成位流。");
      return;
    }
    setBusy(true);
    mappingStatus.setText("保存映射中");
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
          ensureDesignCurrent();
          mappingStatus.setText("已映射");
          yosysStatus.setText("等待生成位流");
          implementationStatus.setText("等待生成位流");
          mappingComplete = true;
          appendLog("端口映射完成。请点击“2. 生成位流”，软件会自动完成综合和布局布线。");
        } catch (Exception exception) {
          buildingBitstream = false;
          mappingStatus.setText("映射失败");
          yosysStatus.setText("映射失败");
          appendLog("XDC 生成失败: " + rootMessage(exception));
        } finally {
          setBusy(false);
        }
      }
    }.execute();
  }

  private void generateBitstream() {
    if (busy || !mappingComplete || lastProjectDirectory == null) return;
    buildingBitstream = true;
    runYosys();
  }

  private void runYosys() {
    if (busy || !mappingComplete || lastProjectDirectory == null || lastTopModule == null) return;
    synthesisComplete = false;
    bitstreamComplete = false;
    implementationStatus.setText("等待综合");
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
          ensureDesignCurrent();
          yosysStatus.setText("已完成");
          synthesisComplete = true;
        } catch (Exception exception) {
          buildingBitstream = false;
          yosysStatus.setText("失败");
          appendLog("Yosys 失败: " + rootMessage(exception));
        } finally {
          setBusy(false);
        }
        if (buildingBitstream && synthesisComplete) {
          buildingBitstream = false;
          runImplementation();
        }
      }
    }.execute();
  }

  private void setBusy(boolean busy) {
    this.busy = busy;
    mappingButton.setEnabled(!busy);
    generateButton.setEnabled(!busy && mappingComplete && lastProjectDirectory != null);
    detectButton.setEnabled(!busy && FpgaToolchain.isWindows());
    programButton.setEnabled(!busy && bitstreamComplete);
  }

  private interface BackgroundAction { void run() throws Exception; }

  private void executeAction(String caption, BackgroundAction action, Runnable success, Runnable failure) {
    if (busy) return;
    setBusy(true);
    appendLog(caption + "...");
    new SwingWorker<Void, Void>() {
      @Override protected Void doInBackground() throws Exception { action.run(); return null; }
      @Override protected void done() {
        try { get(); success.run(); }
        catch (Exception exception) { failure.run(); appendLog(caption + "失败: " + rootMessage(exception)); }
        finally { setBusy(false); }
      }
    }.execute();
  }

  private void runImplementation() {
    if (busy || !synthesisComplete) return;
    bitstreamComplete = false;
    implementationStatus.setText("生成中");
    final java.nio.file.Path directory = lastProjectDirectory;
    final String top = lastTopModule;
    executeAction("布局布线并生成位流", () -> {
          new FpgaImplementation().buildBitstream(directory, top, this::appendLog);
          ensureDesignCurrent();
        }, () -> {
          bitstreamComplete = true;
          implementationStatus.setText("已生成");
          appendLog("位流生成完成。请点击“3. 下载到 FPGA”；无需再次执行综合或布局布线。");
        }, () -> implementationStatus.setText("失败"));
  }

  private void detectDevice() {
    final java.nio.file.Path directory = lastProjectDirectory;
    executeAction("检测设备", () -> new FpgaImplementation().detect(directory == null
        ? java.nio.file.Files.createTempDirectory("loongsim-jtag-") : directory,
        "auto", this::appendLog), () -> appendLog("设备检测完成，请核对日志中的芯片型号。"), () -> {});
  }

  private void programDevice() {
    if (busy || !bitstreamComplete) return;
    final java.nio.file.Path directory = lastProjectDirectory;
    executeAction("下载到 FPGA", () -> new FpgaImplementation().program(directory, "auto",
        this::appendLog, () -> !designChanged), () -> appendLog("FPGA 已配置，断电后清除。"), () -> {});
  }

  private void ensureDesignCurrent() throws java.io.IOException {
    if (designChanged) throw new java.io.IOException("电路已修改或切换，请重新生成位流");
  }

  private void invalidateDesign() {
    if (lastProjectDirectory == null && !busy) return;
    boolean first = !designChanged;
    designChanged = true;
    buildingBitstream = false;
    mappingComplete = false;
    synthesisComplete = false;
    bitstreamComplete = false;
    mappingStatus.setText("需重新映射");
    yosysStatus.setText("等待端口映射");
    implementationStatus.setText("电路已修改");
    setBusy(busy);
    if (first) appendLog("电路已修改或切换，旧位流已停用，请重新生成。");
  }

  @Override public void dispose() {
    if (listening && sourceProject != null) {
      sourceProject.removeProjectListener(designListener);
      listening = false;
    }
    invalidateDesign();
    super.dispose();
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
