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
package org.xronos.openforge.lim.memory;

import java.util.HashSet;
import java.util.Set;

import org.xronos.openforge.lim.Bus;
import org.xronos.openforge.lim.Component;
import org.xronos.openforge.lim.Exit;
import org.xronos.openforge.lim.Latency;
import org.xronos.openforge.lim.Module;
import org.xronos.openforge.lim.PhysicalImplementationModule;
import org.xronos.openforge.lim.Port;
import org.xronos.openforge.lim.Referenceable;
import org.xronos.openforge.lim.StateAccessor;
import org.xronos.openforge.lim.StateHolder;
import org.xronos.openforge.lim.Value;
import org.xronos.openforge.lim.Visitor;
import org.xronos.openforge.lim.op.CastOp;
import org.xronos.openforge.lim.primitive.And;
import org.xronos.openforge.lim.primitive.Or;
import org.xronos.openforge.lim.primitive.Reg;
import org.xronos.openforge.util.naming.ID;

/**
 * A {@link MemoryPort} read access.
 * <P>
 * 
 * @author Stephen Edwards
 * @version $Id: MemoryRead.java 280 2006-08-11 17:00:32Z imiller $
 */
public class MemoryRead extends MemoryAccess implements StateAccessor {

	/**
	 * This class implements the logic and connectivity for the 'done caching'
	 * logic in the read. It is basically a register whose set and reset pins
	 * are manipulated (Q feeds back to D). The incoming GO set's the register.
	 * The incoming done (from the memory) is ANDed with the register output to
	 * produce the done for this read. The output of the AND is also used to
	 * clear the register by being ORed with the global RESET.
	 * 
	 * This class is for reads which take 1 or more cycle.
	 */
	public class DoneCache {
		And doneAnd = null;
		Or resetOr = null;
		Reg goRegister = null;

		protected DoneCache() {
		}

		public DoneCache(Module owner) {
			// create internal done-caching logic
			// this.goRegister = new Reg(Reg.REGRS, null);
			goRegister = Reg.getConfigurableReg(Reg.REGRS, "done_cache");
			// goRegister.useSetPort();
			// goRegister.useInternalResetPort();
			final Port goRegIn = goRegister.getDataPort();
			final Bus goRegOut = goRegister.getResultBus();
			goRegOut.setSize(1, false);
			owner.addComponent(goRegister);

			doneAnd = new And(2);
			owner.addComponent(doneAnd);

			resetOr = new Or(2);
			owner.addComponent(resetOr);

			// now wire everything up

			resetOr.getDataPorts().get(0).setBus(doneAnd.getResultBus());

			/*
			 * XXX: It seems the internal reset is what's intended, but just to
			 * be safe, connect the regular reset as well.
			 */
			goRegister.getInternalResetPort().setBus(resetOr.getResultBus());
			goRegister.getResetPort().setBus(resetOr.getResultBus());

			goRegIn.setBus(goRegOut);

			doneAnd.getDataPorts().get(0).setBus(goRegOut);
		}

		public void connectClock(Bus clock) {
			goRegister.getClockPort().setBus(clock);
		}

		public void connectComponentDone(Port done) {
			done.setBus(doneAnd.getResultBus());
		}

		public void connectComponentGo(Bus goBus) {
			goRegister.getSetPort().setBus(goBus);
		}

		public void connectMemoryDone(Bus memDone) {
			doneAnd.getDataPorts().get(1).setBus(memDone);
		}

		public void connectReset(Bus resetBus) {
			resetOr.getDataPorts().get(1).setBus(resetBus);
		}
	}

	/**
	 * The full physical implementation of a MemoryRead. Physical provides
	 * explicit internal connections for ports and buses, and extra logic to
	 * trap the GO signal so that it can be paired with the returning DONE
	 * signal.
	 * <P>
	 * <img src="doc-files/MemoryRead.png">
	 */
	public class Physical extends PhysicalImplementationModule {
		Port sideDataReady;
		Port sideDataIn;
		Bus sideAddress;
		Bus sideEnable;
		Bus sideSize;

		private Reg goRegister;

		/**
		 * Constructs a new Physical which appropriates all the port-bus
		 * connections of the MemoryRead.
		 */
		public Physical() {
			super(0);

			// one normal port for the address
			Port addressIn = makeDataPort();
			Port memReadAddress = getAddressPort();
			assert memReadAddress.getBus() != null : "MemoryRead's address port not attached to a bus.";
			assert memReadAddress.getBus().getValue() != null : "MemoryRead address port has no value";
			addressIn.setUsed(memReadAddress.isUsed());
			addressIn.setBus(memReadAddress.getBus());

			// and another normal port for the size input.
			Port sizeIn = makeDataPort();
			sizeIn.setUsed(getSizePort().isUsed());
			sizeIn.setBus(getSizePort().getBus());

			// appropriate the go signal
			Port memReadGo = MemoryRead.this.getGoPort();
			Port go = getGoPort();
			assert memReadGo.getBus() != null : "MemoryRead's go port not attached to a bus.";
			go.setUsed(memReadGo.isUsed());
			go.setBus(memReadGo.getBus());

			// appropriate the clock signal
			Port memReadClock = MemoryRead.this.getClockPort();
			Port clk = getClockPort();
			assert memReadClock.getBus() != null : "MemoryRead's clock port not attached to a bus.";
			clk.setUsed(memReadClock.isUsed());
			clk.setBus(memReadClock.getBus());

			// appropriate the reset signal
			Port memReadReset = MemoryRead.this.getResetPort();
			Port reset = getResetPort();
			assert memReadReset.getBus() != null : "MemoryRead's reset port not attached to a bus.";
			reset.setUsed(memReadReset.isUsed());
			reset.setBus(memReadReset.getBus());

			// appropriate the data out bus
			Exit physicalExit = makeExit(0);
			Bus dataOut = physicalExit.makeDataBus();
			Bus memReadData = MemoryRead.this.getExit(Exit.DONE).getDataBuses()
					.get(0);
			final int dataWidth = memReadData.getValue().getSize();
			dataOut.setUsed(memReadData.isUsed());
			dataOut.setIDLogical(ID.showLogical(memReadData));
			dataOut.setSize(dataWidth, memReadData.getValue().isSigned());
			for (Port consumer : memReadData.getPorts()) {
				consumer.setBus(dataOut);
			}

			// appropriate the done bus
			Bus done = physicalExit.getDoneBus();
			Bus memReadDone = MemoryRead.this.getExit(Exit.DONE).getDoneBus();
			done.setUsed(memReadDone.isUsed());
			done.setIDLogical(ID.showLogical(memReadDone));
			for (Port consumer : memReadDone.getPorts()) {
				consumer.setBus(done);
			}

			sideDataReady = makeDataPort(Component.SIDEBAND);
			sideDataIn = makeDataPort(Component.SIDEBAND);
			sideAddress = physicalExit.makeDataBus(Component.SIDEBAND);
			sideAddress.setIDLogical(ID.showLogical(MemoryRead.this) + "_RA");
			sideAddress.setSize(memReadAddress.getBus().getSize(),
					memReadAddress.getBus().getValue().isSigned());
			sideAddress.getPeer().setBus(addressIn.getPeer());

			sideEnable = physicalExit.makeDataBus(Component.SIDEBAND);
			sideEnable.setIDLogical(ID.showLogical(MemoryRead.this) + "_RE");
			sideEnable.setSize(1, false);
			sideEnable.getPeer().setBus(go.getPeer());

			sideSize = physicalExit.makeDataBus(Component.SIDEBAND);
			sideSize.setIDLogical(ID.showLogical(MemoryRead.this) + "_RS");
			sideSize.setSize(getSizePort().getBus().getSize(), getSizePort()
					.getBus().getValue().isSigned());
			sideSize.getPeer().setBus(sizeIn.getPeer());

			/*
			 * Cast the data returned by the memory to the requested size. This
			 * is because the side data bus connected to the memory is initially
			 * sized for the largest possible data value.
			 */
			final CastOp castOp = new CastOp(dataOut.getValue().getSize(),
					dataOut.getValue().isSigned());
			addComponent(castOp);
			castOp.getDataPort().setBus(sideDataIn.getPeer());
			dataOut.getPeer().setBus(castOp.getResultBus());

			// create internal done-caching logic
			final DoneCache dc;
			final Latency lat = MemoryRead.this.getLatency();
			if (lat == Latency.ZERO) {
				dc = new ZeroDoneCache(this);
			} else {
				if (lat.getMinClocks() > 0) {
					dc = new DoneCache(this);
				} else {
					dc = new ZeroPlusDoneCache(this);
				}
			}
			// For feedback handling in the data flow visitor.
			goRegister = dc.goRegister;

			dc.connectReset(reset.getPeer());
			dc.connectClock(clk.getPeer());
			dc.connectComponentGo(go.getPeer());
			dc.connectMemoryDone(sideDataReady.getPeer());
			dc.connectComponentDone(done.getPeer());
		}

		@Override
		public void accept(Visitor v) {
		}

		@Override
		public Set<Component> getFeedbackPoints() {
			Set<Component> feedback = new HashSet<Component>();
			feedback.addAll(super.getFeedbackPoints());
			if (goRegister != null) {
				feedback.add(goRegister);
			}
			return feedback;
		}

		public Bus getSideAddressBus() {
			return sideAddress;
		}

		public Port getSideDataPort() {
			return sideDataIn;
		}

		public Port getSideDataReadyPort() {
			return sideDataReady;
		}

		public Bus getSideEnableBus() {
			return sideEnable;
		}

		public Bus getSideSizeBus() {
			return sideSize;
		}

		@Override
		public boolean removeDataBus(Bus bus) {
			assert false : "remove data bus not supported on " + this;
			return false;
		}

		@Override
		public boolean removeDataPort(Port port) {
			assert false : "remove data port not supported on " + this;
			return false;
		}

	} // class Physical

	/**
	 * This class implements the done caching logic necessary for read accesses
	 * that complete combinationally. All it does is logically AND the read's GO
	 * with the memory's DONE.
	 * 
	 * This class if for accesses that take 0 cycles.
	 */
	public class ZeroDoneCache extends DoneCache {
		private And and = null;

		public ZeroDoneCache(Module owner) {
			and = new And(2);
			owner.addComponent(and);
		}

		@Override
		public void connectClock(Bus clock) {
		}

		@Override
		public void connectComponentDone(Port done) {
			done.setBus(and.getResultBus());
		}

		@Override
		public void connectComponentGo(Bus goBus) {
			and.getDataPorts().get(0).setBus(goBus);
		}

		@Override
		public void connectMemoryDone(Bus memDone) {
			and.getDataPorts().get(1).setBus(memDone);
		}

		@Override
		public void connectReset(Bus resetBus) {
		}
	}

	/**
	 * This class implements the logic and connectivity for the 'done caching'
	 * logic in the read. It is basically a register whose set and reset pins
	 * are manipulated (Q feeds back to D). The incoming GO set's the register.
	 * The incoming done (from the memory) is ANDed with the logical OR of the
	 * register output and the incoming GO in order to produce the done for this
	 * read. The output of the AND is also used to clear the register by being
	 * ORed with the global RESET. This class differs in that there is the OR
	 * gate beween the register output and the AND which includes the incoming
	 * GO so that combinational accesses complete in the same cycle.
	 * 
	 * This class is for reads which take 0 or more cycle.
	 */
	public class ZeroPlusDoneCache extends DoneCache {
		private Or combOr = null;

		public ZeroPlusDoneCache(Module owner) {
			super(owner);
			// Now insert an 'or' between the register and the doneAnd
			// to include a combinational path for the 0 latency
			// case. NOTE. The flop used MUST have reset take
			// precedence over set (the FDRS does!).
			combOr = new Or(2);
			owner.addComponent(combOr);

			doneAnd.getDataPorts().get(0).setBus(null);
			doneAnd.getDataPorts().get(0).setBus(combOr.getResultBus());

			combOr.getDataPorts().get(0).setBus(goRegister.getResultBus());
		}

		@Override
		public void connectComponentGo(Bus goBus) {
			super.connectComponentGo(goBus);
			combOr.getDataPorts().get(1).setBus(goBus);
		}
	}

	/** The full physical implementation of the MemoryRead. */
	Physical physical = null;

	public MemoryRead(boolean isVolatile, int width, boolean isSigned) {
		/*
		 * One port for the address.
		 */
		super(1, isVolatile, isSigned, width);

		getGoPort().setUsed(true);

		/*
		 * One bus for the data.
		 */
		makeExit(1);
		getResultBus().setSize(width, isSigned);
	}

	/**
	 * Accept method for the Visitor interface
	 */
	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	/**
	 * Returns a copy of this MemoryRead by creating a new memory read off of
	 * the {@link MemoryPort} associated with this node. We create a new access
	 * instead of cloning because of the way that the MemoryPort stores
	 * references (not in Referent). Creating a new access correctly sets up the
	 * Referent/Reference relationship.
	 * 
	 * @return a MemoryRead object.
	 * @exception CloneNotSupportedException
	 *                if an error occurs
	 */
	@Override
	public Object clone() {
		assert physical == null : "Cloning Physical not implemented";
		final MemoryRead clone = new MemoryRead(isVolatile(), getWidth(),
				isSigned());
		clone.setMemoryPort(getMemoryPort());
		copyComponentAttributes(clone);
		return clone;
	}

	@Override
	public Module getPhysicalComponent() {
		return physical;
	}

	public Bus getResultBus() {
		return getExit(Exit.DONE).getDataBuses().get(0);
	}

	/**
	 * Returns the targetted Register which is a StateHolder object.
	 * 
	 * @return the targetted Register
	 */
	@Override
	public StateHolder getStateHolder() {
		return getMemoryPort().getLogicalMemory();
	}

	/**
	 * returns true
	 */
	@Override
	public boolean isReadAccess() {
		return true;
	}

	/**
	 * This accessor forces contention on the {@link Referenceable} target so it
	 * may not execute in parallel with other accesses.
	 */
	@Override
	public boolean isSequencingPoint() {
		return true;
	}

	/**
	 * returns false
	 */
	@Override
	public boolean isWriteAccess() {
		return false;
	}

	public Physical makePhysicalComponent() {
		assert physical == null : "MemoryRead.physical already exists.";
		physical = new Physical();
		return physical;
	}

	/**
	 * Performs reverse constant propagation inside through component. This
	 * component will fetch the incoming {@link Value} from each {@link Bus}
	 * using {@link Bus#_getValue()}. It will then compute a new outgoing
	 * {@link Value} for each {@link Port} and set it with
	 * {@link Port#pushValueBackward(Value)}.
	 * 
	 * @return true if any of the port values was modified, false otherwise
	 */
	@Override
	protected boolean pushValuesBackward() {
		/*
		 * This is really handled by the physical implementation.
		 */
		return false;
	}

	/**
	 * Performs forward constant propagation through this component. This
	 * component will fetch the incoming {@link Value} from each {@link Port}
	 * using {@link Port#_getValue()}. It will then compute a new outgoing
	 * {@link Value} for each {@link Bus} and set it with
	 * {@link Bus#pushValueForward(Value)}.
	 * 
	 * @return true if any of the bus values was modified, false otherwise
	 */
	@Override
	protected boolean pushValuesForward() {
		final Bus resultBus = getResultBus();
		if (resultBus.getValue() == null) {
			resultBus.setSize(getWidth(), isSigned());
		}

		/*
		 * The is really handled by the physical implementation.
		 */
		return false;
	}
}
