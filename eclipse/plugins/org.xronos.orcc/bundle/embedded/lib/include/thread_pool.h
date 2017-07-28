/**
 * ----------------------------------------------------------------------------
 * __  ___ __ ___  _ __   ___  ___
 * \ \/ / '__/ _ \| '_ \ / _ \/ __|
 *  >  <| | | (_) | | | | (_) \__ \
 * /_/\_\_|  \___/|_| |_|\___/|___/
 * ----------------------------------------------------------------------------
 * Copyright (C) 2015 EPFL SCI STI MM
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
 * 
 * Additional permission under GNU GPL version 3 section 7
 * 
 * If you modify this Program, or any covered work, by linking or combining it
 * with Eclipse (or a modified version of Eclipse or an Eclipse plugin or
 * an Eclipse library), containing parts covered by the terms of the
 * Eclipse Public License (EPL), the licensors of this Program grant you
 * additional permission to convey the resulting work.  Corresponding Source
 * for a non-source form of such a combination shall include the source code
 * for the parts of Eclipse libraries used as well as that of the  covered work.
 * 
 */

#ifndef __THREAD_POOL_H__
#define __THREAD_POOL_H__

#include <algorithm>
#include <vector>

#include "condition.h"
#include "mutex.h"
#include "thread.h"


class ThreadPool
{
public:
	void start(void*  args)
	{
		std::vector<Thread*>::iterator it;
		for(it = pool.begin(); it != pool.end(); it++)
			(*it)->start(args);
	}

	void addWorker(Thread* schedThread)
	{
		pool.push_back(schedThread);
	}

	void wait()
	{
		done.wait(mutex);
	}

	void cancel()
	{
		std::vector<Thread*>::iterator it;
		for(it = pool.begin(); it != pool.end(); it++)
		{
			(*it)->cancel();
		}
	}

	std::vector<Thread*> pool;

	Condition done;

	Mutex mutex;

};

#endif