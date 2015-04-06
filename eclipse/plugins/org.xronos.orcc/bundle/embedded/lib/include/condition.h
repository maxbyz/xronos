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

#ifndef __EMBEDDED_CONDITION_H__
#define __EMBEDDED_CONDITION_H__

#include "mutex.h"

class Condition
{
public:
	Condition();

	~Condition();

	void wait(Mutex& mutex);

	void signal();

	void broadcast();

private:
#ifdef _WIN32
	HANDLE m_event;
	HANDLE m_sema;
	long m_waiters;
	unsigned m_was_broadcast;
#else
	pthread_cond_t mHandle;
#endif
};

#endif
