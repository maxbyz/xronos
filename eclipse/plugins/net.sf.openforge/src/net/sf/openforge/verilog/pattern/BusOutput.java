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
package net.sf.openforge.verilog.pattern;

import net.sf.openforge.lim.Bus;
import net.sf.openforge.util.naming.ID;
import net.sf.openforge.verilog.model.Output;

/**
 * A Verilog Output which is based on a LIM {@link Bus}.
 * <P>
 * 
 * Created: May 7, 2002
 * 
 * @author <a href="mailto:andreas.kollegger@xilinx.com">Andreas Kollegger</a>
 * @version $Id: BusOutput.java 2 2005-06-09 20:00:48Z imiller $
 */
public class BusOutput extends Output implements BusNet {

	Bus bus;

	/**
	 * Constructs a BusOutput based on a LIM Bus.
	 * 
	 * @param bus
	 *            The LIM Bus upon which to base the Net
	 */
	public BusOutput(Bus bus) {
		super(ID.toVerilogIdentifier(ID.showLogical(bus)), bus.getValue()
				.getSize());
		this.bus = bus;
	}

	/**
	 * Returns the Bus upon which the Net is based.
	 * 
	 * @return The LIM Bus upon which the Net is based
	 */
	@Override
	public Bus getBus() {
		return bus;
	}

} // BusOutput

