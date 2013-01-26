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
package org.xronos.openforge.report;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.xronos.openforge.app.EngineThread;
import org.xronos.openforge.app.OptionRegistry;
import org.xronos.openforge.lim.Bus;
import org.xronos.openforge.lim.CodeLabel;
import org.xronos.openforge.lim.Component;
import org.xronos.openforge.lim.Exit;
import org.xronos.openforge.lim.Latch;
import org.xronos.openforge.lim.Port;
import org.xronos.openforge.lim.memory.MemoryRead;
import org.xronos.openforge.lim.op.AddOp;
import org.xronos.openforge.lim.op.AndOp;
import org.xronos.openforge.lim.op.ComplementOp;
import org.xronos.openforge.lim.op.ConditionalAndOp;
import org.xronos.openforge.lim.op.ConditionalOrOp;
import org.xronos.openforge.lim.op.DivideOp;
import org.xronos.openforge.lim.op.EqualsOp;
import org.xronos.openforge.lim.op.GreaterThanEqualToOp;
import org.xronos.openforge.lim.op.GreaterThanOp;
import org.xronos.openforge.lim.op.LeftShiftOp;
import org.xronos.openforge.lim.op.LessThanEqualToOp;
import org.xronos.openforge.lim.op.LessThanOp;
import org.xronos.openforge.lim.op.MinusOp;
import org.xronos.openforge.lim.op.ModuloOp;
import org.xronos.openforge.lim.op.MultiplyOp;
import org.xronos.openforge.lim.op.NotEqualsOp;
import org.xronos.openforge.lim.op.NotOp;
import org.xronos.openforge.lim.op.NumericPromotionOp;
import org.xronos.openforge.lim.op.OrOp;
import org.xronos.openforge.lim.op.PlusOp;
import org.xronos.openforge.lim.op.RightShiftOp;
import org.xronos.openforge.lim.op.RightShiftUnsignedOp;
import org.xronos.openforge.lim.op.SubtractOp;
import org.xronos.openforge.lim.op.XorOp;
import org.xronos.openforge.lim.primitive.And;
import org.xronos.openforge.lim.primitive.EncodedMux;
import org.xronos.openforge.lim.primitive.Mux;
import org.xronos.openforge.lim.primitive.Not;
import org.xronos.openforge.lim.primitive.Or;
import org.xronos.openforge.lim.primitive.Reg;
import org.xronos.openforge.lim.primitive.SRL16;
import org.xronos.openforge.util.IndentWriter;


/**
 * Prints a resource count report hierarchically.
 * 
 * @version $Id: ResourcePrinter.java 134 2006-05-01 21:39:25Z imiller $
 * @author ysyu
 */
public class ResourcePrinter {

	private IndentWriter writer = null;

	/** current design report to be printed */
	// private DesignResource designResource = null;

	/** map of lim class to string */
	private Map<Class<?>, String> limClassToName = new HashMap<Class<?>, String>();

	/** maintains method name hierarchy */
	private String upper_method_name = null;

	/**
	 * Default constructor
	 */
	public ResourcePrinter(OutputStream stream) {
		writer = new IndentWriter(stream);
		writer.setIndentString("    ");
		initLimClassToNameMap();

		writer.println("//");
		writer.println("// Design Resource Utilization Report");
		final String runDate = EngineThread.getGenericJob()
				.getOption(OptionRegistry.RUN_DATE)
				.getValue(CodeLabel.UNSCOPED).toString();
		writer.println("// Generated on: " + runDate);
		writer.println("//\n");
	}

	/**
	 * Prints design resources.
	 * 
	 * @param designRes
	 *            design resources to be printed
	 */
	public void print(DesignResource designRes) {
		/** prints total design resource count */
		writer.println(designRes.getDesign().showIDLogical() + " Total: ");
		printResource(designRes.generateReport());
		writer.println("");

		// designResource = designRes;

		String s = "Design: " + designRes.getDesign().showIDLogical();
		writer.println(s);
		printDotted(s);

		/** now traverse design tasks */
		for (Iterator<Object> iter = designRes.getResources().iterator(); iter
				.hasNext();) {
			print((TaskResource) iter.next());
		}
	}

	/**
	 * Prints task resources
	 * 
	 * @param taskResource
	 *            task resources to be printed
	 */
	public void print(TaskResource taskResource) {
		String s = "Entry: " + taskResource.getTask().getCall().showIDLogical();
		// writer.inc();
		writer.println(s);
		printDotted(s);

		/** print entry resources first and any sub method calls */
		for (Iterator<Object> iter = taskResource.getResources().iterator(); iter
				.hasNext();) {
			print((ProcedureResource) iter.next());
			upper_method_name = null;
		}

		/** prints total task resources */
		printDotted(s + " Total:");
		writer.println(s + " Total: ");
		printResource(taskResource.generateReport());
		printDotted(s + " Total:");
		writer.println("");
		// writer.dec();
	}

	/**
	 * Prints resources within a procedure recursively which maintains method
	 * call hierarchy.
	 * 
	 * @param procResource
	 *            Procedure report to be printed
	 */
	public void print(ProcedureResource procResource) {
		/*
		 * tries to imitate java hierarchical name. (ie: main calls test1 and
		 * test1 calls test2 will result in "main.test1.test2".
		 */
		String s = "";
		if (upper_method_name == null) {
			upper_method_name = procResource.getProcedure().showIDLogical();
			s = "Method: " + upper_method_name;
		} else {
			upper_method_name = upper_method_name + "."
					+ procResource.getProcedure().showIDLogical();
			s = "Method: " + upper_method_name;
		}

		writer.inc();
		writer.println(s);
		printDotted(s);

		/** prints resources local to this method */
		printResource(procResource.generateReport());

		/** prints sub methods before totaling */
		for (Iterator<Object> iter = procResource.getResources().iterator(); iter
				.hasNext();) {
			Object o = iter.next();
			if (o instanceof ProcedureResource) {
				print((ProcedureResource) o);
			}
		}

		/** print total resource count of this method, includes sub methods */
		writer.println();
		writer.println("Method Total:");
		printResource(procResource.getTotalReport());
		printDotted("Method Total:");
		writer.dec();

		/** removes last method (which is current method) name before existing */
		int last = upper_method_name.lastIndexOf(".");
		if (last >= 0) {
			upper_method_name = upper_method_name.substring(0, last);
		}
	}

	/**
	 * Print each resources with appropriate count. ie: "2  Adds" or
	 * "1  Subtract"
	 * 
	 * @param resources
	 *            resources report
	 */
	private void printResource(Map resources) {
		for (Iterator iter = resources.keySet().iterator(); iter.hasNext();) {
			final Class<?> key = (Class<?>) iter.next();
			final Set<Object> set = (Set) resources.get(key);
			if (getLimClassName(key) == null) {
			} // must be something we arent interested in reporting
			else {
				writer.print(set.size());
				if (set.size() < 10)
					writer.print("    ");
				else if (set.size() < 100)
					writer.print("   ");
				else if (set.size() < 1000)
					writer.print("  ");
				else
					writer.print(" ");
				writer.print(getLimClassName(key));
				final Map<Integer, Set<Object>> sortedByBitWidth = BitWidthFinder
						.sortByBitWidth(set);
				String values = "";
				for (Integer width : sortedByBitWidth.keySet()) {
					values += sortedByBitWidth.get(width).size() + "x" + width
							+ " ";
				}
				// writer.println(set.size() > 1 ? "s":"");
				// writer.println("       " + values);
				writer.println((set.size() > 1 ? "s" : "") + " : " + values);

				if (EngineThread.getGenericJob().getUnscopedBooleanOptionValue(
						OptionRegistry.XDETAILED_REPORT)) {
					writer.inc();
					for (Integer width : sortedByBitWidth.keySet()) {
						Set<?> comps = sortedByBitWidth.get(width);
						for (Object obj : comps) {
							if (obj instanceof Component)
								writer.println(showComponent((Component) obj));
							else
								writer.println("???");
						}
					}
					writer.dec();
				}
			}
		}
	}

	private String showComponent(Component comp) {
		String ret = comp.toString();
		for (Port port : comp.getPorts()) {
			String value = port.getValue() == null ? "null" : port.getValue()
					.debug();
			if (port == comp.getGoPort() || port == comp.getClockPort()
					|| port == comp.getResetPort())
				;
			else
				ret = ret + " p:" + value;
			/*
			 * if (port == getGoPort()) ret = ret + " go:" + val; else if (port
			 * == getClockPort()) ret = ret + " ck:" + val; else if (port ==
			 * getResetPort()) ret = ret + " rs:" + val; else ret = ret + " p:"
			 * + val;
			 */
		}
		for (Exit exit : comp.getExits()) {
			for (Bus bus : exit.getBuses()) {
				String value = bus.getValue() == null ? "null" : bus.getValue()
						.debug();
				if (bus == exit.getDoneBus())
					// ret = ret + " done:" + val;
					;
				else
					ret = ret + " data:" + value;
			}
		}

		return ret;
	}

	/**
	 * @param c
	 *            a Class
	 * 
	 * @return a String representation of c
	 */
	private String getLimClassName(Class<?> c) {
		return limClassToName.get(c);
	}

	/**
	 * Prints dotted line according to the previous line length
	 * 
	 * @param previous
	 *            previous line
	 */
	private void printDotted(String previous) {
		final int length = previous.length();
		String dotted_line = "-";
		for (int i = 0; i < length - 1; i++) {
			dotted_line = dotted_line + "-";
		}
		writer.println(dotted_line);
	}

	/**
	 * Initializes limClassToName map
	 */
	private void initLimClassToNameMap() {
		limClassToName.put(AddOp.class, "Add");
		limClassToName.put(And.class, "And");
		limClassToName.put(AndOp.class, "Logical And");
		limClassToName.put(NumericPromotionOp.class, "Numeric Promote");
		limClassToName.put(ComplementOp.class, "Complement");
		limClassToName.put(ConditionalAndOp.class, "Logical And");
		limClassToName.put(ConditionalOrOp.class, "Logical Or");
		limClassToName.put(DivideOp.class, "Div");
		limClassToName.put(EncodedMux.class, "EncodedMux");
		limClassToName.put(EqualsOp.class, "Equal To");
		limClassToName.put(GreaterThanEqualToOp.class,
				"Greater Than Or Equal To");
		limClassToName.put(GreaterThanOp.class, "Greater Than");
		limClassToName.put(Latch.class, "Latch");
		limClassToName.put(LeftShiftOp.class, "Left Shift");
		limClassToName.put(LessThanEqualToOp.class, "Less Than Or Equal To");
		limClassToName.put(LessThanOp.class, "Less Than");
		limClassToName.put(MemoryRead.class, "Memory");
		limClassToName.put(MinusOp.class, "Minus");
		limClassToName.put(ModuloOp.class, "Modulo");
		limClassToName.put(MultiplyOp.class, "Multiply");
		limClassToName.put(Mux.class, "Mux");
		limClassToName.put(Not.class, "Not");
		limClassToName.put(NotEqualsOp.class, "Not Equal");
		limClassToName.put(NotOp.class, "(!)Conditional Not");
		limClassToName.put(Or.class, "Or");
		limClassToName.put(OrOp.class, "Logical Or");
		limClassToName.put(PlusOp.class, "Plus");
		limClassToName.put(Reg.class, "Register");
		limClassToName.put(RightShiftOp.class, "Right Shift");
		limClassToName.put(RightShiftUnsignedOp.class, "Unsigned Right Shift");
		limClassToName.put(SRL16.class, "SRL16");
		limClassToName.put(SubtractOp.class, "Subtract");
		limClassToName.put(XorOp.class, "Xor");
	}
}
