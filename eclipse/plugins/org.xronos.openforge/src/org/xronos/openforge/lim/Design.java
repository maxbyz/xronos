/*******************************************************************************
 * Copyright 2002-2009  Xilinx Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
/*
 * 
 *
 * 
 */

package org.xronos.openforge.lim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xronos.openforge.app.Engine;
import org.xronos.openforge.app.EngineThread;
import org.xronos.openforge.app.project.SearchLabel;
import org.xronos.openforge.forge.api.entry.EntryMethod;
import org.xronos.openforge.forge.api.internal.EntryMethods;
import org.xronos.openforge.forge.api.pin.Buffer;
import org.xronos.openforge.forge.api.pin.ClockPin;
import org.xronos.openforge.forge.api.pin.ResetPin;
import org.xronos.openforge.forge.api.sim.pin.PinSimData;
import org.xronos.openforge.forge.api.sim.pin.SequentialPinData;
import org.xronos.openforge.lim.io.FSLFifoInput;
import org.xronos.openforge.lim.io.FSLFifoOutput;
import org.xronos.openforge.lim.io.FifoID;
import org.xronos.openforge.lim.io.FifoIF;
import org.xronos.openforge.lim.io.SimplePin;
import org.xronos.openforge.lim.io.actor.ActorNativeScalarInput;
import org.xronos.openforge.lim.io.actor.ActorNativeScalarOutput;
import org.xronos.openforge.lim.io.actor.ActorScalarInput;
import org.xronos.openforge.lim.io.actor.ActorScalarOutput;
import org.xronos.openforge.lim.memory.LogicalMemory;
import org.xronos.openforge.util.naming.ID;

/**
 * Design is the top level representation of a an implementation in hardware. It
 * consists of one or more parallel {@link Task Tasks} and zero or more global
 * {@link Resource Resources} that are used by these Tasks.
 * 
 * @author Stephen Edwards
 * @version $Id: Design.java 538 2007-11-21 06:22:39Z imiller $
 */
public class Design extends ID implements Visitable, Cloneable {

	public static class ClockDomain {
		private static class ControlPin extends SimplePin {
			private ControlPin(String name) {
				super(1, name);
			}
		}

		public static String[] parse(String spec) {
			String[] result = { "", "" };
			String[] split = spec.split(":");
			if (split.length > 0) {
				result[0] = split[0];
			} else {
				throw new IllegalArgumentException("Cannot parse clock domain");
			}
			if (split.length > 1) {
				result[1] = split[1];
			}
			return result;
		}

		private final SimplePin clock;
		private SimplePin reset;

		private final GlobalReset.Physical gsr;

		private final String domainSpec;

		private ClockDomain(String domainSpec) {
			this.domainSpec = domainSpec;
			String[] parsed = parse(domainSpec);
			String clk = parsed[0];
			String rst = parsed[1];
			clock = new ControlPin(clk);
			if (rst != null && rst.length() > 0) {
				reset = new ControlPin(rst);
			}
			gsr = new GlobalReset.Physical(reset != null);
			clock.connectBus(Collections.singleton(gsr.getClockInput()));
			if (reset != null) {
				reset.connectBus(Collections.singleton(gsr.getResetInput()));
			}
		}

		public void connectComponentToDomain(Component comp) {
			if (comp.getClockPort().isUsed()) {
				clock.connectBus(Collections.singleton(comp.getClockPort()));
			}
			if (comp.getResetPort().isUsed()) {
				comp.getResetPort().setBus(gsr.getResetOutput());
			}
		}

		// Private methods are accessible to Design
		public SimplePin getClockPin() {
			return clock;
		}

		public String getDomainKeyString() {
			return domainSpec;
		}

		private GlobalReset.Physical getGSR() {
			return gsr;
		}

		public SimplePin getResetPin() {
			return reset;
		}
	}

	/**
	 * This is a specific module type used to hold all the top level components
	 * for the design.
	 */
	public static class DesignModule extends Module {
		@Override
		public void accept(Visitor vis) {
			throw new UnsupportedOperationException(
					"Cannot directly visit a design Module");
		}

		@Override
		public void addComponent(Component comp) {
			super.addComponent(comp);
		}

		@Override
		public Collection<Component> getComponents() {
			LinkedHashSet<Component> comps = new LinkedHashSet<Component>(
					super.getComponents());
			comps.remove(getInBuf());
			comps.remove(getOutBufs());
			return comps;
		}

		@Override
		public boolean replaceComponent(Component removed, Component inserted) {
			if (!removeComponent(removed)) {
				return false;
			}
			addComponent(inserted);
			return true;
		}

	}

	private List<Task> taskList = Collections.emptyList();

	private Collection<Register> registers = Collections.emptyList();

	/**
	 * Tracks the allocated memory ID's for this design. Note that memory ID 0
	 * is reserved for the Null object.
	 */
	private int memoryId = 1;
	/**
	 * This is a map of String (the fifo ID) to an instance of FifoIF which
	 * contains all the necessary pins for the fifo interface. Uses a linked
	 * hashmap as a convenience to the user so that the translated Verilog has
	 * the same port ordering each time.
	 */
	private final Map<String, FifoIF> fifoInterfaces = new LinkedHashMap<String, FifoIF>();

	private Collection<Pin> inputPins = Collections.emptyList();

	private Collection<Pin> outputPins = Collections.emptyList();
	// private Collection bidirectionalPins=Collections.EMPTY_LIST;

	private Collection<LogicalMemory> logicalMemories = Collections.emptyList();

	/**
	 * Holds a reference to the {@link Tester} that is capable of testing this
	 * <code>Design</code>.
	 */
	private Tester tester = null;

	/** Map of defined clock domains. */
	private final Map<String, ClockDomain> clockDomains = new HashMap<String, ClockDomain>();

	/** map of api clock pin name to input pins (clocks) */
	private final HashMap<String, Pin> apiClockNameToLIMClockMap = new HashMap<String, Pin>();

	/** map of api reset pin name to input pins (reset) */
	private final HashMap<String, GlobalReset> apiResetNameToLIMResetMap = new HashMap<String, GlobalReset>();

	/**
	 * A mapping between {@link Pin} and {@link Port} where the Port is the port
	 * on a Call (entry method) and the Pin is the pin created to connect to
	 * that port. This map is used by the automatic test bench generator to
	 * provide correlations.
	 */
	private final Map<ID, ID> pinPortBusMap = new HashMap<ID, ID>();

	/** The max gate depth */
	private int maxGateDepth = 0;

	/** The max unbreakable gate depth */
	private int unbreakableGateDepth = 0;

	/** The include statements */
	private List<String> includeStatements = Collections.emptyList();

	private final CodeLabel searchLabel;

	private Set<EntryMethod> entryData;

	private Map<Buffer, SequentialPinData> pinSimDriveMap;

	private Map<Buffer, SequentialPinData> pinSimTestMap;

	private final DesignModule designModule;

	public Design() {
		super();
		apiClockNameToLIMClockMap.clear();
		apiResetNameToLIMResetMap.clear();
		designModule = new DesignModule();
		searchLabel = new CodeLabel("design");
	}

	@Override
	public void accept(Visitor vis) {
		vis.visit(this);
	}

	/**
	 * Adds the specified components to the design module. This method does NOT
	 * update any of the Design 'get' methods.
	 * 
	 * @param comp
	 *            a value of type 'Component'
	 */
	public void addComponentToDesign(Collection<Component> comps) {
		getDesignModule().addComponents(comps);
	}

	/**
	 * Adds the specified component to the design module. This method does NOT
	 * update any of the Design 'get' methods.
	 * 
	 * @param comp
	 *            a value of type 'Component'
	 */
	public void addComponentToDesign(Component comp) {
		getDesignModule().addComponent(comp);
	}

	/**
	 * Mainly for use with IPCore. Sets the include statement in a Design
	 * header.
	 * 
	 * @param include
	 *            the HDL source to be included
	 */
	public void addIncludeStatement(String include) {
		if (includeStatements.isEmpty()) {
			includeStatements = new ArrayList<String>(1);
		}

		if (!includeStatements.contains(include)) {
			includeStatements.add(include);
		}
	}

	/**
	 * Add an InputPin in the list of InputPins for this Design
	 * 
	 * @param pin
	 *            {@link InputPin}
	 */
	public void addInputPin(InputPin pin) {
		if (inputPins == Collections.EMPTY_LIST) {
			inputPins = new ArrayList<Pin>(3);
		}
		inputPins.add(pin);
	}

	/**
	 * Adds a {@link BidirectionalPin} to this Design.
	 */
	// public void addBidirectionalPin (BidirectionalPin pin)
	// {
	// if (this.bidirectionalPins == Collections.EMPTY_LIST)
	// {
	// this.bidirectionalPins = new LinkedList();
	// }
	// this.bidirectionalPins.add(pin);
	// }

	public void addInputPin(InputPin pin, Port port) {
		addInputPin(pin);

		pinPortBusMap.put(pin, port);
		pinPortBusMap.put(port, pin);
	}

	/**
	 * Adds the given {@link LogicalMemory} to this design
	 */
	public void addMemory(LogicalMemory mem) {
		if (logicalMemories == Collections.EMPTY_LIST) {
			logicalMemories = new ArrayList<LogicalMemory>(3);
		}
		logicalMemories.add(mem);
		// TBD. When we have full flow we will need to come back
		// through and ensure that any object/struct reference has a
		// unique identifier regardless of what memory it is in.
		// Also, we'll need to add to the MemoryDispositionReporter
		// support for LogicalMemories.
		// mem.setMemoryId(getNextMemoryId());
	}

	/**
	 * Add an OutputPin in the list of OutputPins for this Design
	 * 
	 * @param pin
	 *            {@link OutputPin}
	 */
	public void addOutputPin(OutputPin pin) {
		if (outputPins == Collections.EMPTY_LIST) {
			outputPins = new ArrayList<Pin>(3);
		}
		outputPins.add(pin);
	}

	/**
	 * Add an OutputPin in the list of OutputPins for this Design
	 * 
	 * @param pin
	 *            {@link OutputPin}
	 */
	public void addOutputPin(OutputPin pin, Bus bus) {
		this.addOutputPin(pin);
		pinPortBusMap.put(pin, bus);
		pinPortBusMap.put(bus, pin);
	}

	/**
	 * Add a Register in the list of Register for this Design
	 * 
	 * @param reg
	 *            {@link Register Register}
	 */
	public void addRegister(Register reg) {
		if (registers == Collections.EMPTY_LIST) {
			registers = new ArrayList<Register>(3);
		}
		registers.add(reg);
	}

	public void addTask(Task tk) {
		if (taskList == Collections.EMPTY_LIST) {
			taskList = new ArrayList<Task>(3);
		}
		taskList.add(tk);
		addComponentToDesign(tk.getCall());
	}

	/**
	 * Clear Core, Entry, PinSim
	 * 
	 */
	public void clearAPIContext() {
		// ipcore - see saveAPIContext comments above.

		// entry
		EntryMethods.clearEntryMethods();

		// pinsim
		PinSimData.clear();
	}

	/**
	 * 
	 * Creates a new and fully independent copy of the design. The cloning
	 * process works in two phases. In the first phase, the clone in
	 * accomplished by simply cloning each task in the design. Once cloned the
	 * new copy of the task is visited by the Design.DesignCloneVisitor. The job
	 * of this visitor is to find each Call in the design and point it to the
	 * clone of the original procedure. This process ensures that each procedure
	 * is only cloned once, and that any call pointing to a given procedure is
	 * re-targetted to the corresponding clone. Once the process of cloning each
	 * Task is completed, all global resources are cloned. This includes
	 * memories, registers, pins, etc. As each one is cloned we point each
	 * access in the cloned LIM (cloned task hierarchies) to the corresponding
	 * new resource. This is accomplished by iterating over the collection of
	 * references stored by each original resource. Each reference is contained
	 * in a module (by LIM definition) which has a clone correlation map. This
	 * map provides a correlation between that original reference and the clone
	 * reference. The clone reference then has it's {@link Referent} set to the
	 * cloned resource via the {@link Reference#setReferent} method.
	 * 
	 * @return a deeply cloned copy of the Design
	 * @exception CloneNotSupportedException
	 *                if an error occurs
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();

		/*
		 * Leave this out and untested until it is really needed.
		 */

		// Design clone = (Design)super.clone();

		// // Clone each task.
		// // DesignCloneVisitor cloneVisitor = new DesignCloneVisitor();
		// Map thisToClone = new HashMap();
		// if (this.taskList.size() > 0)
		// {
		// clone.taskList = new ArrayList(this.taskList.size());
		// for (Iterator iter = this.taskList.iterator(); iter.hasNext();)
		// {
		// Task origTask = (Task)iter.next();
		// Task cloneTask = (Task)origTask.clone();
		// //
		// // Do a 'deep' clone, cloning the procedure attached
		// // to each call, unless we've already cloned that
		// // procedure
		// // ABK: not anymore. Call.clone() now makes a freshly
		// // cloned Procedure.
		// // cloneTask.accept(cloneVisitor);
		// clone.taskList.add(cloneTask);
		// correlate(origTask, cloneTask, thisToClone);
		// }
		// }
		// else
		// {
		// clone.taskList = Collections.EMPTY_LIST;
		// }

		// // Now clone each accessed resource

		// clone.registers = cloneResources(this.registers);

		// // Clone any memories.
		// if (this.memories.size() > 0)
		// {
		// clone.memories = new ArrayList(this.memories.size());
		// for (Iterator iter = this.memories.iterator(); iter.hasNext();)
		// {
		// Memory origMem = (Memory)iter.next();
		// Memory cloneMem = (Memory)origMem.clone();
		// for (Iterator oMemPortIter = origMem.getMemoryPorts().iterator(),
		// cMemPortIter = cloneMem.getMemoryPorts().iterator();
		// oMemPortIter.hasNext();)
		// {
		// resetReferent((Resource)oMemPortIter.next(),
		// (Resource)cMemPortIter.next());
		// }
		// }
		// }
		// else
		// {
		// clone.memories = Collections.EMPTY_LIST;
		// }

		// clone.sharedProcedures = cloneResources(this.sharedProcedures);

		// // Create a port/bus correlation map from this to clone.
		// clone.pinPortBusMap = new HashMap();
		// clone.inputPins = clonePins(this.inputPins, clone, thisToClone);
		// clone.outputPins = clonePins(this.outputPins, clone, thisToClone);
		// clone.bidirectionalPins = clonePins(this.bidirectionalPins, clone,
		// thisToClone);

		// clone.resources = cloneResources(this.resources);

		// return clone;
	}

	/**
	 * Tests whether or not this design is clocked.
	 * 
	 * @return true if this design contains any elements that require a clock
	 */
	public boolean consumesClock() {
		for (Component component : getDesignModule().getComponents()) {
			if (component.consumesClock()) {
				return true;
			}
		}
		for (Pin pin : getPins()) {
			if (pin.consumesClock()) {
				return true;
			}
		}

		return !getRegisters().isEmpty() || !getLogicalMemories().isEmpty();
	}

	/**
	 * Tests whether or not this design is resettable.
	 * 
	 * @return true if this design contains any elements that require a reset
	 */
	public boolean consumesReset() {
		for (Component component : getDesignModule().getComponents()) {
			if (component.consumesReset()) {
				return true;
			}
		}

		// this adds logic for pins FIXME for all the other things!
		for (Pin pin : getPins()) {
			if (pin.consumesReset()) {
				return true;
			}
		}
		return !getRegisters().isEmpty() || !getLogicalMemories().isEmpty();
	}

	/**
	 * Retrieves a collection of all the allocated clock domains for this
	 * design.
	 */
	public Collection<ClockDomain> getAllocatedClockDomains() {
		return Collections.unmodifiableCollection(clockDomains.values());
	}

	/**
	 * Old way of getting the inout pins, not used any longer???
	 * 
	 * @return a value of type 'Collection'
	 * @deprecated
	 */
	@Deprecated
	public Collection<Pin> getBidirectionalPins() {
		// return bidirectionalPins;
		return Collections.emptyList();
	}

	/**
	 * <code>getClockDomain</code> returns the appropriate {@link ClockDomain}
	 * for the specified clk/reset string. The String takes the form of
	 * clkname:resetname or simply clkname if there is no (published) reset for
	 * that clock domain.
	 * 
	 * @param domainSpec
	 *            a <code>String</code> value
	 * @return a <code>ClockDomain</code> value
	 */
	public ClockDomain getClockDomain(String domainSpec) {
		ClockDomain domain = clockDomains.get(domainSpec);
		if (domain == null) {
			domain = new ClockDomain(domainSpec);
			addComponentToDesign(domain.getClockPin());
			if (domain.getResetPin() != null) {

				addComponentToDesign(domain.getResetPin());
			}
			addComponentToDesign(domain.getGSR());
			clockDomains.put(domainSpec, domain);
		}
		return domain;
	}

	/**
	 * method to return an InputPin representing a clock. If not already
	 * defined, define it first.
	 * 
	 * @param apiClockPin
	 *            api ClockPin corresponding to the requested lim clock pin
	 * @return the InputPin representing clockName calls Job.fatalError() if the
	 *         name is already defined as a reset pin
	 */
	public InputPin getClockPin(ClockPin apiClockPin) {
		String name = apiClockPin.getName();

		if (apiResetNameToLIMResetMap.containsKey(name)) {
			EngineThread.getEngine().fatalError(
					"Can't have clock and reset share a pin name: "
							+ apiClockPin);
		}

		Pin clockPin = apiClockNameToLIMClockMap.get(name);
		// if not defined, then define a clock pin & store it
		if (clockPin == null) {
			clockPin = new InputPin(1, false);
			clockPin.setApiPin(apiClockPin);
			clockPin.setIDLogical(name);
			addInputPin((InputPin) clockPin);
			apiClockNameToLIMClockMap.put(name, clockPin);
		}
		return (InputPin) clockPin;
	}

	/**
	 * return the LIM Clock Pins
	 */
	public Collection<Pin> getClockPins() {
		return apiClockNameToLIMClockMap.values();
	}

	public DesignModule getDesignModule() {
		return designModule;
	}

	public Engine getEngine() {
		return EngineThread.getEngine();
	}

	/**
	 * Retrieves the specific FifoIF object that exists in this design for the
	 * attributes contained in the specified {@link FifoID} object. If a
	 * matching {@link FifoIF} object has not yet been allocated on this design,
	 * then one will be created and returned. Subsequent calls to getFifoIF with
	 * a fifoID with the same criteria will return the same FifoIF object.
	 * 
	 * @param fifoID
	 *            a non null {@link FifoID}
	 * @return a value of type 'FifoIF'
	 * @throws IllegalArgumentException
	 *             if the fifoID contains criteria that conflict with an already
	 *             allocated fifo interface. Including, for example, requesting
	 *             a fifo interface with the same ID but a different data path
	 *             width.
	 */
	public FifoIF getFifoIF(FifoID fifoID) {
		// This method must look at the ID number specified in the id class
		// and return the FifoIF that has been allocated for that ID number.
		// Thus all fifoID instances with ID with matching number and
		// direction must return the same FifoIF. Some rudimentary checking
		// should be performed to try to catch configuration errors early on,
		// such as returning a FifoIF for a given ID number when the fifoID
		// instances data width does not match the FifoIF, direction does not
		// match, etc. If a FifoIF has not yet been created for the given ID
		// number, then create a new FifoIF (of the right direction input or
		// output), add all of its pins to this design, and then return the
		// FifoIF object.

		String key = fifoID.getName() + "" + fifoID.isInputFifo();
		FifoIF fifoIF = fifoInterfaces.get(key);

		if (fifoIF == null) {
			String id = fifoID.getName();

			switch (fifoID.getType()) {
			case FifoID.TYPE_FSL:
				if (fifoID.isInputFifo()) {
					fifoIF = new FSLFifoInput(id, fifoID.getBitWidth());
				} else {
					fifoIF = new FSLFifoOutput(id, fifoID.getBitWidth());
				}
				break;
			case FifoID.TYPE_ACTION_SCALAR:
				if (fifoID.isInputFifo()) {
					fifoIF = new ActorScalarInput(fifoID);
				} else {
					fifoIF = new ActorScalarOutput(fifoID);
				}
				break;
			case FifoID.TYPE_ACTION_NATIVE_SCALAR:
				if (fifoID.isInputFifo()) {
					fifoIF = new ActorNativeScalarInput(fifoID);
				} else {
					fifoIF = new ActorNativeScalarOutput(fifoID);
				}
				break;
			case FifoID.TYPE_ACTION_CIRCULAR_BUFFER:
				if (fifoID.isInputFifo()) {
					fifoIF = new ActorScalarInput(fifoID);
				} else {
					fifoIF = new ActorScalarOutput(fifoID);
				}
				break;
			case FifoID.TYPE_ACTION_OBJECT:
				throw new UnsupportedOperationException(
						"Object fifos not yet supported");
				// break;
			}

			fifoInterfaces.put(key, fifoIF);
		} else {
			// Rule checking
			if (fifoIF.getWidth() != fifoID.getBitWidth()) {
				throw new IllegalArgumentException(
						"Attempt to redefine fifo interaface "
								+ fifoID.getName() + " width from "
								+ fifoIF.getWidth() + " to "
								+ fifoID.getBitWidth());
			}
		}

		addComponentToDesign(new LinkedHashSet<Component>(fifoIF.getPins()));

		return fifoIF;
	}

	/**
	 * Returns a Collection of the defined FifoIF objects for this design.
	 * 
	 * @return a Collection of {@link FifoIF} objects
	 */
	public Collection<FifoIF> getFifoInterfaces() {
		// Jump through these hoops so that the fifo interfaces come
		// back in the same order each time.
		List<FifoIF> interfaces = new LinkedList<FifoIF>();
		for (Map.Entry<String, FifoIF> entry : fifoInterfaces.entrySet()) {
			interfaces.add(entry.getValue());
		}

		return interfaces;
	}

	/**
	 * @return a list of included source file paths
	 */
	public List<String> getIncludeStatements() {
		return includeStatements;
	}

	/**
	 * Old way of getting the input pins, still used for clock and reset, and
	 * non-blockio pins.
	 * 
	 * @return a value of type 'Collection'
	 * 
	 */
	public Collection<Pin> getInputPins() {
		return inputPins;
	}

	public Collection<LogicalMemory> getLogicalMemories() {
		return Collections.unmodifiableCollection(logicalMemories);
	}

	public int getMaxGateDepth() {
		return maxGateDepth;
	}

	/**
	 * Retrieves the next non-allocated memory ID.
	 */
	public int getNextMemoryId() {
		int id = memoryId;
		memoryId += 1;
		if (id < 0) {
			EngineThread.getEngine().fatalError(
					"Too many memory objects allocated in design");
		}
		if (id == 0) {
			EngineThread.getEngine().fatalError(
					"Memory id 0 is reserved for the null object");
		}
		return id;
	}

	/**
	 * Old way of getting the output pins, still used for done and result, and
	 * non-blockio pins.
	 * 
	 * @return a value of type 'Collection'
	 * 
	 */
	public Collection<Pin> getOutputPins() {
		return outputPins;
	}

	// private List clonePins(Collection pinsToBeCloned, Design clone, Map
	// correlation) throws CloneNotSupportedException
	// {
	// List cloneList = Collections.EMPTY_LIST;

	// if (pinsToBeCloned.size() > 0)
	// {
	// cloneList = new ArrayList(pinsToBeCloned.size());
	// for (Iterator iter = pinsToBeCloned.iterator(); iter.hasNext();)
	// {
	// Pin origPin = (Pin)iter.next();
	// Pin clonePin = (Pin)origPin.clone();
	// for (Iterator origBufIter = origPin.getPinBufs().iterator(),
	// cloneBufIter = clonePin.getPinBufs().iterator();
	// origBufIter.hasNext();)
	// {
	// resetReferent((PinBuf)origBufIter.next(),
	// (PinBuf)cloneBufIter.next());
	// }
	// Object portOrBus = this.pinPortBusMap.get(origPin);
	// if (portOrBus != null)
	// {
	// Object clonePortOrBus = correlation.get(portOrBus);
	// clone.pinPortBusMap.put(clonePortOrBus, clonePin);
	// clone.pinPortBusMap.put(clonePin, clonePortOrBus);
	// }
	// }
	// }

	// return cloneList;
	// }

	// /**
	// * Correlate all ports and buses from the call of each task.
	// */
	// private static void correlate(Task local, Task clone, Map map)
	// {
	// Call localCall = local.getCall();
	// Call cloneCall = clone.getCall();
	// for (Iterator localIter = localCall.getPorts().iterator(),
	// cloneIter = cloneCall.getPorts().iterator(); localIter.hasNext();)
	// {
	// Port lp = (Port)localIter.next();
	// Port cp = (Port)cloneIter.next();
	// map.put(lp, cp);
	// map.put(cp, lp);
	// }
	// for (Iterator localIter = localCall.getBuses().iterator(),
	// cloneIter = cloneCall.getBuses().iterator(); localIter.hasNext();)
	// {
	// Bus lp = (Bus)localIter.next();
	// Bus cp = (Bus)cloneIter.next();
	// map.put(lp, cp);
	// map.put(cp, lp);
	// }
	// }

	// /**
	// * Returns a List of cloned Resources based on the input
	// * collection of Resource objects. This method depends on the
	// * tasks already being cloned so as to populate the clone
	// * correlation maps at each module level.
	// *
	// * @param toBeCloned a Collection of {@link Resource} objects.
	// * @return a List of cloned {@link Resource} objects.
	// */
	// private static List cloneResources(Collection toBeCloned) throws
	// CloneNotSupportedException
	// {
	// List cloneList = Collections.EMPTY_LIST;

	// if (toBeCloned.size() > 0)
	// {
	// cloneList = new ArrayList(toBeCloned.size());

	// for (Iterator iter = toBeCloned.iterator(); iter.hasNext();)
	// {
	// Resource orig = (Resource)iter.next();
	// Resource clone = (Resource)orig.clone();
	// cloneList.add(clone);

	// // Now set up the references to this resource. Call
	// // the 'setReferent' on each Reference
	// resetReferent(orig, clone);
	// }
	// }

	// return cloneList;
	// }

	// private static void resetReferent(Resource orig, Resource clone)
	// {
	// assert false : "FIXME";
	// // for (Iterator refIter = orig.getReferences().iterator();
	// refIter.hasNext();)
	// // {
	// // Reference origRef = (Reference)refIter.next();
	// // assert origRef.getOwner() != null :
	// "Reference cannot exist outside of a module";
	// // assert origRef.getOwner().getCloneCorrelationMap() != null :
	// "The modules must all be cloned first before cloning global resources. Ref: "
	// + origRef + " in " + origRef.getOwner();
	// // assert
	// origRef.getOwner().getCloneCorrelationMap().containsKey(origRef) :
	// "The clone correlation map doesn't contain the resource!";
	// // Reference cloneRef =
	// (Reference)origRef.getOwner().getCloneCorrelationMap().get(origRef);
	// // cloneRef.setReferent(clone);
	// // }
	// }

	// /**
	// * ABK: this is no longer needed because Calls also clone the procedure
	// * which they are calling.
	// * @deprecated
	// */
	// private static class DesignCloneVisitor extends FilteredVisitor
	// {
	// // Map of original procedure -> clone procedure to ensure that
	// // we keep the proper uniqueness (or lack thereof) for each call.
	// private Map clonedProcedures = new HashMap();

	// public void preFilter(Call call)
	// {
	// super.preFilter(call);

	// // Clone the procedure since the cloning of the call by
	// // default points to the _same_ procedure as the original.
	// try
	// {
	// Procedure original = call.getProcedure();
	// Procedure clone;
	// if (clonedProcedures.containsKey(original))
	// {
	// clone = (Procedure)clonedProcedures.get(original);
	// }
	// else
	// {
	// clone = (Procedure)original.clone();
	// }
	// call.setReferent(clone);
	// }
	// catch (CloneNotSupportedException e)
	// {
	// e.printStackTrace();
	// System.exit(1);
	// }
	// }
	// }

	/**
	 * Retrieves the Pin created for the given Port or Bus
	 * 
	 * @param o
	 *            a Port or Bus
	 * @return a value of type 'Pin'
	 */
	public Pin getPin(Object o) {
		return (Pin) pinPortBusMap.get(o);
	}

	public Collection<Pin> getPins() {
		Collection<Pin> pins = new LinkedHashSet<Pin>();
		pins.addAll(getInputPins());
		pins.addAll(getOutputPins());
		// pins.addAll(getBidirectionalPins());
		return pins;
	}

	public Collection<Register> getRegisters() {
		return registers;
	}

	/**
	 * method to return an InputPin representing a reset. If not already
	 * defined, define it first.
	 * 
	 * @param apiResetPin
	 *            api ResetPin corresponding to the requested lim reset pin
	 * @return the InputPin representing resetName calls Job.fatalError() if the
	 *         name is already defined as a clock pin
	 */
	public GlobalReset getResetPin(ResetPin apiResetPin) {
		String name = apiResetPin.getName();
		if (apiClockNameToLIMClockMap.containsKey(name)) {
			EngineThread.getEngine().fatalError(
					"Can't have clock and reset share a pin name: "
							+ apiResetPin);
		}

		GlobalReset resetPin = apiResetNameToLIMResetMap.get(name);
		// if not defined, then define a reset pin & store it
		if (resetPin == null) {
			resetPin = new GlobalReset();
			resetPin.setApiPin(apiResetPin);
			resetPin.setIDLogical(name);
			apiResetNameToLIMResetMap.put(name, resetPin);

			// wire the input of the reset to the output of the paired
			// clock
			ClockPin apiClockPin = apiResetPin.getDomain().getClockPin();
			InputPin clockPin = getClockPin(apiClockPin);
			resetPin.getPort().setBus(clockPin.getBus());
		}
		return resetPin;
	}

	/**
	 * return the LIM reset pins
	 */
	public Collection<GlobalReset> getResetPins() {
		return apiResetNameToLIMResetMap.values();
	}

	public SearchLabel getSearchLabel() {
		// return CodeLabel.UNSCOPED;
		return searchLabel;
	}

	public Collection<Task> getTasks() {
		return taskList;
	}

	/**
	 * @return The {@link Tester} for this <code>Design</code>. From the
	 *         {@link Tester} all the information necessary for generating a
	 *         self verifying automatic test bench can be gleaned. A return
	 *         value of <code>null</code> is valid if this <code>Design</code>
	 *         doesn't have a {@link Tester}.
	 */
	public Tester getTester() {
		return tester;
	}

	public int getUnbreakableGateDepth() {
		return unbreakableGateDepth;
	}

	/**
	 * Adds any 'global' resources, such as Memories, Registers, Pins, etc from
	 * the source design to this design. Note that this is, essentially,
	 * destructive to the source design as any and all accesses to the resources
	 * should (eventually) be moved over to this design.
	 * 
	 * @param source
	 *            a value of type 'Design'
	 */
	public void mergeResources(Design source) {
		/*
		 * These things do not have to be merged: sharedProcedures - we dont
		 * have any yet! kickers - we aren't bringing over tasks, so we dont
		 * need kickers flipflopschain - again, no tasks brought over
		 * apiClockNameToLIMClockMap - merging should be done pre-clock pins
		 * apiResetNameToLIMResetMap - ditto pinPortBusMap - ditto
		 */
		assert apiClockNameToLIMClockMap.isEmpty();
		assert apiResetNameToLIMResetMap.isEmpty();
		assert pinPortBusMap.isEmpty();

		Module sourceMod = source.getDesignModule();
		Set<Component> sourceComps = new HashSet<Component>(
				sourceMod.getComponents());
		// We want the top level infrastructure, but NOT the entry
		// methods
		for (Task task : source.getTasks()) {
			sourceComps.remove(task.getCall());
		}
		// this.addComponentToDesign(source.getDesignModule().getComponents());
		this.addComponentToDesign(sourceComps);

		// registers
		for (Register reg : source.getRegisters()) {
			addRegister(reg);
		}
		// // memories
		// for (Iterator iter = source.getMemories().iterator();
		// iter.hasNext();)
		// {
		// addMemory((Memory)iter.next());
		// }
		// logicalMemories
		for (LogicalMemory logicalMemory : source.getLogicalMemories()) {
			addMemory(logicalMemory);
		}
		// inputPins
		for (Pin pin : source.getInputPins()) {
			addInputPin((InputPin) pin);
		}
		// outputPins
		for (Pin pin : source.getOutputPins()) {
			addOutputPin((OutputPin) pin);
		}
		// bidirectionalPins
		// for (Iterator iter = source.getBidirectionalPins().iterator();
		// iter.hasNext();)
		// {
		// addBidirectionalPin((BidirectionalPin)iter.next());
		// }
		// include statements
		for (String string : source.getIncludeStatements()) {
			addIncludeStatement(string);
		}
	}

	/**
	 * Removes the given {@link LogicalMemory} from this design
	 */
	public void removeMemory(LogicalMemory mem) {
		logicalMemories.remove(mem);
		if (logicalMemories.size() == 0) {
			logicalMemories = Collections.emptyList();
		}
	}

	// ///////////////////
	//
	// Implementation of a design module to contain the resources.
	//
	// ///////////////////

	/**
	 * Removes the specified register from this design.
	 * 
	 * @param register
	 *            true if removed, false if not found
	 */
	public boolean removeRegister(Register register) {
		return registers.remove(register);
	}

	/**
	 * Restore Core, Entry, PinSim
	 * 
	 */
	public void restoreAPIContext() {
		// ipcore - see saveAPIContext comments above.

		// entry
		EntryMethods.setEntryMethods(entryData);

		// pinsim data
		PinSimData.setDriveData(pinSimDriveMap);
		PinSimData.setTestData(pinSimTestMap);
	}

	/**
	 * This will save off the static info in forge.api.ipcore.Core,
	 * forge.api.entry.Entry, and forge.api.sim.pin.PinSimData
	 * 
	 */
	public void saveAPIContext() {
		// Don't clear the IPCore map since it keeps unique
		// ipcore references and when bubbling up replaced operations
		// that use IPCores, what they put in the map needs to not get
		// cleared out. This puts the requirement on the translator
		// to only call the HDLWriters for IPCores that are included
		// in the design.

		// entry
		entryData = EntryMethods.cloneEntryMethods();

		// pinsim
		pinSimDriveMap = PinSimData.cloneDriveMap();
		pinSimTestMap = PinSimData.cloneTestMap();
	}

	public void setMaxGateDepth(int maxGateDepth) {
		this.maxGateDepth = maxGateDepth;
	}

	/**
	 * Sets the {@link Tester} for this <code>Design</code>.
	 * 
	 * @param tester
	 *            the <code>Tester</code> that can generate arguments and
	 *            expected results for this <code>Design</code>
	 */
	public void setTester(Tester tester) {
		this.tester = tester;
	}

	public void setUnbreakableGateDepth(int unbreakableGateDepth) {
		this.unbreakableGateDepth = unbreakableGateDepth;
	}

}
