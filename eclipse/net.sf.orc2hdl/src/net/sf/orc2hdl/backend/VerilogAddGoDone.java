/*
 * Copyright (c) 2011, Ecole Polytechnique Fédérale de Lausanne
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   * Neither the name of the Ecole Polytechnique Fédérale de Lausanne nor the names of its
 *     contributors may be used to endorse or promote products derived from this
 *     software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package net.sf.orc2hdl.backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import net.sf.orcc.df.Action;
import net.sf.orcc.df.Instance;

/**
 * This class takes a Verilog file generated by OpenForge and adds the Go and
 * done signal for each action in the Top instance module
 * 
 * @author Endri Bezati
 * 
 */
public class VerilogAddGoDone {

	Instance instance;

	String srcPath;

	String tgtPath;

	public VerilogAddGoDone(Instance instance, String srcPath, String tgtPath) {
		this.instance = instance;
		this.srcPath = srcPath;
		this.tgtPath = tgtPath;
	}

	public void addGoDone() {
		File newVerilogFile = new File(tgtPath + File.separator
				+ instance.getSimpleName() + ".v");
		try {
			// Old Verilog Instance without Go and Done
			String oVerilogFile = srcPath + File.separator
					+ instance.getSimpleName() + ".v";
			FileInputStream iStream = new FileInputStream(oVerilogFile);

			BufferedReader iBuffer = new BufferedReader(new InputStreamReader(
					iStream));

			// New Verilog Instance with Go and Done
			FileOutputStream oStream = new FileOutputStream(newVerilogFile);
			PrintWriter verilogWriter = new PrintWriter(oStream);

			// Read each character in until ")" of the module
			while (iBuffer.ready()) {
				char tmp = (char) iBuffer.read();
				if (tmp == ')') {
					iBuffer.mark(10);
					break;
				} else {
					verilogWriter.print(tmp);
				}
			}
			// Add for each action into to the Top module the name of the action
			// with _go and _done
			for (Action action : instance.getActor().getActions()) {
				verilogWriter.print(", " + action.getName() + "_go, "
						+ action.getName() + "_done");
			}

			// Close bracket and new line
			verilogWriter.println(");");

			// Print go and done of each action for input declaration
			for (Action action : instance.getActor().getActions()) {
				verilogWriter.print("output\t\t" + action.getName() + "_go;\n");
				verilogWriter.print("output\t\t" + action.getName()
						+ "_done;\n");
			}

			int skip = 1;
			String contains;
			iBuffer.reset();
			boolean stop = false;
			while (iBuffer.ready()) {
				if (skip == 1) {
					iBuffer.readLine();
					skip = 2;
				}
				iBuffer.mark(100);
				contains = iBuffer.readLine();
				// Find <actionName>_go and <actionName>_done in the assign on
				// the Top Module
				
				if (contains.indexOf("endmodule") != -1) {
					stop = true;
				}
				if (contains.indexOf("assign") != -1 && !stop) {
					for (Action action : instance.getActor().getActions()) {

						String actionNameGo = action.getName() + "_go";
						String actionNameDone = action.getName() + "_done";
						if (contains.indexOf(actionNameGo) != -1) {
							int loc = contains.indexOf(actionNameGo);
							String newLine = "assign "
									+ contains
											.substring(loc, contains.length());
							verilogWriter.println(newLine);
						}
						if (contains.indexOf(actionNameDone) != -1) {
							int loc = contains.indexOf(actionNameDone);
							String newLine = "assign "
									+ contains
											.substring(loc, contains.length());
							verilogWriter.println(newLine);
						}
					}
					verilogWriter.println(contains);

				} else {
					verilogWriter.println(contains);
				}
			}

			// Flush, close and write
			verilogWriter.flush();
			verilogWriter.close();
			iBuffer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
