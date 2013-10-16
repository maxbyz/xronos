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

import net.sf.orcc.ir.Block;
import net.sf.orcc.ir.BlockIf;
import net.sf.orcc.ir.BlockWhile;
import net.sf.orcc.ir.InstPhi;
import net.sf.orcc.ir.Var;
import net.sf.orcc.ir.util.AbstractIrVisitor;
import net.sf.orcc.ir.util.IrUtil;

/**
 * A dead phi code elimination,
 * 
 * @author Endri Bezati
 * 
 */
public class DeadPhiElimination extends AbstractIrVisitor<Void> {

	boolean changed = false;

	@Override
	public Void caseBlockIf(BlockIf blockIf) {
		// visit Join Block
		doSwitch(blockIf.getJoinBlock());
		if (changed) {
			changed = false;
			doSwitch(blockIf);
		}
		doSwitch(blockIf.getThenBlocks());
		if (changed) {
			changed = false;
			doSwitch(blockIf);
		}

		doSwitch(blockIf.getElseBlocks());
		if (changed) {
			changed = false;
			doSwitch(blockIf);
		}

		return null;
	}

	@Override
	public Void caseBlockWhile(BlockWhile blockWhile) {
		// Visit the join block in the begining
		doSwitch(blockWhile.getJoinBlock());

		doSwitch(blockWhile.getBlocks());
		if (changed) {
			changed = false;
			doSwitch(blockWhile);
		}
		return null;
	}

	@Override
	public Void caseInstPhi(InstPhi phi) {
		Var target = phi.getTarget().getVariable();
		if (target != null && !target.isUsed()) {
			IrUtil.delete(phi);
			changed = true;
			indexInst--;
		}
		return null;
	}

	@Override
	public Void doSwitch(List<Block> blocks) {
		return visitBlocksReverse(blocks);
	}

}
