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

package net.sf.openforge.app;

/**
 * JobListener receives events about the status of a job.
 * 
 * 
 * Created: Thu Mar 14 15:23:14 2002
 * 
 * @author <a href="mailto:abk@cubist">Andreas Kollegger</a>
 * @version $Id: JobListener.java 2 2005-06-09 20:00:48Z imiller $
 */

public interface JobListener extends java.util.EventListener {

	public void jobNotice(JobEvent event);

} // class JobListener
