/*
 * Copyright (c) 2012, Ecole Polytechnique Fédérale de Lausanne
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

package org.xronos.orcc.design;

import net.sf.orcc.df.Actor;
import net.sf.orcc.df.Instance;

import org.xronos.openforge.app.EngineThread;
import org.xronos.openforge.app.GenericJob;
import org.xronos.openforge.app.OptionRegistry;
import org.xronos.openforge.lim.Design;
import org.xronos.orcc.design.visitors.DesignActor;

/**
 * This class transforms an Orcc {@link Instance} Object to an OpenForge
 * {@link Design} Object
 * 
 * @author Endri Bezati
 */
public class ActorToDesign {
	Design design;
	Actor actor;
	ResourceCache resourceCache;

	public ActorToDesign(Actor actor, ResourceCache resourceCache) {
		this.actor = actor;
		this.resourceCache = resourceCache;
		design = new Design();
	}

	public Design buildDesign() {
		// Get Instance name
		String designName = actor.getName();
		design.setIDLogical(designName);
		GenericJob job = EngineThread.getGenericJob();
		job.getOption(OptionRegistry.TOP_MODULE_NAME).setValue(
				design.getSearchLabel(), designName);

		// DfSwitch<Void> stmIOFinder = new DfVisitor<Void>(new StmtIO(
		// resourceCache));
		// stmIOFinder.doSwitch(actor);

		DesignActor designVisitor = new DesignActor(design, resourceCache);
		designVisitor.doSwitch(actor);

		return design;
	}

}
