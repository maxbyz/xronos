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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.xronos.openforge.lim.op.Constant;

/**
 * Record is the mechanism for describing aggregate {@link LogicalValue}
 * initializers. It is simply an ordered container for zero or more constituent
 * values. The size of a Record is the sum of the sizes of these constituents.
 * <P>
 * In specifying a Record, the caller must be sure to include values in such a
 * way that their addressable unit representations, when concatenated,
 * constitute the valid layout of the entire Record in memory, including any
 * values needed for padding.
 * 
 * @version $Id: Record.java 70 2005-12-01 17:43:11Z imiller $
 */
public class Record implements LogicalValue, MemoryVisitable {

	@SuppressWarnings("serial")
	private static class MixedAddressStridePolicyException extends
			RuntimeException {
		public MixedAddressStridePolicyException(String msg) {
			super(msg);
		}
	}

	/** List of component LogicalValues */
	private List<LogicalValue> values;

	/** Cached sum of sizes, in addressable units, of component LogicalValues */
	private int size = 0;

	/** Cached sum of sizes, in bits, of component LogicalValues */
	private int bitSize = 0;

	/**
	 * Constructs a new Record.
	 * 
	 * @param values
	 *            the ordered list of component {@link LogicalValue
	 *            LogicalValues} of this record; these should include values for
	 *            any padding that is required by the caller's source language
	 *            semantics
	 * @throws NullPointerException
	 *             if <code>values</code> is null or any of its elements is null
	 * @throws ClassCastException
	 *             if any element of <code>values</code> does not implement
	 *             {@link LogicalValue}
	 */
	public Record(List<LogicalValue> values) {
		this.values = new ArrayList<LogicalValue>(values);
		for (LogicalValue lv : this.values) {
			size += lv.getSize();
			bitSize += lv.getBitSize();
		}
	}

	/**
	 * Implementation of the MemoryVisitable interface.
	 * 
	 * @param memVis
	 *            a non null 'MemoryVisitor'
	 * @throws NullPointerException
	 *             if memVis is null
	 */
	@Override
	public void accept(MemoryVisitor memVis) {
		memVis.visit(this);
	}

	/**
	 * Returns a new LogicalValue object (Record) which has been deep copied
	 * from this. Each component LogicalValue of this Record is similarly copied
	 * to generate a deep copy of this Record.
	 * 
	 * <p>
	 * requires : none
	 * <p>
	 * modifies : none
	 * <p>
	 * effects : creates a new Record which is a deep copy of this record.
	 * 
	 * @return a new Record with the same structure and initial values as this.
	 */
	@Override
	public LogicalValue copy() {
		List<LogicalValue> copiedList = new ArrayList<LogicalValue>(
				values.size());
		for (LogicalValue lv : values) {
			copiedList.add(lv.copy());
		}
		return new Record(copiedList);
	}

	/**
	 * Returns the address stride policy governing this logical value.
	 */
	@Override
	public AddressStridePolicy getAddressStridePolicy() {
		assert !values.isEmpty();
		if (values.isEmpty()) {
			return null;
		}

		// Test that the stride policy is consistent throughout this
		// record
		AddressStridePolicy policy = values.get(0).getAddressStridePolicy();
		for (LogicalValue logicalValue : getComponentValues()) {
			if (!logicalValue.getAddressStridePolicy().equals(policy)) {
				throw new MixedAddressStridePolicyException(policy
						+ " not equivalent to "
						+ logicalValue.getAddressStridePolicy());
			}
		}
		return policy;
	}

	/**
	 * Returns the maximum addressable unit size of any constituent of this
	 * Record.
	 * 
	 * @return The largest constituent alignment size, greater than or equal to
	 *         0.
	 */
	@Override
	public int getAlignmentSize() {
		int max = 0;
		for (LogicalValue logicalValue : getComponentValues()) {
			max = Math.max(max, logicalValue.getAlignmentSize());
		}
		return max;
	}

	/**
	 * Returns the number of bits allocated based on analysis of the
	 * AddressableUnit representation.
	 */
	@Override
	public int getBitSize() {
		return bitSize;
	}

	/**
	 * Gets the component values.
	 * 
	 * @return the ordered list of component {@link LogicalValue LogicalValues}
	 *         as provided in the constructor
	 */
	public List<LogicalValue> getComponentValues() {
		return Collections.unmodifiableList(values);
	}

	/**
	 * Gets the bitwise representation of this value.
	 * 
	 * @return an array of AddressableUnit objects, ordered in Least Significant
	 *         Address to Most Significant Address order (meaning the higher the
	 *         index to the rep, the higher it appears in memory) the array may
	 *         have no elements in the case that this is the value of an empty
	 *         data item, such as a struct with no fields
	 */
	@Override
	public AddressableUnit[] getRep() {
		final AddressableUnit[] rep = new AddressableUnit[getSize()];
		int index = 0;
		for (LogicalValue value : values) {
			final AddressableUnit[] valueRep = value.getRep();
			System.arraycopy(valueRep, 0, rep, index, valueRep.length);
			index += valueRep.length;
		}
		return rep;
	}

	/**
	 * Gets the size in addressable units of this value.
	 * 
	 * @return the number of addressable needed to represent this value, always
	 *         greater than or equal to 0 (&gt;=0)
	 */
	@Override
	public int getSize() {
		return size;
	}

	/** @inheritDoc */
	@Override
	public LogicalValue getValueAtOffset(int delta, int size) {
		if (delta == 0 && size == getSize()) {
			return this;
		}

		return new Slice(this, delta, size);
	}

	/**
	 * Returns a copy of this LogicalValue in which the range of addressable
	 * units specified by min and max (both inclusive) has been removed. Thus
	 * the returned LogicalValue has size of the original - (max - min + 1).
	 * <p>
	 * This is accomplished by removing all fields that are completely contained
	 * within the range and then removing the remaining range at either boundry.
	 * 
	 * @param min
	 *            the offset of the least significant addressable unit of the
	 *            range to delete. 0 based offset.
	 * @param max
	 *            the offset of the most significant addressable unit to of the
	 *            range to delete. 0 based offset.
	 * @return a new LogicalValue object whose value is based on the current
	 *         LogicalValue with the specified range removed
	 * @throws NonRemovableRangeException
	 *             if any part of the range cannot be removed because the
	 *             resulting context would be non-meaningful (eg removal of a
	 *             portion of a pointers value)
	 */
	@Override
	public LogicalValue removeRange(int min, int max)
			throws NonRemovableRangeException {
		/*
		 * XXX === side note === XXX
		 * 
		 * This version of implementation does correctly chop the head and
		 * truncate the tail, it DOES_NOT work on removing the range within the
		 * middle, which means when this method is used, make sure the specified
		 * min and max are NOT_BOTH within the middle of this rocoed's range.
		 * For example:
		 * 
		 * suppose the size of this recoed is 20 bytes. 1. Removes from 0 to any
		 * position is ok. 2. Removes from any position to 19 is ok. 3. Removes
		 * from 5 to 10 is NOT_OK.
		 * 
		 * Since the MemoryReduction either chops the head or truncates the
		 * tail, there is no need to support removing range in other ways.
		 */
		int localMin = min;
		int localMax = max;
		List<LogicalValue> newValues = new ArrayList<LogicalValue>();
		int processedUnits = 0;

		final AddressableUnit[] rep = getRep();

		if (min < 0 || max >= rep.length) {
			String msg = "range [" + min + "-" + max
					+ "] out of bound, legal range [0-" + (rep.length - 1)
					+ "]";
			throw new NonRemovableRangeException(msg);
		}

		for (LogicalValue value : values) {
			int chunkSize = value.getSize();
			int inspectedSize = processedUnits + chunkSize;

			LogicalValue newValue = null;
			if (min >= 0 && max == getSize() - 1) {
				// Truncates the tail or the entire range
				if (inspectedSize <= min) {
					newValue = value.copy();
				} else {
					localMin = Math.max(0, min - processedUnits);
					if (inspectedSize - 1 <= max) {

						localMax = Math
								.min(chunkSize - 1, max - processedUnits);
					}
					newValue = value.removeRange(localMin, localMax);
				}
			} else if (min == 0 && max < getSize() - 1) {
				// Chops off the head
				if (processedUnits - 1 >= max) {
					newValue = value.copy();
				} else {
					if (inspectedSize - 1 > max) {
						localMax = Math
								.min(chunkSize - 1, max - processedUnits);
					} else {
						localMax = chunkSize - 1;
					}
					newValue = value.removeRange(localMin, localMax);
				}
			}

			if (newValue.getSize() > 0) {
				newValues.add(newValue);
			}
			processedUnits += value.getSize();
		}

		return new Record(newValues);
	}

	/**
	 * Returns an AggregateConstant.
	 * 
	 * @see org.xronos.openforge.lim.memory.LogicalValue#toConstant()
	 */
	@Override
	public MemoryConstant toConstant() {
		ArrayList<Constant> constants = new ArrayList<Constant>(values.size());
		// int bitwidth = 0;
		for (LogicalValue lv : values) {
			constants.add(lv.toConstant());
		}
		return new AggregateConstant(constants, bitSize);
	}

	/**
	 * Gets the {@link Location} denoted by this value.
	 * 
	 * @return the location, or {@link Location#INVALID} if this value does not
	 *         denote a valid location
	 */
	@Override
	public Location toLocation() {
		return Location.INVALID;
	}

	@Override
	public String toString() {
		final StringBuffer buf = new StringBuffer();
		buf.append("Record@");
		buf.append(Integer.toHexString(hashCode()));
		buf.append("={");
		for (Iterator<LogicalValue> iter = getComponentValues().iterator(); iter
				.hasNext();) {
			buf.append(iter.next().toString());
			if (iter.hasNext()) {
				buf.append(",");
			}
		}
		buf.append("}");
		return buf.toString();
	}

}
