/* 
 * XRONOS, High Level Synthesis of Streaming Applications
 * 
 * Copyright (C) 2014 EPFL SCI STI MM
 *
 * This file is part of XRONOS.
 *
 * XRONOS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * XRONOS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with XRONOS.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.xronos.orcc.backend.transform.pipelining.coloring;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * This class contains the input of the operators
 * 
 * @author Anatoly Prihozhy
 * 
 */
public class OperatorInputs {

	/**
	 * The inputs of operators
	 */
	public int[] objIns;

	/**
	 * Number of Operators
	 */
	public int N;

	/**
	 * Number of Variables
	 */
	public int M;

	public OperatorInputs(TestBench tB) {
		N = tB.N;
		M = tB.M;
		objIns = new int[N * M];

		create(tB);
	}

	/**
	 * Create the input objects
	 * 
	 * @param tB
	 */
	private void create(TestBench tB) {
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < M; j++) {
				objIns[i * M + j] = tB.F[i * M + j];
			}
		}

	}

	/**
	 * Print the input objects
	 * 
	 * @return
	 */
	public boolean print(BufferedWriter out) {
		if (N == 0) {
			return false;
		}
		if (M == 0) {
			return false;
		}
		try {
			out.write("MatrixF:" + "\n");
			for (int i = 0; i < N; i++) {
				for (int j = 0; j < M; j++) {
					out.write(objIns[i * M + j] + " ");
				}
				out.write("\n");
			}

			// out.close();
		} catch (IOException e) {
		}
		return true;
	}
}
