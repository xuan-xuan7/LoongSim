package com.cburch.logisim.fpga.loongsim;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.NetlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.HDLGeneratorFactory;
import com.cburch.logisim.fpga.gui.FPGAReport;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Clock;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Logisim export, board mapping and synthesis; implementation continues in FpgaImplementation. */
public final class LoongSimFpgaFlow {
  private static final String BOARD_XDC = "loongson-a7-fbg676-1.xdc";
  private static final long BOARD_CLOCK_HZ = 100_000_000L;
  private static final String FPGA_GLOBAL_CLOCK = "FPGA_GlobalClock";
  private static final String CLOCK_TOP_SUFFIX = "_LoongSimTop";
  private static final Pattern XDC_PORT_PATTERN = Pattern.compile(
      "\\[get_(?:ports|nets)\\s+(?:\\{([^}]*)\\}|([^\\s\\]]+))\\]");
  private static final Pattern VERILOG_DECLARATION_PATTERN = Pattern.compile(
      "\\b(?:input|output|inout)\\b([^;\\)]*)", Pattern.MULTILINE);
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
      "[A-Za-z_][A-Za-z0-9_]*");

  public static final class ExportResult {
    private final Path projectDirectory;
    private final String topModule;
    private final List<FpgaPort> ports;

    private ExportResult(Path projectDirectory, String topModule, List<FpgaPort> ports) {
      this.projectDirectory = projectDirectory;
      this.topModule = topModule;
      this.ports = ports;
    }

    public Path getProjectDirectory() {
      return projectDirectory;
    }

    public String getTopModule() {
      return topModule;
    }

    public List<FpgaPort> getPorts() {
      return ports;
    }
  }

  public ExportResult generateVerilog(Project project, Circuit circuit, Path outputDirectory,
      Consumer<String> sink)
      throws IOException {
    if (project == null || circuit == null) {
      throw new IOException("当前没有可导出的电路，请先打开一个电路工程");
    }
    String circuitName = CorrectLabel.getCorrectLabel(circuit.getName());
    if (circuitName.isEmpty()) circuitName = "main";
    if (outputDirectory == null) {
      throw new IOException("必须先选择 Verilog 工程保存目录");
    }
    Path boardXdc = findBoardXdc();
    if (boardXdc == null) {
      throw new IOException("未找到 A7 板卡 XDC 模板，不能生成可实现的 FPGA 工程");
    }
    Path projectDirectory = outputDirectory.toAbsolutePath().normalize();
    clearGeneratedOutputs(projectDirectory);
    Files.createDirectories(projectDirectory.resolve("verilog"));

    report(sink, "开始导出电路: " + circuit.getName());
    FpgaPortLabels displayLabels = new FpgaPortLabels(circuit);
    FPGAReport reporter = new FPGAReport(sink == null ? message -> {} : sink);
    // Keep labels optional in the editor. The original Logisim annotation
    // algorithm supplies stable HDL names only for components that need one.
    circuit.ClearAnnotationLevel();
    circuit.Annotate(false, reporter, false);
    // Refresh the same netlist used by Logisim's original HDL export path.
    circuit.getNetList().clear();
    ArrayList<String> sheetNames = new ArrayList<>();
    if (circuit.getNetList().DesignRuleCheckResult(
        reporter, HDLGeneratorFactory.VERILOG, true, sheetNames)
        != com.cburch.logisim.fpga.designrulecheck.Netlist.DRC_PASSED) {
      throw new IOException("电路设计规则检查失败，无法生成 Verilog");
    }
    Set<String> handledComponents = new HashSet<>();
    // This is the original Logisim circuit HDL generator. The LoongSim layer
    // keeps that circuit module intact and adds board constraints; when a
    // Clock tree exists it also adds a small LoongSim top-level wrapper.
    HDLGeneratorFactory generator = circuit.getSubcircuitFactory()
        .getHDLGenerator(HDLGeneratorFactory.VERILOG, circuit.getStaticAttributes());
    if (generator == null) {
      throw new IOException("当前电路不支持 Verilog HDL 导出");
    }
    boolean generated = generator.GenerateAllHDLDescriptions(
        handledComponents,
        projectDirectory.toString() + java.io.File.separator,
        new ArrayList<>(),
        reporter,
        HDLGeneratorFactory.VERILOG);
    if (!generated) throw new IOException("Verilog 生成失败，请检查电路中的组件是否支持 HDL");

    List<Path> generatedVerilog = findVerilogFiles(projectDirectory);
    if (generatedVerilog.isEmpty()) {
      throw new IOException("原始 HDL 生成器没有生成 Verilog 文件，请检查电路网表");
    }
    for (Path file : generatedVerilog) {
      if (Files.size(file) == 0) {
        throw new IOException("生成的 Verilog 文件为空: " + file);
      }
    }
    report(sink, "已生成 Verilog 文件: " + generatedVerilog.stream()
        .map(Path::toString).collect(Collectors.joining(", ")));

    Path boardTemplate = projectDirectory.resolve("board-template.xdc");
    Files.copy(boardXdc, boardTemplate, StandardCopyOption.REPLACE_EXISTING);
    String topModule = circuitName;
    Path circuitVerilog = findTopVerilog(projectDirectory, circuitName);
    if (circuitVerilog == null) {
      throw new IOException("未找到顶层 Verilog 模块: " + circuitName);
    }
    boolean hasClockWrapper = circuit.getNetList().NumberOfClockTrees() > 0;
    List<FpgaPort> circuitPorts = readTopPorts(circuitVerilog, circuitName);
    List<FpgaPort> mappableCircuitPorts = filterMappablePorts(circuit, circuitPorts, false);
    if (mappableCircuitPorts.isEmpty()) {
      throw new IOException("原始电路模块没有可映射端口: " + circuitName);
    }
    validateReservedClockPort(circuitPorts, circuit.getNetList().RequiresGlobalClockConnection());
    List<Path> projectSources = new ArrayList<>(generatedVerilog);
    Path topVerilog = circuitVerilog;
    if (hasClockWrapper) {
      topModule = circuitName + CLOCK_TOP_SUFFIX;
      topVerilog = projectDirectory.resolve("verilog").resolve(topModule + ".v");
      writeClockSupportSources(projectDirectory, project, reporter);
      writeClockWrapper(topVerilog, topModule, circuitName, circuitPorts, circuit, project);
      projectSources = new ArrayList<>(findVerilogFiles(projectDirectory));
    }
    // The circuit/clock modules retain their original logical values. A distinct
    // outer module adapts the selected board's electrical levels after mapping.
    String logicalModule = topModule;
    List<FpgaPort> logicalPorts = readTopPorts(topVerilog, logicalModule);
    String boardTop = logicalModule + "_LoongSimBoardTop";
    int suffix = 1;
    while (findTopVerilog(projectDirectory, boardTop) != null) {
      boardTop = logicalModule + "_LoongSimBoardTop_" + suffix++;
    }
    topModule = boardTop;
    FpgaBoardAdapter.write(projectDirectory, topModule, logicalModule, logicalPorts, Map.of());
    topVerilog = projectDirectory.resolve("verilog/loongsim-board-interface.v");
    projectSources = findVerilogFiles(projectDirectory);
    for (Path source : projectSources) {
      if (Files.size(source) == 0) {
        throw new IOException("生成的 Verilog 文件为空: " + source);
      }
    }
    List<FpgaPort> ports = displayLabels.apply(circuit, filterMappablePorts(circuit,
        readTopPorts(topVerilog, topModule), hasClockWrapper));
    if (ports.isEmpty()) {
      throw new IOException("顶层 Verilog 没有可映射端口: " + topModule);
    }
    writeProjectFiles(projectDirectory, topModule, projectSources, false);
    writePortLabels(projectDirectory.resolve("port-labels.json"), ports);
    report(sink, "已生成板卡模板: " + boardTemplate);
    report(sink, "已识别顶层端口: " + ports.stream().map(FpgaPort::getDisplayName)
        .collect(Collectors.joining(", ")));
    writePendingReadme(projectDirectory, circuitName, topModule, hasClockWrapper);
    report(sink, "请完成 FPGA 端口映射后再生成专属 XDC");
    report(sink, "Verilog 工程已生成: " + projectDirectory);
    return new ExportResult(projectDirectory, topModule, ports);
  }

  public void runYosys(Path projectDirectory, String topModule, Consumer<String> sink)
      throws IOException, InterruptedException {
    projectDirectory = projectDirectory.toAbsolutePath().normalize();
    FpgaToolchain tools = FpgaToolchain.discover();
    tools.requireSynthesis();
    List<Path> verilogFiles = validateProject(projectDirectory, topModule);
    FpgaBuildArtifacts.clearSynthesis(projectDirectory);
    String inputs = FpgaBuildArtifacts.inputDigest(projectDirectory);
    StringBuilder script = new StringBuilder();
    for (Path file : verilogFiles) {
      script.append("read_verilog ").append(yosysQuote(projectDirectory.relativize(file).toString()
          .replace('\\', '/'))).append(";\n");
    }
    script.append("hierarchy -check -top ").append(topModule).append(";\n");
    script.append("synth_xilinx -flatten -top ").append(topModule).append(";\n");
    script.append("check -assert;\nwrite_json yosys-netlist.json;\n");
    Files.writeString(projectDirectory.resolve("synthesis.ys"), script, StandardCharsets.UTF_8);
    report(sink, "使用内置综合工具: " + tools.yosys());
    FpgaToolProcess.run(tools, projectDirectory,
        List.of(tools.yosys().toString(), "-s", "synthesis.ys"), "synthesis.log", sink);
    if (!inputs.equals(FpgaBuildArtifacts.inputDigest(projectDirectory))) {
      throw new IOException("综合期间源文件或约束发生改变，请重新生成工程并综合");
    }
    FpgaBuildArtifacts.recordSynthesis(projectDirectory);
    report(sink, "综合完成: " + projectDirectory.resolve("yosys-netlist.json"));
  }

  static List<Path> validateProject(Path projectDirectory, String topModule) throws IOException {
    if (topModule == null || !topModule.matches("[A-Za-z_][A-Za-z0-9_$]*")) {
      throw new IOException("无效的工程顶层模块名称");
    }
    Path constraints = projectDirectory.resolve("constraints.xdc");
    if (!Files.isRegularFile(constraints)) {
      throw new IOException("请先完成端口映射并生成 constraints.xdc");
    }
    Path recordedTop = projectDirectory.resolve("top-module.txt");
    if (Files.isRegularFile(recordedTop)) {
      String recorded = Files.readString(recordedTop, StandardCharsets.UTF_8).trim();
      if (!topModule.equals(recorded)) {
        throw new IOException("工程顶层模块与映射记录不一致，请重新生成工程");
      }
    }
    List<Path> verilogFiles = readSourceList(projectDirectory);
    if (verilogFiles.isEmpty()) throw new IOException("工程 sources.list 没有 Verilog 文件，请先生成工程");
    Path topVerilog = findTopVerilog(projectDirectory, topModule);
    if (topVerilog == null) throw new IOException("工程中找不到顶层模块: " + topModule);
    if (!verilogFiles.contains(topVerilog.toAbsolutePath().normalize())) {
      throw new IOException("sources.list 未包含顶层 Verilog 文件: " + topVerilog.getFileName());
    }
    List<FpgaPort> topPorts = readTopPorts(topVerilog, topModule);
    if (topPorts.isEmpty()) throw new IOException("顶层模块没有端口: " + topModule);
    Set<String> topPortNames = topPorts.stream().map(FpgaPort::getName)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    validateGeneratedXdc(constraints, topPortNames);

    return verilogFiles;
  }

  /** Validate a confirmed mapping and create the XDC used by later implementation stages. */
  public void completeMapping(Path projectDirectory, String topModule, List<FpgaPort> ports,
      Map<String, String> mappings, Consumer<String> sink) throws IOException {
    if (projectDirectory == null || topModule == null || ports == null || mappings == null) {
      throw new IOException("端口映射数据不完整");
    }
    if (!Files.isDirectory(projectDirectory.resolve("verilog"))) {
      throw new IOException("找不到 Verilog 源文件目录，请先重新生成工程");
    }
    Map<String, FpgaResource> resources = new LinkedHashMap<>();
    for (FpgaResource resource : LoongSimBoardCatalog.resources()) {
      resources.put(resource.getId(), resource);
    }
    Set<String> usedResources = new HashSet<>();
    Set<String> circuitPorts = ports.stream().map(FpgaPort::getName)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    List<FpgaPort> targets = FpgaPortMapping.targets(ports, mappings);
    for (FpgaPort port : targets) {
      String resourceId = mappings.get(port.getName());
      FpgaResource resource = resourceId == null ? null : resources.get(resourceId);
      if (resource == null || !resource.accepts(port)) {
        throw new IOException("端口未完成有效映射: " + port);
      }
      boolean conflict = usedResources.stream().anyMatch(resourceId::equals)
          || usedResources.stream().anyMatch(existing -> resource.conflictsWith(existing));
      if (conflict) {
        throw new IOException("板卡资源被重复使用: " + resource.getLabel());
      }
      usedResources.add(resourceId);
    }
    Path template = projectDirectory.resolve("board-template.xdc");
    if (!Files.isRegularFile(template)) throw new IOException("找不到 board-template.xdc");
    Set<String> templateResources = readBoardResources(template);
    for (String resource : mappings.values()) {
      if (LoongSimBoardCatalog.isVirtualResource(resource)) continue;
      if (!matchesBoardResource(resource, templateResources)) {
        throw new IOException("板卡资源不在 XDC 模板中: " + resource);
      }
    }
    String logicalModule = FpgaBoardAdapter.logicalModule(projectDirectory);
    Path topVerilog = findTopVerilog(projectDirectory, logicalModule);
    if (topVerilog == null) throw new IOException("找不到顶层 Verilog 模块: " + topModule);
    List<FpgaPort> generatedPorts = readTopPorts(topVerilog, logicalModule);
    if (!containsPorts(generatedPorts, ports)) {
      throw new IOException("顶层 Verilog 已变化，请重新生成工程后再映射");
    }
    FpgaBoardAdapter.MappingResult physical = FpgaBoardAdapter.applyMapping(
        projectDirectory, topModule, generatedPorts, mappings, targets);
    Set<String> physicalNames = physical.ports.stream().map(FpgaPort::getName)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    FpgaBuildArtifacts.clearSynthesis(projectDirectory);
    Path constraints = projectDirectory.resolve("constraints.xdc");
    Set<String> emittedResources = generateCircuitXdc(template, constraints, physicalNames,
        physical.mappings, topModule);
    if (!emittedResources.containsAll(physical.mappings.values())) {
      Set<String> missing = new LinkedHashSet<>(physical.mappings.values());
      missing.removeAll(emittedResources);
      throw new IOException("XDC 模板没有为这些映射生成有效约束: " + missing);
    }
    validateGeneratedXdc(constraints, physicalNames);
    validateMappedIoConstraints(constraints, FpgaPortMapping.targets(physical.ports, physical.mappings));
    Set<String> boardResources = resources.keySet();
    writeMappingJson(projectDirectory.resolve("mapping.json"), topModule, circuitPorts,
        boardResources, mappings, ports);
    writePortLabels(projectDirectory.resolve("port-labels.json"), ports);
    if (mappings.values().stream().anyMatch(LoongSimBoardCatalog::isDisplay)) {
      report(sink, "已自动连接数码管选位；多个显示位置由板载时钟轮流刷新。");
    }
    if (mappings.values().stream().anyMatch(LoongSimBoardCatalog::isKeypadKey)) {
      report(sink, "已连接矩阵键盘按键；扫描、防抖与行列引脚由 LoongSim 处理（按下为1，松开为0）。");
    }
    writeProjectFiles(projectDirectory, topModule, findVerilogFiles(projectDirectory), true);
    Files.write(projectDirectory.resolve("README.txt"), List.of(
        "LoongSim FPGA 工程",
        "顶层模块: " + topModule,
        "目标器件: " + LoongSimBoardCatalog.DEVICE,
        "board-template.xdc: 这块 FPGA 板卡的完整约束模板。",
        "constraints.xdc: 端口映射确认后生成的本电路约束文件。",
        "mapping.json: 电路端口到板卡资源的映射清单。",
        "top-module.txt 和 sources.list 可直接提供给后续综合、布局布线工具。",
        "如果电路使用 Logisim Clock，LoongSim 会自动生成时钟支持模块和顶层封装。",
        "loongsim-board-interface.v 自动适配单色 LED 的低电平点亮；电路逻辑 1 对应灯亮。",
        "完成约束和综合后，constraints.xdc 与 yosys-netlist.json 属于同一个顶层模块。"),
        StandardCharsets.UTF_8);
    report(sink, "端口映射已确认，已生成: " + projectDirectory.resolve("constraints.xdc"));
    for (Map.Entry<String, String> entry : mappings.entrySet()) {
      if (LoongSimBoardCatalog.isActiveLowLed(entry.getValue())) {
        String display = targets.stream().filter(port -> port.getName().equals(entry.getKey()))
            .map(FpgaPort::getDisplayName).findFirst().orElse(entry.getKey());
        report(sink, "已适配 LED 低电平点亮: " + display + "（电路 1 = 灯亮）");
      }
    }
  }

  private static void writePendingReadme(Path projectDirectory, String circuitName,
      String topModule, boolean hasClockWrapper)
      throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("LoongSim FPGA 工程");
    lines.add("电路模块: " + circuitName);
    lines.add("实现顶层模块: " + topModule);
    lines.add("目标器件: " + LoongSimBoardCatalog.DEVICE);
    lines.add("top-module.txt: 后续综合和布局布线使用的顶层模块名。");
    lines.add("sources.list: 后续工具需要读取的全部 Verilog 源文件。");
    lines.add("board-template.xdc: 这块 FPGA 板卡的完整约束模板。");
    lines.add("当前尚未生成 constraints.xdc；请在 LoongSim 的端口映射窗口确认资源后生成。");
    lines.add("mapping.json 会记录电路端口到板卡资源的映射。");
    if (hasClockWrapper) {
      lines.add("检测到 Logisim Clock，已生成 LoongSim 时钟分频器和顶层封装。");
      lines.add("请将顶层 FPGA_GlobalClock 映射到板卡专用 clk 资源。");
    } else {
      lines.add("电路没有额外时钟封装；外层板卡接口在映射后处理 LED 的有效电平。");
    }
    lines.add("完成映射后，constraints.xdc 可直接交给后续 FPGA 实现流程。");
    Files.write(projectDirectory.resolve("README.txt"), lines, StandardCharsets.UTF_8);
  }

  public Path findYosys() {
    Path candidate = FpgaToolchain.discover().yosys();
    return Files.isRegularFile(candidate) ? candidate : null;
  }

  private Path findBoardXdc() {
    for (Path root : roots()) {
      Path candidate = root.resolve("fpga/boards/loongson-a7/" + BOARD_XDC).normalize();
      if (Files.isRegularFile(candidate)) return candidate;
    }
    return null;
  }

  /** Locate the generated Verilog file for the selected top-level circuit. */
  private static Path findTopVerilog(Path projectDirectory, String topModule) throws IOException {
    List<Path> candidates = findVerilogFiles(projectDirectory);
    for (Path candidate : candidates) {
      if (candidate.getFileName().toString().equals(topModule + ".v")) return candidate;
    }
    for (Path candidate : candidates) {
      String source = Files.readString(candidate, StandardCharsets.UTF_8);
      if (Pattern.compile("\\bmodule\\s+" + Pattern.quote(topModule) + "\\b")
          .matcher(source).find()) return candidate;
    }
    return null;
  }

  private static List<Path> findVerilogFiles(Path projectDirectory) throws IOException {
    Path sourceDirectory = projectDirectory.resolve("verilog");
    if (!Files.isDirectory(sourceDirectory)) return new ArrayList<>();
    try (java.util.stream.Stream<Path> stream = Files.walk(sourceDirectory)) {
      return stream.filter(path -> Files.isRegularFile(path)
              && path.toString().toLowerCase().endsWith(".v"))
          .sorted(Comparator.naturalOrder()).collect(Collectors.toList());
    }
  }

  static List<Path> readSourceList(Path projectDirectory) throws IOException {
    Path sourceList = projectDirectory.resolve("sources.list");
    if (!Files.isRegularFile(sourceList)) {
      throw new IOException("工程缺少 sources.list，请重新生成 Verilog 工程");
    }
    List<Path> sources = new ArrayList<>();
    for (String line : Files.readAllLines(sourceList, StandardCharsets.UTF_8)) {
      String relative = line.trim();
      if (relative.isEmpty() || relative.startsWith("#")) continue;
      Path source = projectDirectory.resolve(relative).normalize();
      if (!source.startsWith(projectDirectory) || !Files.isRegularFile(source)
          || !source.toString().toLowerCase().endsWith(".v")) {
        throw new IOException("sources.list 包含无效 Verilog 源文件: " + relative);
      }
      sources.add(source);
    }
    return sources;
  }

  /** Read the external ports declared by a generated Verilog top module. */
  private static List<FpgaPort> readTopPorts(Path verilogFile, String topModule) throws IOException {
    // Generated connection comments contain "input"/"output", not port declarations.
    String source = Files.readString(verilogFile, StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", " ");
    Matcher module = Pattern.compile("\\bmodule\\s+" + Pattern.quote(topModule)
        + "\\b(.*?)(?:\\bendmodule\\b)", Pattern.DOTALL).matcher(source);
    if (!module.find()) return new ArrayList<>();
    String body = module.group(1);
    List<FpgaPort> ports = new ArrayList<>();
    Matcher declaration = VERILOG_DECLARATION_PATTERN.matcher(body);
    while (declaration.find()) addDeclarationPorts(ports, declaration.group(), declaration.group(1));
    if (!ports.isEmpty()) return ports;

    // Older generators use a non-ANSI module header with directions in a separate block.
    Matcher header = Pattern.compile("\\bmodule\\s+" + Pattern.quote(topModule)
        + "\\s*\\((.*?)\\)\\s*;", Pattern.DOTALL).matcher(source);
    if (header.find()) {
      for (String token : header.group(1).split(",")) {
        Matcher identifier = IDENTIFIER_PATTERN.matcher(token);
        String last = null;
        while (identifier.find()) last = identifier.group();
        if (last != null && !"input".equals(last) && !"output".equals(last)
            && !"inout".equals(last)) ports.add(new FpgaPort(last,
                FpgaResource.Direction.INOUT, 1));
      }
    }
    return ports;
  }

  /**
   * Keep only real top-level Logisim pins in the mapping UI. The generated
   * circuit module also contains implementation-only bubble/net ports, and
   * those must never be assigned to a board resource.
   */
  private static List<FpgaPort> filterMappablePorts(Circuit circuit,
      List<FpgaPort> generated, boolean includeGlobalClock) {
    Set<String> allowed = new LinkedHashSet<>();
    Netlist netlist = circuit.getNetList();
    for (int i = 0; i < netlist.NumberOfInputPorts(); i++) addNetlistPort(allowed,
        netlist.GetInputPin(i));
    for (int i = 0; i < netlist.NumberOfInOutPorts(); i++) addNetlistPort(allowed,
        netlist.GetInOutPin(i));
    for (int i = 0; i < netlist.NumberOfOutputPorts(); i++) addNetlistPort(allowed,
        netlist.GetOutputPin(i));
    if (includeGlobalClock) allowed.add(FPGA_GLOBAL_CLOCK);
    return generated.stream()
        .filter(port -> allowed.contains(port.getName()) || isIoBubblePort(port.getName()))
        .collect(Collectors.toList());
  }

  /** Logisim I/O components (LED, button, seven-segment, etc.) are exported
   * through these top-level bubble buses. They are real board-facing signals,
   * unlike the internal s_LOGISIM_NET_* wires. */
  private static boolean isIoBubblePort(String name) {
    return HDLGeneratorFactory.LocalInputBubbleBusname.equals(name)
        || HDLGeneratorFactory.LocalOutputBubbleBusname.equals(name)
        || HDLGeneratorFactory.LocalInOutBubbleBusname.equals(name)
        || name.startsWith(HDLGeneratorFactory.InputBubblePortName)
        || name.startsWith(HDLGeneratorFactory.OutputBubblePortName)
        || name.startsWith(HDLGeneratorFactory.InOutBubblePortName);
  }

  private static void addNetlistPort(Set<String> names, NetlistComponent port) {
    if (port == null || port.GetComponent() == null) return;
    Object label = port.GetComponent().getAttributeSet().getValue(StdAttr.LABEL);
    if (label != null) {
      String corrected = CorrectLabel.getCorrectLabel(label.toString());
      if (!corrected.isEmpty()) names.add(corrected);
    }
  }

  private static void addDeclarationPorts(List<FpgaPort> ports, String declaration,
      String declarationBody) {
    FpgaResource.Direction direction = declaration.trim().startsWith("input")
        ? FpgaResource.Direction.INPUT
        : declaration.trim().startsWith("output")
            ? FpgaResource.Direction.OUTPUT : FpgaResource.Direction.INOUT;
    Matcher range = Pattern.compile("\\[\\s*(\\d+)\\s*:\\s*(\\d+)\\s*\\]")
        .matcher(declarationBody);
    int width = 1;
    if (range.find()) {
      width = Math.abs(Integer.parseInt(range.group(1)) - Integer.parseInt(range.group(2))) + 1;
    }
    String cleaned = declarationBody.replaceAll("\\b(?:wire|reg|logic|tri|signed|unsigned)\\b", "")
        .replaceAll("\\[[^]]*\\]", "");
    for (String item : cleaned.split(",")) {
      Matcher identifier = IDENTIFIER_PATTERN.matcher(item.replaceAll("=.*", ""));
      String last = null;
      while (identifier.find()) last = identifier.group();
      if (last != null) ports.add(new FpgaPort(last, direction, width));
    }
  }

  private static void writeClockSupportSources(Path projectDirectory, Project project,
      FPGAReport reporter) throws IOException {
    Path output = projectDirectory.resolve("verilog").resolve("loongsim-clock-support.v");
    double tickFrequency = project.getSimulator().getTickFrequency();
    if (tickFrequency <= 0) tickFrequency = 1.0;
    if (tickFrequency > BOARD_CLOCK_HZ / 4.0) tickFrequency = BOARD_CLOCK_HZ / 4.0;
    long reload = (long) (BOARD_CLOCK_HZ / tickFrequency);
    if (reload < 1) reload = 1;
    if (reload > 0x7fffffffL) reload = 0x7fffffffL;
    int tickBits = Math.max(1, 64 - Long.numberOfLeadingZeros(reload));

    List<String> lines = new ArrayList<>();
    lines.add("// LoongSim-generated FPGA clock support");
    lines.add("// Board clock: " + BOARD_CLOCK_HZ + " Hz; requested tick: "
        + tickFrequency + " Hz");
    lines.add("module LoongSimTickGenerator #(");
    lines.add("    parameter integer RELOAD_VALUE = " + reload + ",");
    lines.add("    parameter integer COUNTER_BITS = " + tickBits);
    lines.add(")(");
    lines.add("    input FPGA_GlobalClock,");
    lines.add("    output FPGATick");
    lines.add(");");
    lines.add("  reg [COUNTER_BITS-1:0] count_reg;");
    lines.add("  wire [COUNTER_BITS-1:0] count_next = (count_reg == 0)"
        + " ? RELOAD_VALUE-1 : count_reg-1;");
    lines.add("  assign FPGATick = (count_reg == 0);");
    lines.add("  always @(posedge FPGA_GlobalClock) begin");
    lines.add("    count_reg <= count_next;");
    lines.add("  end");
    lines.add("  initial count_reg = 0;");
    lines.add("endmodule");
    lines.add("");
    lines.add("module LoongSimClockGenerator #(");
    lines.add("    parameter integer HIGH_TICKS = 1,");
    lines.add("    parameter integer LOW_TICKS = 1,");
    lines.add("    parameter integer COUNTER_BITS = 1");
    lines.add(")(");
    lines.add("    input GlobalClock,");
    lines.add("    input ClockTick,");
    lines.add("    output [4:0] ClockBus");
    lines.add(");");
    lines.add("  reg [3:0] output_regs;");
    lines.add("  reg [COUNTER_BITS-1:0] counter_reg;");
    lines.add("  reg derived_clock_reg;");
    lines.add("  wire counter_is_zero = (counter_reg == 0);");
    lines.add("  wire [COUNTER_BITS-1:0] counter_next = counter_is_zero"
        + " ? (derived_clock_reg ? LOW_TICKS-1 : HIGH_TICKS-1)"
        + " : counter_reg-1;");
    lines.add("  assign ClockBus = {GlobalClock, output_regs};");
    lines.add("  always @(posedge GlobalClock) begin");
    lines.add("    output_regs[0] <= derived_clock_reg;");
    lines.add("    output_regs[1] <= ~derived_clock_reg;");
    lines.add("    output_regs[2] <= ~derived_clock_reg & ClockTick & counter_is_zero;");
    lines.add("    output_regs[3] <= derived_clock_reg & ClockTick & counter_is_zero;");
    lines.add("    if (counter_is_zero && ClockTick) derived_clock_reg <= ~derived_clock_reg;");
    lines.add("    if (ClockTick) counter_reg <= counter_next;");
    lines.add("  end");
    lines.add("  initial begin");
    lines.add("    output_regs = 0;");
    lines.add("    counter_reg = 0;");
    lines.add("    derived_clock_reg = 0;");
    lines.add("  end");
    lines.add("endmodule");
    Files.write(output, lines, StandardCharsets.UTF_8);
    if (reporter != null) reporter.AddInfo("已生成 LoongSim 时钟支持模块: " + output);
  }

  private static void writeClockWrapper(Path output, String topModule, String circuitModule,
      List<FpgaPort> circuitPorts, Circuit circuit, Project project) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("// LoongSim-generated FPGA top-level clock wrapper");
    List<String> portNames = new ArrayList<>();
    portNames.add(FPGA_GLOBAL_CLOCK);
    for (FpgaPort port : circuitPorts) {
      if (isClockSupportPort(port) || isClockTreePort(port)) continue;
      portNames.add(port.getName());
    }
    lines.add("module " + topModule + "(" + String.join(", ", portNames) + ");");
    lines.add("  input " + FPGA_GLOBAL_CLOCK + ";");
    for (FpgaPort port : circuitPorts) {
      if (isClockSupportPort(port) || isClockTreePort(port)) continue;
      lines.add("  " + portDeclaration(port) + ";");
    }
    boolean requiresGlobalClock = circuit.getNetList().RequiresGlobalClockConnection();
    lines.add("  wire s_FPGA_Tick;");
    if (requiresGlobalClock) {
      lines.add("  assign s_FPGA_Tick = 1'b1;");
    } else {
      lines.add("  LoongSimTickGenerator #(.RELOAD_VALUE(" + tickReload(project)
          + "), .COUNTER_BITS(" + tickCounterBits(project) + ")) tick_generator (");
      lines.add("    .FPGA_GlobalClock(" + FPGA_GLOBAL_CLOCK + "),");
      lines.add("    .FPGATick(s_FPGA_Tick));");
    }
    for (int i = 0; i < circuit.getNetList().NumberOfClockTrees(); i++) {
      Component clock = circuit.getNetList().GetAllClockSources().get(i);
      int high = clock.getAttributeSet().getValue(Clock.ATTR_HIGH).intValue();
      int low = clock.getAttributeSet().getValue(Clock.ATTR_LOW).intValue();
      int max = Math.max(high, low);
      int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(max));
      lines.add("  wire [4:0] " + HDLGeneratorFactory.ClockTreeName + i + ";");
      lines.add("  LoongSimClockGenerator #(.HIGH_TICKS(" + high + "), .LOW_TICKS(" + low
          + "), .COUNTER_BITS(" + bits + ")) clock_generator_" + i + " (");
      lines.add("    .GlobalClock(" + FPGA_GLOBAL_CLOCK + "),");
      lines.add("    .ClockTick(s_FPGA_Tick),");
      lines.add("    .ClockBus(" + HDLGeneratorFactory.ClockTreeName + i + "));" );
    }
    lines.add("  " + circuitModule + " circuit_instance (");
    List<String> connections = new ArrayList<>();
    for (FpgaPort port : circuitPorts) {
      String signal = isClockSupportPort(port)
          ? FPGA_GLOBAL_CLOCK : port.getName();
      if (isClockTreePort(port)) {
        signal = port.getName();
      }
      connections.add("    ." + port.getName() + "(" + signal + ")");
    }
    for (int i = 0; i < connections.size(); i++) {
      lines.add(connections.get(i) + (i + 1 < connections.size() ? "," : ""));
    }
    lines.add("  );");
    lines.add("endmodule");
    Files.write(output, lines, StandardCharsets.UTF_8);
  }

  private static boolean isClockSupportPort(FpgaPort port) {
    return FPGA_GLOBAL_CLOCK.equals(port.getName())
        && port.getDirection() == FpgaResource.Direction.INPUT;
  }

  private static boolean isClockTreePort(FpgaPort port) {
    return port.getName().startsWith(HDLGeneratorFactory.ClockTreeName);
  }

  private static void validateReservedClockPort(List<FpgaPort> ports,
      boolean generatedByLogisimComponent) throws IOException {
    for (FpgaPort port : ports) {
      if (FPGA_GLOBAL_CLOCK.equals(port.getName()) && !generatedByLogisimComponent) {
        throw new IOException("顶层端口名 FPGA_GlobalClock 是 LoongSim 保留名称，"
            + "不能作为普通数据端口使用。");
      }
    }
  }

  private static String portDeclaration(FpgaPort port) {
    String direction = port.getDirection() == FpgaResource.Direction.INPUT ? "input"
        : port.getDirection() == FpgaResource.Direction.OUTPUT ? "output" : "inout";
    String range = port.getWidth() > 1 ? "[" + (port.getWidth() - 1) + ":0] " : "";
    return direction + " " + range + port.getName();
  }

  private static long tickReload(Project project) {
    double freq = project.getSimulator().getTickFrequency();
    if (freq <= 0) freq = 1.0;
    if (freq > BOARD_CLOCK_HZ / 4.0) freq = BOARD_CLOCK_HZ / 4.0;
    long reload = (long) (BOARD_CLOCK_HZ / freq);
    return Math.max(1, Math.min(0x7fffffffL, reload));
  }

  private static int tickCounterBits(Project project) {
    return Math.max(1, 64 - Long.numberOfLeadingZeros(tickReload(project)));
  }

  private static Set<String> readBoardResources(Path template) throws IOException {
    Set<String> boardPorts = new LinkedHashSet<>();
    for (String line : Files.readAllLines(template, StandardCharsets.UTF_8)) {
      Matcher matcher = XDC_PORT_PATTERN.matcher(line);
      while (matcher.find()) {
        String resource = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        String base = basePortName(resource);
        if (base != null) {
          boardPorts.add(resource);
          boardPorts.add(base);
        }
      }
    }
    return boardPorts;
  }

  private static Map<String, String> identityMappings(Set<String> circuitPorts,
      Set<String> boardPorts) {
    Map<String, String> mappings = new LinkedHashMap<>();
    for (String port : circuitPorts) {
      if (boardPorts.contains(port)) mappings.put(port, port);
    }
    return mappings;
  }

  /**
   * Generate an XDC for one circuit. The mapping is Verilog port name -> board resource;
   * identity mappings are used until the photo-based port picker is added.
   */
  public static Set<String> generateCircuitXdc(Path template, Path output, Set<String> circuitPorts,
      Map<String, String> mappings, String topModule) throws IOException {
    Map<String, String> circuitToBoard = new LinkedHashMap<>(mappings);
    Map<String, String> boardToCircuit = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : circuitToBoard.entrySet()) {
      boardToCircuit.put(entry.getValue(), entry.getKey());
    }
    Set<String> allowedBoardPorts = new LinkedHashSet<>(boardToCircuit.keySet());
    List<String> result = new ArrayList<>();
    Set<String> emittedResources = new LinkedHashSet<>();
    result.add("# LoongSim circuit-specific XDC");
    result.add("# Top module: " + topModule);
    result.add("# Generated from: " + template.getFileName());
    result.add("# Only resources mapped to this circuit are included.");
    for (String line : Files.readAllLines(template, StandardCharsets.UTF_8)) {
      String trimmed = line.trim();
      if (trimmed.startsWith("#")) {
        result.add(line);
        continue;
      }
      Matcher matcher = XDC_PORT_PATTERN.matcher(line);
      Set<String> referenced = new LinkedHashSet<>();
      while (matcher.find()) {
        String resource = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (resource != null) referenced.add(resource);
      }
      if (!referenced.isEmpty()) {
        List<String> mappedLines = rewriteXdcLine(line, referenced, boardToCircuit,
            allowedBoardPorts, emittedResources);
        result.addAll(mappedLines);
      } else if (trimmed.startsWith("set_property CFGBVS")
          || trimmed.startsWith("set_property CONFIG_VOLTAGE")
          || trimmed.startsWith("set_property BITSTREAM.")) {
        result.add(line);
      }
    }
    Files.write(output, result, StandardCharsets.UTF_8);
    return emittedResources;
  }

  private static List<String> rewriteXdcLine(String line, Set<String> references,
      Map<String, String> boardToCircuit, Set<String> allowedBoardPorts,
      Set<String> emittedResources) {
    List<String> variants = new ArrayList<>();
    variants.add(line);
    for (String reference : references) {
      List<String> next = new ArrayList<>();
      if (reference.endsWith("[*]")) {
        String base = reference.substring(0, reference.length() - 3);
        String aggregateCircuitPort = boardToCircuit.get(base);
        if (aggregateCircuitPort != null) {
          for (String variant : variants) {
            next.add(replaceXdcReference(variant, reference, aggregateCircuitPort));
          }
          emittedResources.add(base);
        } else {
          for (Map.Entry<String, String> entry : boardToCircuit.entrySet()) {
            if (!entry.getKey().startsWith(base + "[")
                || !matchesBoardResource(entry.getKey(), allowedBoardPorts)) continue;
            for (String variant : variants) {
              next.add(replaceXdcReference(variant, reference, entry.getValue()));
            }
            emittedResources.add(entry.getKey());
          }
        }
      } else {
        String circuitPort = boardToCircuit.get(reference);
        String boardResource = reference;
        if (circuitPort == null) {
          String base = basePortName(reference);
          circuitPort = boardToCircuit.get(base);
          if (circuitPort != null && reference.startsWith(base)) {
            circuitPort += reference.substring(base.length());
            boardResource = base;
          }
        }
        if (circuitPort != null && matchesBoardResource(reference, allowedBoardPorts)) {
          for (String variant : variants) {
            next.add(replaceXdcReference(variant, reference, circuitPort));
          }
          emittedResources.add(boardResource);
        }
      }
      variants = next;
      if (variants.isEmpty()) return variants;
    }
    return variants;
  }

  private static String replaceXdcReference(String line, String oldReference,
      String newReference) {
    Matcher matcher = XDC_PORT_PATTERN.matcher(line);
    StringBuilder result = new StringBuilder();
    int cursor = 0;
    while (matcher.find()) {
      String reference = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      if (!oldReference.equals(reference)) continue;
      int start = matcher.group(1) != null ? matcher.start(1) : matcher.start(2);
      int end = matcher.group(1) != null ? matcher.end(1) : matcher.end(2);
      result.append(line, cursor, start).append(newReference);
      cursor = end;
    }
    if (cursor == 0) return line;
    result.append(line, cursor, line.length());
    return result.toString();
  }

  private static String basePortName(String value) {
    if (value == null) return null;
    Matcher matcher = IDENTIFIER_PATTERN.matcher(value.trim());
    return matcher.find() ? matcher.group() : null;
  }

  private static boolean matchesBoardResource(String reference, Set<String> allowed) {
    if (allowed.contains(reference)) return true;
    String base = basePortName(reference);
    if (base != null && allowed.contains(base)) return true;
    if (reference != null && reference.endsWith("[*]")) {
      String wildcardBase = reference.substring(0, reference.length() - 3);
      for (String resource : allowed) {
        if (resource.equals(wildcardBase) || resource.startsWith(wildcardBase + "[")) return true;
      }
    }
    return false;
  }

  private static boolean samePorts(List<FpgaPort> expected, List<FpgaPort> actual) {
    if (expected.size() != actual.size()) return false;
    Map<String, FpgaPort> actualByName = new LinkedHashMap<>();
    for (FpgaPort port : actual) actualByName.put(port.getName(), port);
    for (FpgaPort port : expected) {
      FpgaPort current = actualByName.get(port.getName());
      if (current == null || current.getDirection() != port.getDirection()
          || current.getWidth() != port.getWidth()) return false;
    }
    return true;
  }

  private static boolean containsPorts(List<FpgaPort> actual, List<FpgaPort> expected) {
    Map<String, FpgaPort> actualByName = new LinkedHashMap<>();
    for (FpgaPort port : actual) actualByName.put(port.getName(), port);
    for (FpgaPort port : expected) {
      FpgaPort current = actualByName.get(port.getName());
      if (current == null || current.getDirection() != port.getDirection()
          || current.getWidth() != port.getWidth()) return false;
    }
    return true;
  }

  private static void validateGeneratedXdc(Path constraints, Set<String> circuitPorts)
      throws IOException {
    for (String line : Files.readAllLines(constraints, StandardCharsets.UTF_8)) {
      Matcher matcher = XDC_PORT_PATTERN.matcher(line);
      while (matcher.find()) {
        String reference = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        String base = basePortName(reference);
        if (base == null || !circuitPorts.contains(base)) {
          throw new IOException("生成的 XDC 引用了不存在的顶层端口: " + reference);
        }
      }
    }
  }

  private static void validateMappedIoConstraints(Path constraints,
      List<FpgaPort> targets) throws IOException {
    Set<String> packagePins = new HashSet<>();
    Set<String> ioStandards = new HashSet<>();
    for (String line : Files.readAllLines(constraints, StandardCharsets.UTF_8)) {
      String trimmed = line.trim();
      Matcher matcher = XDC_PORT_PATTERN.matcher(line);
      while (matcher.find()) {
        String port = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        String base = basePortName(port);
        if (base == null) continue;
        if (trimmed.startsWith("set_property PACKAGE_PIN")) packagePins.add(port);
        if (trimmed.startsWith("set_property IOSTANDARD")) ioStandards.add(port);
      }
    }
    for (FpgaPort target : targets) {
      for (int bit = 0; bit < target.getWidth(); bit++) {
        String reference = target.getWidth() == 1 ? target.getName()
            : target.getName() + "[" + bit + "]";
        if (!packagePins.contains(reference)) {
          throw new IOException("端口缺少 PACKAGE_PIN 约束: " + target.getDisplayName() + "（位 " + bit + "）");
        }
        if (!ioStandards.contains(reference) && !ioStandards.contains(basePortName(reference))
            && !ioStandards.contains(basePortName(reference) + "[*]")) {
          throw new IOException("端口缺少 IOSTANDARD 约束: " + target.getDisplayName() + "（位 " + bit + "）");
        }
      }
    }
  }

  private static void writeProjectFiles(Path projectDirectory, String topModule,
      List<Path> verilogFiles, boolean mapped) throws IOException {
    Files.writeString(projectDirectory.resolve("top-module.txt"), topModule + "\n",
        StandardCharsets.UTF_8);
    List<String> sourceList = new ArrayList<>();
    for (Path source : verilogFiles) {
      sourceList.add(projectDirectory.relativize(source).toString().replace('\\', '/'));
    }
    Files.write(projectDirectory.resolve("sources.list"), sourceList, StandardCharsets.UTF_8);
    List<String> manifest = new ArrayList<>();
    manifest.add("{");
    manifest.add("  \"format\": 1,");
    manifest.add("  \"board\": \"" + LoongSimBoardCatalog.BOARD_ID + "\",");
    manifest.add("  \"device\": \"" + LoongSimBoardCatalog.DEVICE + "\",");
    manifest.add("  \"topModule\": \"" + jsonEscape(topModule) + "\",");
    manifest.add("  \"sources\": \"sources.list\",");
    manifest.add("  \"constraints\": \"" + (mapped ? "constraints.xdc" : "") + "\",");
    manifest.add("  \"synthesisOutput\": \"yosys-netlist.json\",");
    manifest.add("  \"mappingComplete\": " + mapped);
    manifest.add("}");
    Files.write(projectDirectory.resolve("loongsim-project.json"), manifest,
        StandardCharsets.UTF_8);
  }

  private static void writeMappingJson(Path output, String topModule, Set<String> circuitPorts,
      Set<String> boardResources, Map<String, String> mappings, List<FpgaPort> ports) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("{");
    lines.add("  \"board\": \"loongson-a7-fbg676-1\",");
    lines.add("  \"device\": \"" + LoongSimBoardCatalog.DEVICE + "\",");
    lines.add("  \"topModule\": \"" + jsonEscape(topModule) + "\",");
    lines.add("  \"portLabelFile\": \"port-labels.json\",");
    lines.add("  \"boardResources\": [");
    int resourceIndex = 0;
    for (String resource : boardResources) {
      lines.add("    \"" + jsonEscape(resource) + "\""
          + (++resourceIndex < boardResources.size() ? "," : ""));
    }
    lines.add("  ],");
    lines.add("  \"portMappings\": {");
    int index = 0;
    for (Map.Entry<String, String> entry : mappings.entrySet()) {
      String suffix = ++index < mappings.size() ? "," : "";
      lines.add("    \"" + jsonEscape(entry.getKey()) + "\": \""
          + jsonEscape(entry.getValue()) + "\"" + suffix);
    }
    lines.add("  },");
    lines.add("  \"boardResourceLabels\": {");
    Map<String, String> labels = new LinkedHashMap<>();
    for (FpgaResource resource : LoongSimBoardCatalog.resources()) labels.put(resource.getId(), resource.getLabel());
    index = 0;
    for (Map.Entry<String, String> entry : mappings.entrySet()) {
      lines.add("    \"" + jsonEscape(entry.getKey()) + "\": \""
          + jsonEscape(labels.get(entry.getValue())) + "\"" + (++index < mappings.size() ? "," : ""));
    }
    lines.add("  },");
    lines.add("  \"activeLowLedPorts\": [" + mappings.entrySet().stream()
        .filter(entry -> LoongSimBoardCatalog.isActiveLowLed(entry.getValue()))
        .map(entry -> "\"" + jsonEscape(entry.getKey()) + "\"").collect(Collectors.joining(", ")) + "],");
    lines.add("  \"unmappedCircuitPorts\": [");
    List<String> unmapped = ports.stream().filter(port -> !FpgaPortMapping.isComplete(port, mappings))
        .map(FpgaPort::getName).collect(Collectors.toList());
    for (int i = 0; i < unmapped.size(); i++) {
      lines.add("    \"" + jsonEscape(unmapped.get(i)) + "\""
          + (i + 1 < unmapped.size() ? "," : ""));
    }
    lines.add("  ]");
    lines.add("}");
    Files.write(output, lines, StandardCharsets.UTF_8);
  }

  private static void writePortLabels(Path output, List<FpgaPort> ports) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("{\n  \"format\": 1,\n  \"ports\": [");
    for (int index = 0; index < ports.size(); index++) {
      FpgaPort port = ports.get(index);
      lines.add("    {");
      lines.add("      \"hdlName\": \"" + jsonEscape(port.getName()) + "\",");
      lines.add("      \"displayName\": \"" + jsonEscape(port.getDisplayName()) + "\",");
      lines.add("      \"direction\": \"" + port.getDirection() + "\",");
      lines.add("      \"width\": " + port.getWidth() + ",");
      lines.add("      \"bitLabels\": {");
      int bitIndex = 0;
      for (Map.Entry<Integer, String> bit : port.getBitLabels().entrySet()) {
        lines.add("        \"" + bit.getKey() + "\": \"" + jsonEscape(bit.getValue()) + "\""
            + (++bitIndex < port.getBitLabels().size() ? "," : ""));
      }
      lines.add("      }");
      lines.add("    }" + (index + 1 < ports.size() ? "," : ""));
    }
    lines.add("  ]\n}");
    Files.write(output, lines, StandardCharsets.UTF_8);
  }

  private static String jsonEscape(String value) {
    StringBuilder escaped = new StringBuilder();
    for (char character : value.toCharArray()) {
      if (character == '\\' || character == '"') escaped.append('\\').append(character);
      else if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
      else escaped.append(character);
    }
    return escaped.toString();
  }

  private List<Path> roots() {
    Set<Path> roots = new LinkedHashSet<>();
    addRoot(roots, Paths.get(System.getProperty("user.dir")));
    try {
      Path location = Paths.get(LoongSimFpgaFlow.class.getProtectionDomain()
          .getCodeSource().getLocation().toURI());
      addRoot(roots, Files.isRegularFile(location) ? location.getParent() : location);
    } catch (URISyntaxException ignored) {
      // Working-directory candidates cover a source checkout.
    }
    return new ArrayList<>(roots);
  }

  private static void addRoot(Set<Path> roots, Path root) {
    if (root == null) return;
    Path current = root.toAbsolutePath().normalize();
    for (int i = 0; i < 8 && current != null; i++) {
      roots.add(current);
      current = current.getParent();
    }
  }

  private static String yosysQuote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static void report(Consumer<String> sink, String message) {
    if (sink != null) sink.accept(message);
  }

  /** Remove only files produced by this workflow; user files in the chosen folder are preserved. */
  static void clearGeneratedOutputs(Path directory) throws IOException {
    Files.createDirectories(directory);
    Path marker = directory.resolve(".loongsim-generated");
    Path manifest = directory.resolve("loongsim-project.json");
    boolean owned = Files.isRegularFile(marker)
        && "LoongSim FPGA generated outputs v1\n".equals(Files.readString(marker, StandardCharsets.UTF_8));
    if (!owned && Files.isRegularFile(manifest)) {
      owned = Files.readString(manifest, StandardCharsets.UTF_8)
          .contains("\"board\": \"" + LoongSimBoardCatalog.BOARD_ID + "\"");
    }
    if (!owned) {
      for (String name : List.of(".loongsim-generated", "verilog", "README.txt", "board-template.xdc",
          "constraints.xdc", "mapping.json", "port-labels.json", "yosys-netlist.json", "top-module.txt", "sources.list",
          "loongsim-project.json", "synthesis.properties", "implementation.properties", "design.bit",
          "design.fasm", "design.frames", "routed.json", "timing.json", "implementation.xdc")) {
        if (Files.exists(directory.resolve(name))) {
          throw new IOException("所选目录已有同名文件且不是 LoongSim 生成目录，请选择新的工程目录，避免覆盖: " + name);
        }
      }
    }
    Files.writeString(marker, "LoongSim FPGA generated outputs v1\n", StandardCharsets.UTF_8);
    FpgaBuildArtifacts.clearSynthesis(directory);
    String[] generatedDirectories = {"verilog"};
    for (String generated : generatedDirectories) {
      Path generatedPath = directory.resolve(generated);
      if (Files.exists(generatedPath)) deleteTree(generatedPath);
    }
    String[] generatedFiles = {"board-template.xdc", "constraints.xdc", "mapping.json", "port-labels.json",
        "README.txt", "yosys-netlist.json", "top-module.txt", "sources.list",
        "loongsim-project.json"};
    for (String generated : generatedFiles) {
      Files.deleteIfExists(directory.resolve(generated));
    }
  }

  private static void deleteTree(Path directory) throws IOException {
    Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
          if (exception != null) throw exception;
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
  }
}
