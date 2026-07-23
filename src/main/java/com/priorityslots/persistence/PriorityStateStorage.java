package com.priorityslots.persistence;

interface PriorityStateStorage
{
	String read();

	void write(String serializedState);

	void clear();
}
