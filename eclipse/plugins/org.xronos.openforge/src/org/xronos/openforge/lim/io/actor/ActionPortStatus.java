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

package org.xronos.openforge.lim.io.actor;

import org.xronos.openforge.lim.Bus;
import org.xronos.openforge.lim.Exit;
import org.xronos.openforge.lim.Latency;
import org.xronos.openforge.lim.io.FifoAccess;
import org.xronos.openforge.lim.io.FifoIF;
import org.xronos.openforge.lim.io.SimplePin;
import org.xronos.openforge.lim.io.SimplePinRead;

/**
 * ActionPortStatus is an atomic access to a given {@link FifoIF} which
 * 
 * <p>
 * Created: Wed Oct 19 15:26:09 2005
 * 
 * @author imiller, last modified by $Author: imiller $
 * @version $Id: ActionPortStatus.java 98 2006-02-02 20:08:45Z imiller $
 */
public class ActionPortStatus extends FifoAccess {

	public ActionPortStatus(ActorScalarInput targetInterface) {
		// The send pin indicates that the port contains data.
		this(targetInterface, targetInterface.getSendPin());
	}

	public ActionPortStatus(ActorScalarOutput targetInterface) {
		this(targetInterface, targetInterface.getReadyPin());
	}

	private ActionPortStatus(FifoIF targetInterface, SimplePin statusPin) {
		super(targetInterface);

		// Excluding 'sideband' ports/buses (those connecting to pins)
		// there is a single result bus on this module, resulting in
		// the status of the target interface (status of the full or
		// exists port)
		Exit exit = makeExit(1);
		Bus result = exit.getDataBuses().get(0);
		// Bus done = exit.getDoneBus();
		result.setUsed(true);

		exit.setLatency(Latency.ZERO);

		final SimplePinRead status = new SimplePinRead(statusPin);
		addComponent(status);

		result.getPeer().setBus(status.getResultBus());
	}

	/**
	 * This accessor may execute in parallel with other similar (non state
	 * modifying) accesses.
	 */
	@Override
	public boolean isSequencingPoint() {
		return false;
	}

}
