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

import java.util.Map;
import java.util.Set;

import org.xronos.openforge.lim.Component;
import org.xronos.openforge.lim.Task;

/**
 * TaskResource is responsible to report all resources in a Task.
 * 
 * @author ysyu
 * @version $Id: TaskResource.java 2 2005-06-09 20:00:48Z imiller $
 */
public class TaskResource extends ResourceBank {

	/** the task that owns all these resources */
	private Task task = null;

	public TaskResource(Task task) {
		super();
		this.task = task;
	}

	/**
	 * @return a mapping of all resources to its counts
	 */
	@Override
	public Map<Class<Object>, Set<Component>> generateReport() {
		ProcedureResource pResource = (ProcedureResource) getResources()
				.iterator().next();
		return pResource.getTotalReport();
	}

	/**
	 * @return the Task that owns these resources
	 */
	public Task getTask() {
		return task;
	}
}
