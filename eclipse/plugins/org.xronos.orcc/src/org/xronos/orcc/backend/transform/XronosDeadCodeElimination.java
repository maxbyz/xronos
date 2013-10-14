/*
 * Copyright (c) 2013, Ecole Polytechnique Fédérale de Lausanne
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
package org.xronos.orcc.backend.transform;

import java.util.List;
import java.util.ListIterator;

import net.sf.orcc.ir.Block;
import net.sf.orcc.ir.BlockBasic;
import net.sf.orcc.ir.BlockIf;
import net.sf.orcc.ir.ExprBool;
import net.sf.orcc.ir.ExprVar;
import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.InstAssign;
import net.sf.orcc.ir.InstPhi;
import net.sf.orcc.ir.Instruction;
import net.sf.orcc.ir.IrFactory;
import net.sf.orcc.ir.Var;
import net.sf.orcc.ir.util.AbstractIrVisitor;
import net.sf.orcc.util.util.EcoreHelper;

public class XronosDeadCodeElimination extends AbstractIrVisitor<Void> {

	@Override
	public Void caseBlockIf(BlockIf blockIf) {
		Expression condition = blockIf.getCondition();
		if (condition.isExprBool()) {
			if (((ExprBool) condition).isValue()) {

			} else {
				if (blockIf.getElseBlocks().isEmpty()) {
					List<Block> parentBlocks = EcoreHelper
							.getContainingList(blockIf);
					// parentBlocks.remove(blockIf);
					// replacePhis(blockIf.getJoinBlock(), 1);
				}
			}
		}
		return null;
	}

	private void replacePhis(BlockBasic joinBlock, int index) {
		ListIterator<Instruction> it = joinBlock.listIterator();
		while (it.hasNext()) {
			Instruction instruction = it.next();
			if (instruction.isInstPhi()) {
				InstPhi phi = (InstPhi) instruction;

				Var target = phi.getTarget().getVariable();
				ExprVar sourceExpr = (ExprVar) phi.getValues().get(index);
				Var source = sourceExpr.getUse().getVariable();

				// translate the phi to an assign
				ExprVar expr = IrFactory.eINSTANCE.createExprVar(source);
				InstAssign assign = IrFactory.eINSTANCE.createInstAssign(
						target, expr);

				it.set(assign);

				// remove the other variable
				ExprVar localExpr = (ExprVar) phi.getValues().get(1 - index);
				Var local = localExpr.getUse().getVariable();
				procedure.getLocals().remove(local);
			}
		}
	}

}