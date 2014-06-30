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
package org.xronos.orcc.backend.transform;

import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.transform.TacTransformation;

import org.eclipse.emf.ecore.EObject;
import org.xronos.orcc.ir.BlockMutex;
import org.xronos.orcc.ir.InstPortWrite;

/**
 * This class extends the TacTransformation, adding it InstSpecific
 * 
 * @author Endri Bezati
 * 
 */
public class XronosTac extends TacTransformation {
	@Override
	public Expression defaultCase(EObject object) {
		if (object instanceof BlockMutex) {
			doSwitch(((BlockMutex) object).getBlocks());
		} else if (object instanceof InstPortWrite) {
			InstPortWrite instPortWrite = (InstPortWrite) object;
			complexityLevel++;
			instPortWrite.setValue(doSwitch(instPortWrite.getValue()));
			complexityLevel--;
			return null;
		}
		return super.defaultCase(object);
	}
}
