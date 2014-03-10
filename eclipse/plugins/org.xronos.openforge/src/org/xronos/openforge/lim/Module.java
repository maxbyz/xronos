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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xronos.openforge.app.project.SearchLabel;
import org.xronos.openforge.lim.Exit.Tag;

/**
 * A Module is a {@link Component} that can be a container for other
 * {@link Component Components}.
 * 
 * @version $Id: Module.java 188 2006-07-05 20:13:17Z imiller $
 */
public abstract class Module extends Component implements Cloneable {

	private class BlockSearchLabel implements SearchLabel {
		private String localLabel;

		private BlockSearchLabel(String local) {
			localLabel = local;
		}

		@Override
		public String getLabel() {
			final SearchLabel searchLabel = getSearchLabel();
			return searchLabel.getLabel();
		}

		@Override
		public List<String> getSearchList() {
			final SearchLabel searchLabel = Module.super.getSearchLabel();
			List<String> searchList = searchLabel.getSearchList();
			List<String> modified = new ArrayList<String>();
			modified.add(searchList.get(0) + "." + localLabel);
			modified.addAll(searchList);
			return modified;
		}

		@Override
		public List<String> getSearchList(String postfix) {
			final SearchLabel searchLabel = Module.super.getSearchLabel();
			List<String> searchList = searchLabel.getSearchList(postfix);
			List<String> modified = new ArrayList<String>();
			modified.add(searchList.get(0) + "." + localLabel);
			modified.addAll(searchList);
			return modified;
		}
	}

	/**
	 * Shortcut for adding dependencies to the clock, reset, and go of a child
	 * component.
	 * 
	 * @param entry
	 *            the entry in which the dependencies are added; the
	 *            {@link Component} is obtained with {@link Entry#getOwner()}
	 * @param clockBus
	 *            the bus for which a {@link ClockDependency} will be added for
	 *            the component's clock {@link Port}
	 * @param resetBus
	 *            the bus for which a {@link ResetDependency} will be added for
	 *            the component's reset {@link Port}
	 * @param goBus
	 *            the bus for which a {@link ControlDependency} will be added
	 *            for the component's go {@link Port}
	 */
	protected static void addDependencies(Entry entry, Bus clockBus,
			Bus resetBus, Bus goBus) {
		Component component = entry.getOwner();
		entry.addDependency(component.getClockPort(), new ClockDependency(
				clockBus));
		entry.addDependency(component.getResetPort(), new ResetDependency(
				resetBus));
		entry.addDependency(component.getGoPort(), new ControlDependency(goBus));
	}

	/**
	 * Adds a component's Exits to a map by tag.
	 * 
	 * @param component
	 *            the component whose exits are to be added
	 * @param exitMap
	 *            a map of {@link Exit.Tag} to {@link Collection} of
	 *            {@link Exit}
	 */
	protected static void collectExits(Component component,
			Map<Exit.Tag, Collection<Exit>> exitMap) {
		for (Exit exit : component.getExits()) {
			Collection<Exit> list = exitMap.get(exit.getTag());
			if (list == null) {
				list = new LinkedList<Exit>();
				exitMap.put(exit.getTag(), list);
			}
			list.add(exit);
		}
	}

	/**
	 * Finds the clone of an {@link Entry}.
	 * 
	 * @param entry
	 *            an original entry in the clone map
	 * @param cloneMap
	 *            a map of original components to cloned components and entries
	 */
	protected static Entry getEntryClone(Entry entry,
			Map<Component, Entry> cloneMap) {
		return cloneMap.get(entry);
	}

	/**
	 * Finds the clone of a {@link Port}.
	 * 
	 * @param port
	 *            the port from an original component in the clone map
	 * @param cloneMap
	 *            a map of original components to cloned components
	 */
	protected static Port getPortClone(Port port,
			Map<Component, Component> cloneMap) {
		final Component component = port.getOwner();
		final Component componentClone = cloneMap.get(component);
		if (port == component.getClockPort()) {
			return componentClone.getClockPort();
		} else if (port == component.getResetPort()) {
			return componentClone.getResetPort();
		} else if (port == component.getGoPort()) {
			return componentClone.getGoPort();
		} else {
			final int index = component.getDataPorts().indexOf(port);
			return componentClone.getDataPorts().get(index);
		}
	}

	/**
	 * The component which provides continuation buses for module ports
	 */
	private InBuf inBuf;

	/** Collection of Component */
	//private Collection<Component> components = new ArrayList<Component>();
	private Collection<Component> components;// = new HashSet<Component>();

	/** True iff this module needs a go signal */
	private boolean consumesGo = false;

	/** True iff the exits of this module produce done signals */
	private boolean producesDone = false;

	/** True iff the done signals are synchronous with respect to clock and go */
	private boolean isDoneSynchronous = false;

	/** True iff this module needs a clock signal */
	private boolean consumesClock = false;

	/** True iff this module needs a reset signal */
	private boolean consumesReset = false;

	/** A Set of components which can break feedback paths. */
	private Set<Component> feedbackPoints;// = Collections.emptySet();

	/**
	 * The label used for OptionDB look-ups. May be null if no search scope has
	 * been specifically set for this module
	 */
	private BlockSearchLabel odbLabel = null;

	/**
	 * Creates a module with no data ports.
	 */
	public Module() {
		this(0);
	}

	/**
	 * Constructs a new Module.
	 * 
	 * @param dataCount
	 *            the number of data ports to be created initially for the
	 *            module; a supporting InBuf will also be created and added to
	 *            the module
	 */
	public Module(int dataCount) {
		super(dataCount);
		assert !isConstructed();
		this.components = new HashSet<Component>();
		//this.components = new ArrayList<Component>();
		this.feedbackPoints = Collections.emptySet();
		createInBuf();
	}

	/**
	 * Adds a component to this Module. This will also call the component's
	 * {@link Component#setOwner(Module)} method with this object as the
	 * argument.
	 * 
	 * @param component
	 *            the {@link Component Component} to be added
	 */
	public void addComponent(Component component) {
		components.add(component);
		component.setOwner(this);
	}

	/**
	 * Adds a collection of components to this Module. This will also call each
	 * component's {@link Component#setOwner(Module)} method with this object as
	 * the argument.
	 * 
	 * @param components
	 *            the {@link Component Components} to be added
	 */
	public void addComponents(Collection<Component> components) {
		for (Component component : components) {
			addComponent(component);
		}
	}

	public void addFeedbackPoint(Component comp) {
		if (feedbackPoints == Collections.EMPTY_SET) {
			feedbackPoints = new HashSet<Component>(3);
		}
		feedbackPoints.add(comp);
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		final Module clone = (Module) super.clone();

		assert !clone.isConstructed();

		clone.components = new HashSet<Component>();
		if (odbLabel != null) {
			clone.odbLabel = new BlockSearchLabel(odbLabel.localLabel);
		}

		/* Map of original Component to cloned Component. */
		final Map<Component, Component> cloneMap = new HashMap<Component, Component>();
		cloneMap.put(this, clone);

		/*
		 * The clone needs its own InBuf.
		 */
		clone.createInBuf();
		cloneMap.put(getInBuf(), clone.getInBuf());

		for (Exit exit : getInBuf().getExits()) {
			final Exit exitClone = clone.getInBuf().getExit(exit.getTag());
			exitClone.copyAttributes(exit);
		}

		/*
		 * Clone all components except the InBuf and OutBufs.
		 */
		final Set<Component> bufs = new HashSet<Component>();
		bufs.add(getInBuf());
		bufs.addAll(getOutBufs());
		for (Component component : components) {
			if (!bufs.contains(component)) {
				Component componentClone = (Component) component.clone();
				clone.addComponent(componentClone);
				cloneMap.put(component, componentClone);
			}
		}

		/*
		 * convert the clones feedback points map to the right set of components
		 */
		clone.feedbackPoints = Collections.emptySet();
		for (Component component : feedbackPoints) {
			clone.addFeedbackPoint(cloneMap.get(component));
		}

		/*
		 * Duplicate the Exits and OutBufs.
		 */
		for (Exit exit : getExits()) {
			final Exit exitClone = cloneExit(clone, exit);

			final Component outbuf = exit.getPeer();
			final Component outbufClone = exitClone.getPeer();
			cloneMap.put(outbuf, outbufClone);

			outbufClone.getClockPort().copyAttributes(outbuf.getClockPort());
			outbufClone.getResetPort().copyAttributes(outbuf.getResetPort());
			outbufClone.getGoPort().copyAttributes(outbuf.getGoPort());
			final Iterator<Port> piter = outbuf.getDataPorts().iterator();
			final Iterator<Port> pciter = outbufClone.getDataPorts().iterator();
			while (pciter.hasNext()) {
				pciter.next().copyAttributes(piter.next());
			}
		}

		cloneConnections(clone, cloneMap);
		cloneNotify(clone, cloneMap);
		super.notifyCloneListeners(cloneMap);

		return clone;
	}

	/**
	 * Clones the {@link Dependency Dependencies} and {@link Bus} connections
	 * between the {@link Component Components} of a cloned {@link Module}.
	 * 
	 * @param clone
	 *            the clone of this module
	 * @param cloneMap
	 *            the map of original components to cloned components; this
	 *            method will add the mapping from original entries to cloned
	 *            entries
	 */
	private void cloneConnections(Module clone,
			Map<Component, Component> cloneMap) {
		for (Component component : components) {
			final Component componentClone = cloneMap.get(component);

			/*
			 * Connect Entries and their Dependencies.
			 */
			for (Entry entry : component.getEntries()) {
				final Exit drivingExit = entry.getDrivingExit();
				final Exit drivingExitClone = drivingExit == null ? null
						: getExitClone(drivingExit, cloneMap);
				final Entry entryClone = componentClone
						.makeEntry(drivingExitClone);
				// cloneMap.put(entry, entryClone);

				for (Port port : entry.getPorts()) {
					final Port portClone = getPortClone(port, cloneMap);

					for (Dependency dependency : entry.getDependencies(port)) {
						final Bus logicalBus = dependency.getLogicalBus();

						final Dependency dependencyClone = (Dependency) dependency
								.clone();
						final Bus logicalBusClone = getBusClone(logicalBus,
								cloneMap);
						dependencyClone.setLogicalBus(logicalBusClone);

						entryClone.addDependency(portClone, dependencyClone);
					}
				}
			}

			/*
			 * Set Port values and buses.
			 */
			for (Port port : component.getPorts()) {
				final Port portClone = getPortClone(port, cloneMap);
				if (port.isConnected()) {
					portClone.setBus(getBusClone(port.getBus(), cloneMap));
				}
			}
		}
	}

	@Override
	protected void cloneExit(Component clone, Exit exit) {
		/*
		 * Prevents cloning of Exits until super.clone() returns.
		 */
	}

	protected Exit cloneExit(Module clone, Exit exit) {
		final Exit cloneExit = clone.makeExit(exit.getDataBuses().size(), exit
				.getTag().getType(), exit.getTag().getLabel());
		cloneExit.copyAttributes(exit);
		return cloneExit;
	}

	/**
	 * Overridden by subclasses to respond to the completion of cloning. This
	 * gives the subclass a chance to set any additional fields of the clone.
	 * 
	 * @param clone
	 *            the clone of this module
	 * @param cloneMap
	 *            a map of each original {@link Component} and {@link Component}
	 *            to to its clone
	 */
	protected void cloneNotify(Module clone, Map<Component, Component> cloneMap) {
	}

	/**
	 * Tests whether this component requires a connection to its clock
	 * {@link Port}.
	 */
	@Override
	public boolean consumesClock() {
		return consumesClock;
	}

	/**
	 * Tests whether this component requires a connection to its <em>go</em>
	 * {@link Port} in order to commence processing.
	 */
	@Override
	public boolean consumesGo() {
		return consumesGo;
	}

	/**
	 * Tests whether this component requires a connection to its reset
	 * {@link Port}. By default, returns the value of
	 * {@link Component#consumesClock()}.
	 */
	@Override
	public boolean consumesReset() {
		return consumesReset;
	}

	/**
	 * Tests whether a given component is contained by this Module. Components
	 * are not recursively queried.
	 * 
	 * @param component
	 *            the component to test
	 * @return true if the component is contained by this module, false
	 *         otherwise
	 */
	public boolean contains(Component component) {
		return getComponents().contains(component);
	}

	/**
	 * Constructs and returns a new {@link Exit} for this component, but does no
	 * other modification of this component's state. Subclasses may override to
	 * specialize the behavior of their exits.
	 * 
	 * @param dataCount
	 *            the number of data {@link Bus Buses} on the exit
	 * @param type
	 *            type type of the exit
	 * @param label
	 *            the lable of the exit
	 * @return the new exit, which will be added to the list for this component
	 */
	@Override
	protected Exit createExit(int dataCount, Exit.Type type, String label) {
		// MUST override the super because the constructor for the
		// Exit differentiates between a Component and Module.
		return new Exit(this, dataCount, type, label);
	}

	/**
	 * Creates the {@link InBuf} with a data {@link Bus} for each data
	 * {@link Port} on this Module.
	 */
	private void createInBuf() {
		// final int dataCount = getDataPorts().size();

		/*
		 * Create an InBuf and map all the module's ports to its buses.
		 */
		inBuf = new InBuf(0/* dataCount */);
		addComponent(inBuf);

		getClockPort().setPeer(inBuf.getClockBus());
		inBuf.getClockBus().setPeer(getClockPort());

		getResetPort().setPeer(inBuf.getResetBus());
		inBuf.getResetBus().setPeer(getResetPort());

		getGoPort().setPeer(inBuf.getGoBus());
		inBuf.getGoBus().setPeer(getGoPort());

		Iterator<Port> portIter = getDataPorts().iterator();
		if (getThisPort() != null) {
			Port thisPort = portIter.next();
			Bus thisBus = inBuf.makeThisBus();
			thisPort.setPeer(thisBus);
			thisBus.setPeer(thisPort);
		}

		while (portIter.hasNext()) {
			Port port = portIter.next();
			Bus bus = inBuf.getExit(Exit.DONE).makeDataBus();
			port.setPeer(bus);
			bus.setPeer(port);
		}
	}

	/**
	 * Gets the resources accessed by or within this component.
	 * 
	 * @return a collection of {@link Resource}
	 */
	@Override
	public Collection<Resource> getAccessedResources() {
		final Set<Resource> set = new HashSet<Resource>();
		for (Component component : getComponents()) {
			set.addAll(component.getAccessedResources());
		}
		return set;
	}

	/**
	 * Gets the components contained by this Module.
	 * 
	 * @return a collection of {@link Component Component}s
	 */
	public Collection<Component> getComponents() {
		return components;
	}

	/**
	 * Returns a Set of {@link Component Components} that represent the points
	 * in this Module that are feedback points within this module, it does not
	 * report on any feedback contained within any sub-modules.
	 * 
	 * @return the set of identified feedback points.
	 */
	public Set<Component> getFeedbackPoints() {
		return Collections.unmodifiableSet(feedbackPoints);
	}

	/**
	 * Gets the InBuf of this module.
	 * 
	 * @return the InBuf for this module
	 */
	public InBuf getInBuf() {
		return inBuf;
	}

	/**
	 * Gets the OutBufs of this module.
	 * 
	 * @return a List of OutBufs, one for each bus of this module
	 */
	public Collection<OutBuf> getOutBufs() {
		List<OutBuf> list = new ArrayList<OutBuf>(getExits().size());
		for (Exit exit : getExits()) {
			list.add(exit.getPeer());
		}
		return list;
	}

	/**
	 * Returns the string label associated with this Configurable.
	 * 
	 */
	@Override
	public SearchLabel getSearchLabel() {
		if (odbLabel != null) {
			return odbLabel;
		}
		return super.getSearchLabel();
	}

	/**
	 * Tests whether or not the timing of this component can be balanced during
	 * scheduling. That is, can all of the execution paths through the component
	 * be made to complete in the same number of clocks. Note that this property
	 * is based only upon the type of this component and any components that it
	 * may contain.
	 * 
	 * @return true if all components in the module are balanceable.
	 */
	@Override
	public boolean isBalanceable() {
		for (Component component : getComponents()) {
			if (!component.isBalanceable()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns true if this module has been fully constructed or false if only
	 * the superclass constructor or clone has been called. Actually tests
	 * whether the {@link InBuf} exists and is owned by this module. If false,
	 * {@link Module#makeDataPort()} will default to
	 * {@link Component#makeDataPort()} and no {@link InBuf} data buses will be
	 * created.
	 * 
	 * @see Module#createInBuf()
	 */
	private boolean isConstructed() {
		final InBuf inbuf = getInBuf();
		return inbuf != null && inbuf.getOwner() == this;
	}

	/**
	 * Tests whether the done signal, if any, produced by this module (see
	 * {@link Module#producesDone()}) is synchronous or not. A true value means
	 * that the done signal will be produced with the clock and no earlier than
	 * the go signal is asserted.
	 * 
	 * @see Module#producesDone()
	 */
	@Override
	public boolean isDoneSynchronous() {
		return isDoneSynchronous;
	}

	/**
	 * Returns true if the component in this Module are mutually exclusive in
	 * their execution and thus do not need any additional dependencies beyond
	 * standard data/control dependencies. {@see MutexBlock}
	 * 
	 * @return false
	 */
	public boolean isMutexModule() {
		return false;
	}

	/**
	 * Tests whether this component is opaque. If true, then this component is
	 * to be treated as a self-contained entity. This means that its internal
	 * definition can make no direct references to external entitities. In
	 * particular, external {@link Bit Bits} are not pushed into this component
	 * during constant propagation, nor are any of its internal {@link Bit Bits}
	 * propagated to its external {@link Bus Buses}.
	 * <P>
	 * Typically this implies that the translator will either generate a
	 * primitive definition or an instantiatable module for this component.
	 * 
	 * @return true if this component is opaque, false otherwise
	 */
	@Override
	public boolean isOpaque() {
		/*
		 * By default, any Module that is not owned by another Module will be
		 * translated as an instantiatable module (e.g., a Verilog 'module').
		 */
		return getOwner() == null;
	}

	/**
	 * make a new data port for this module, which is tagged - ie for global
	 * access
	 * 
	 * @param type
	 *            type of port - usually Component.SIDEBAND
	 * 
	 * @return the new port
	 */
	@Override
	public Port makeDataPort(Component.Type tag) {
		Port port = super.makeDataPort(tag);
		if (isConstructed()) {
			Bus bus = inBuf.getExit(Exit.DONE).makeDataBus(tag);
			port.setPeer(bus);
			bus.setPeer(port);
		}
		return port;
	}

	/**
	 * Makes a new {@link Exit} for this module. Each {@link Exit} in a
	 * Component must have a unique {@link Exit.Type Type} and label String
	 * pair.
	 * 
	 * @param dataCount
	 *            the number of data {@link Bus Buses} on the exit
	 * @param type
	 *            type type of the exit
	 * @param label
	 *            the lable of the exit
	 */
	@Override
	public Exit makeExit(int dataCount, Exit.Type type, String label) {
		// Exit exit = new Exit(this, dataCount, type, label);
		Exit exit = createExit(dataCount, type, label);
		assert exits.get(exit.getTag()) == null : "Duplicate Exit type: "
				+ exit.getTag();
		exits.put(exit.getTag(), exit);

		/*
		 * Make sure the OutBuf has the correct value for consumesGo().
		 */
		exit.getPeer().setConsumesGo(producesDone());

		return exit;
	}

	@Override
	public Port makeThisPort() {
		Port port = super.makeThisPort();
		if (isConstructed()) {
			Bus bus = inBuf.makeThisBus();
			port.setPeer(bus);
			bus.setPeer(port);
		}
		return port;
	}

	/**
	 * Creates the {@link Exit Exits} of this module based upon the {@link Exit
	 * Exits} of its components. For each {@link Exit.Tag Tag}, an {@link Exit}
	 * is created on this module; the peer of that {@link Exit} is then
	 * decorated with an {@link Entry} and a {@link ControlDependency} for each
	 * child {@link Exit} with that same {@link Exit.Tag Tag}.
	 */
	protected void mergeExits() {
		mergeExits(getComponents());
	}

	/**
	 * Creates the {@link Exit Exits} of this module based upon the {@link Exit
	 * Exits} of a given set of child components. For each {@link Exit.Tag Tag},
	 * an {@link Exit} is created on this module; the peer of that {@link Exit}
	 * is then decorated with an {@link Entry} and a {@link ControlDependency}
	 * for each child {@link Exit} with that same {@link Exit.Tag Tag}.
	 * 
	 * @param components
	 *            a collection of child {@link Component Components}
	 */
	protected void mergeExits(Collection<Component> components) {
		Map<Tag, Collection<Exit>> exitMap = new LinkedHashMap<Tag, Collection<Exit>>(
				components.size());
		for (Component component : components) {
			collectExits(component, exitMap);
		}
		mergeExits(exitMap, getInBuf().getClockBus(), getInBuf().getResetBus());
	}

	/**
	 * Creates the {@link Exit Exits} of a this Module based upon the
	 * {@link Exit Exits} of its components. For each {@link Exit.Tag Tag}, an
	 * {@link Exit} is created on the {@link Module}; the peer of that
	 * {@link Exit} is then decorated with an {@link Entry} and a
	 * {@link ControlDependency} for each child {@link Exit} with that same
	 * {@link Exit.Tag Tag}.
	 * 
	 * @param exitMap
	 *            a map of exit tag to collection of child exits with that tag
	 *            for all relevant components within the module
	 * @param clockBus
	 *            the clock bus for each new exit
	 * @param resetBus
	 *            the reset bus for each new exit
	 */
	protected void mergeExits(Map<Exit.Tag, Collection<Exit>> exitMap,
			Bus clockBus, Bus resetBus) {
		for (Exit.Tag tag : exitMap.keySet()) {
			Exit moduleExit = getExit(tag.getType(), tag.getLabel());
			if (moduleExit == null) {
				moduleExit = makeExit(0, tag.getType(), tag.getLabel());
			}
			final Component moduleOut = moduleExit.getPeer();

			final Collection<Exit> exitList = exitMap.get(tag);
			for (Exit exit : exitList) {
				final Entry outEntry = moduleOut.makeEntry(exit);
				addDependencies(outEntry, clockBus, resetBus, exit.getDoneBus());
			}
		}
	}

	/*
	 * 
	 * Support for cloning
	 */

	/**
	 * Tests whether this component produces a signal on the done {@link Bus} of
	 * each of its {@link Exit Exits}.
	 */
	@Override
	public boolean producesDone() {
		return producesDone;
	}

	/**
	 * Removes a component from this Module. This will also call the component's
	 * {@link Component#setOwner(Module)} method with a null argument.
	 * 
	 * @param component
	 *            the {@link Component Component} to be removed
	 * @return true if the component was removed, false if not found
	 */
	public boolean removeComponent(Component component) {
		component.disconnect();
		if (components.remove(component)) {
			component.setOwner(null);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Removes a given {@link Exit} and its peer {@link Component}.
	 */
	@Override
	public void removeExit(Exit exit) {
		super.removeExit(exit);
		removeComponent(exit.getPeer());
	}

	/**
	 * Performs a module specific replacement of the given component. Returns
	 * true if the 'removed' Component was removed and the 'inserted' component
	 * was added to this module.
	 */
	public abstract boolean replaceComponent(Component removed,
			Component inserted);

	/**
	 * Sets whether this component requires a connection to its clock
	 * {@link Port}.
	 */
	public void setConsumesClock(boolean consumesClock) {
		this.consumesClock = consumesClock;
	}

	/**
	 * Sets whether this component requires a connection to its <em>go</em>
	 * {@link Port} in order to commence processing.
	 */
	public void setConsumesGo(boolean consumesGo) {
		this.consumesGo = consumesGo;
		getInBuf().setProducesDone(consumesGo);
	}

	/**
	 * Sets whether this component requires a connection to its reset
	 * {@link Port}. By default, returns the value of
	 * {@link Component#consumesClock()}.
	 */
	public void setConsumesReset(boolean consumesReset) {
		this.consumesReset = consumesReset;
	}

	/**
	 * Sets whether the done signal, if any, produced by this module (see
	 * {@link Module#producesDone()}) is synchronous or not. A true value means
	 * that the done signal will be produced with the clock and no earlier than
	 * the go signal is asserted.
	 * 
	 * @see Module#producesDone()
	 */
	public void setDoneSynchronous(boolean isDoneSynchronous) {
		this.isDoneSynchronous = isDoneSynchronous;
	}

	/**
	 * Sets whether this component produces a signal on the done {@link Bus} of
	 * each of its {@link Exit Exits}.
	 */
	public void setProducesDone(boolean producesDone) {
		this.producesDone = producesDone;
		for (OutBuf outBuf : getOutBufs()) {
			outBuf.setConsumesGo(producesDone);
		}
	}

	public void specifySearchScope(String label) {
		odbLabel = new BlockSearchLabel(label);
	}

	@Override
	public String toString() {
		String dataPorts = "";
		String dataBuses = "";

		for (Port port : getDataPorts()) {
			String portName = port.showIDLogical();

			if (dataPorts.equals("")) {
				dataPorts = portName;
			} else {
				dataPorts = dataPorts + "," + portName;
			}

		}

		for (Bus bus : getDataBuses()) {
			String busName = bus.showIDLogical();
			if (dataBuses.equals("")) {
				dataBuses = busName;
			} else {
				dataBuses = dataBuses + "," + busName;
			}
		}

		return this.getIDGlobalType() + "([" + dataPorts + "],[" + dataBuses
				+ "])";
	}

} // class Module

