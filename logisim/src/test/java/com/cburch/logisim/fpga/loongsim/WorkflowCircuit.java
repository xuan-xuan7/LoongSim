package com.cburch.logisim.fpga.loongsim;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.*;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.memory.Mem;
import com.cburch.logisim.std.memory.MemContents;
import com.cburch.logisim.std.wiring.Constant;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.AddTool;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Actual .circ circuit, pins, wires and production simulator; not a mocked HDL graph. */
final class WorkflowCircuit implements AutoCloseable {
  final Project project;
  Circuit circuit;
  final Map<String,Component> inputs=new LinkedHashMap<>(), outputs=new LinkedHashMap<>();
  final List<Map<String,Long>> vectors=new ArrayList<>();
  final List<Map<String,Long>> expected=new ArrayList<>();
  final String scenario;
  int clock=-1,reset=-1,preset=-1;

  @SuppressWarnings("unchecked") static <T> void set(AttributeSet attributes,String name,String value) {
    Attribute<T> attribute=(Attribute<T>)attributes.getAttribute(name);
    if(attribute==null) throw new IllegalArgumentException("Missing attribute: "+name);
    attributes.setValue(attribute,attribute.parse(value));
  }
  WorkflowCircuit(String scenario) throws Exception {
    this.scenario=scenario;
    try(var template=getClass().getClassLoader().getResourceAsStream("resources/logisim/default.templ")) {
      project=new Project(LogisimFile.loadSub(template,new Loader(null)));
    }
    project.getLogisimFile().getCircuits().forEach(c -> c.SetProject(project));
    circuit=project.getCurrentCircuit();
    if(scenario.equals("hierarchy")) {
      Circuit main=circuit;
      Circuit child=new Circuit("运算模块",project.getLogisimFile(),project);
      project.getLogisimFile().addCircuit(child);circuit=child;buildSingle("adder4");
      inputs.clear();outputs.clear();circuit=main;
      Component instance=child.getSubcircuitFactory().createComponent(Location.create(600,600),child.getSubcircuitFactory().createAttributeSet());
      CircuitMutation mutation=new CircuitMutation(circuit);mutation.add(instance);mutation.execute();
      for(int port=0;port<instance.getEnds().size();port++) attach(instance,port,(instance.getEnd(port).isInput()?"输入":"输出")+port);
    } else if(scenario.equals("accumulator4")) buildAccumulator();
    else buildSingle(scenario);
    if(!scenario.equals("RAM")&&!scenario.equals("SimpleCounter")) generateVectors();
  }
  private void buildSingle(String scenario) throws Exception {
    boolean falling=scenario.endsWith("-falling"),high=scenario.endsWith("-high"),low=scenario.endsWith("-low");
    scenario=scenario.replace("-falling","").replace("-high","").replace("-low","");
    String library="Arithmetic",tool=scenario;
    switch(scenario) {
      case "adder4": tool="Adder";break;
      case "subtractor4": tool="Subtractor";break;
      case "alu2": tool="ALU";break;
      case "comparator4": tool="Comparator";break;
      case "shifter4": tool="Shifter";break;
      case "mux4": library="Plexers";tool="Multiplexer";break;
      case "demux2": library="Plexers";tool="Demultiplexer";break;
      case "decoder3": library="Plexers";tool="Decoder";break;
      case "extender4to8": library="Wiring";tool="Bit Extender";break;
      case "register4": library="Memory";tool="Register";clock=2;reset=3;break;
      case "dff": library="Memory";tool="D Flip-Flop";clock=1;reset=4;preset=5;break;
      case "tff": library="Memory";tool="T Flip-Flop";clock=1;reset=4;preset=5;break;
      case "jkff": library="Memory";tool="J-K Flip-Flop";clock=2;reset=5;preset=6;break;
      case "srff": library="Memory";tool="S-R Flip-Flop";clock=2;reset=5;preset=6;break;
      case "rom4": library="Memory";tool="ROM";break;
      case "RAM": case "SimpleCounter": library="Memory";break;
      default: throw new IllegalArgumentException(scenario);
    }
    InstanceFactory factory=(InstanceFactory)((AddTool)project.getLogisimFile().getLibrary(library).getTool(tool)).getFactory();
    AttributeSet attrs=factory.createAttributeSet();
    if(falling) attrs.setValue(attrs.containsAttribute(StdAttr.EDGE_TRIGGER)?StdAttr.EDGE_TRIGGER:StdAttr.TRIGGER,StdAttr.TRIG_FALLING);
    if(high||low) attrs.setValue(StdAttr.TRIGGER,high?StdAttr.TRIG_HIGH:StdAttr.TRIG_LOW);
    if(attrs.containsAttribute(StdAttr.WIDTH)) attrs.setValue(StdAttr.WIDTH,BitWidth.create(scenario.equals("alu2")||scenario.equals("demux2")?2:4));
    if(scenario.equals("decoder3")) set(attrs,"select","3");
    if(scenario.equals("demux2")) set(attrs,"select","2");
    if(scenario.equals("extender4to8")){set(attrs,"in_width","4");set(attrs,"out_width","8");set(attrs,"type","sign");}
    if(scenario.equals("rom4")) {
      attrs.setValue(Mem.ADDR_ATTR,BitWidth.create(4)); attrs.setValue(Mem.DATA_ATTR,BitWidth.create(4));
      MemContents contents=(MemContents)attrs.getValue(attrs.getAttribute("contents"));
      for(int address=0;address<16;address++) contents.set(address,(address*7+3)&15);
    }
    Component component=factory.createComponent(Location.create(600,600),attrs);
    CircuitMutation mutation=new CircuitMutation(circuit); mutation.add(component);mutation.execute();
    for(int port=0;port<component.getEnds().size();port++) {
      EndData end=component.getEnd(port);
      boolean constant=(scenario.equals("adder4")||scenario.equals("subtractor4"))&&port==3 || port==preset;
      if(constant) { connectConstant(component,port,0); continue; }
      attach(component,port,port==clock?"clk":(end.isInput()?"输入":"输出")+port);
    }
  }
  private Component place(String library,String tool,int x,int y) {
    InstanceFactory factory=(InstanceFactory)((AddTool)project.getLogisimFile().getLibrary(library).getTool(tool)).getFactory();
    AttributeSet attrs=factory.createAttributeSet();attrs.setValue(StdAttr.WIDTH,BitWidth.create(4));
    Component component=factory.createComponent(Location.create(x,y),attrs);
    CircuitMutation mutation=new CircuitMutation(circuit);mutation.add(component);mutation.execute();return component;
  }
  private void wire(Location... points) {
    CircuitMutation mutation=new CircuitMutation(circuit);
    for(int i=1;i<points.length;i++) mutation.add(Wire.create(points[i-1],points[i]));mutation.execute();
  }
  private void buildAccumulator() {
    clock=2;reset=3;
    Component adder=place("Arithmetic","Adder",400,400);
    Component mux=place("Plexers","Multiplexer",700,400);
    Component reg=place("Memory","Register",1000,400);
    connectConstant(adder,1,1);connectConstant(adder,3,0);connectConstant(reg,4,1);
    Location sum=adder.getEnd(2).getLocation(),first=mux.getEnd(0).getLocation();
    wire(sum,Location.create(450,sum.getY()),Location.create(450,first.getY()),first);
    Location selected=mux.getEnd(3).getLocation(),d=reg.getEnd(1).getLocation();
    wire(selected,Location.create(800,selected.getY()),Location.create(800,d.getY()),d);
    Location q=reg.getEnd(0).getLocation(),a=adder.getEnd(0).getLocation();
    wire(q,Location.create(1200,q.getY()),Location.create(1200,200),Location.create(250,200),Location.create(250,a.getY()),a);
    attach(mux,1,"装填数据");attach(mux,2,"装填开关");
    attach(reg,2,"clk");attach(reg,3,"输入3");attach(reg,0,"结果");
  }
  private void connectConstant(Component component,int port,long value) {
    AttributeSet attrs=Constant.FACTORY.createAttributeSet(); attrs.setValue(StdAttr.WIDTH,component.getEnd(port).getWidth());
    attrs.setValue(Constant.ATTR_VALUE,value);
    Component constant=Constant.FACTORY.createComponent(component.getEnd(port).getLocation(),attrs);
    CircuitMutation mutation=new CircuitMutation(circuit);mutation.add(constant);mutation.execute();
  }
  private void attach(Component component,int port,String label) {
    EndData end=component.getEnd(port); Location point=end.getLocation(); Bounds bounds=component.getBounds();
    int[] distance={Math.abs(point.getX()-bounds.getX()),Math.abs(point.getX()-bounds.getX()-bounds.getWidth()),
        Math.abs(point.getY()-bounds.getY()),Math.abs(point.getY()-bounds.getY()-bounds.getHeight())};
    int side=0;for(int i=1;i<4;i++) if(distance[i]<distance[side])side=i;
    int[] dx={-150,150,0,0},dy={0,0,-150,150};
    Location position=point.translate(dx[side],dy[side]);
    AttributeSet attrs=Pin.FACTORY.createAttributeSet(); attrs.setValue(Pin.ATTR_TYPE,end.isOutput());
    attrs.setValue(StdAttr.WIDTH,end.getWidth()); attrs.setValue(StdAttr.LABEL,label);
    attrs.setValue(StdAttr.FACING,new Direction[]{Direction.EAST,Direction.WEST,Direction.SOUTH,Direction.NORTH}[side]);
    Component pin=Pin.FACTORY.createComponent(position,attrs);
    CircuitMutation mutation=new CircuitMutation(circuit);mutation.add(pin);mutation.add(Wire.create(point,position));mutation.execute();
    (end.isInput()?inputs:outputs).put(label,pin);
  }
  private void generateVectors() {
    int bits=inputs.entrySet().stream().filter(e -> !e.getKey().equals("clk")).mapToInt(e -> e.getValue().getEnd(0).getWidth().getWidth()).sum();
    int count=clock<0&&bits<=9?1<<bits:128;
    Random random=new Random(0xC1AC017L+scenario.hashCode());
    for(int index=0;index<count;index++) {
      Map<String,Long> vector=new LinkedHashMap<>();int shift=0;
      for(var entry:inputs.entrySet()) {
        int width=entry.getValue().getEnd(0).getWidth().getWidth();
        long value=clock<0&&bits<=9?(index>>shift):random.nextLong();shift+=width;
        if(entry.getKey().equals("clk"))value=0;
        if(entry.getKey().equals("输入"+reset))value=index==0||index%31==0?1:0;
        // S=R=1 is an explicitly invalid SR-latch input, not a defined truth-table vector.
        if(scenario.startsWith("srff")&&entry.getKey().equals("输入1")&&vector.getOrDefault("输入0",0L)==1)value=0;
        vector.put(entry.getKey(),value&BitWidth.create(width).getMask());
      }
      // 011 is reserved by the ALU specification and is checked separately as an invalid input.
      if(scenario.equals("alu2")&&vector.get("输入2")==3)continue;
      // Set up data while retaining the previous clock level before the next edge.
      // Changing both together creates a zero-setup-time race in HDL simulation.
      if(clock>=0&&!vectors.isEmpty()) {
        Map<String,Long> setup=new LinkedHashMap<>(vector);
        setup.put("clk",vectors.get(vectors.size()-1).get("clk"));
        vectors.add(setup);
      }
      vectors.add(vector);
      if(clock>=0){
        Map<String,Long> high=new LinkedHashMap<>(vector);high.put("clk",1L);vectors.add(high);
        if(scenario.startsWith("register")){
          Map<String,Long> hold=new LinkedHashMap<>(high);hold.put("输入1",(hold.get("输入1")+1)&15);vectors.add(hold);
        }
      }
    }
  }
  void simulate() {
    for(Map<String,Long> vector:vectors) {
      for(var entry:inputs.entrySet()) {
        Component pin=entry.getValue();
        Pin.FACTORY.setValue(Pin.FACTORY.createInstanceState(project.getCircuitState(),pin),
            Value.createKnown(pin.getEnd(0).getWidth(),vector.get(entry.getKey())));
        project.getCircuitState().markComponentAsDirty(pin);
      }
      project.getCircuitState().getPropagator().propagate();
      Map<String,Long> values=new LinkedHashMap<>();
      for(var entry:outputs.entrySet()) {
        Value result=project.getCircuitState().getValue(entry.getValue().getEnd(0).getLocation());
        if(!result.isFullyDefined())throw new AssertionError(scenario+" vector "+vector+" -> "+entry.getKey()+"="+result);
        values.put(entry.getKey(),result.toLongValue());
      }
      expected.add(values);
    }
  }
  byte[] save() throws Exception {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    Method write=Arrays.stream(LogisimFile.class.getDeclaredMethods()).filter(m -> m.getName().equals("write")&&m.getParameterCount()==2).findFirst().orElseThrow();
    write.setAccessible(true);write.invoke(project.getLogisimFile(),bytes,project.getLogisimFile().getLoader());return bytes.toByteArray();
  }
  void writeExample(Path path) throws Exception {Files.write(path,save());}
  @Override public void close(){project.getSimulator().shutDown();}
}
