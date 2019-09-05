package io.tomahawkd.common.provider;

public interface TargetProvider {

	enum State {
		INITIAL, // Functionally like FINISHED, Default when initially constructed
		RUNNING, // Querying data in cache when extract remaining data
		WAITING, // Waiting data from extraction
		FINISHING, // Querying data in cache with extraction complete
		FINISHED // No more data
	}

	State getStatus();

	String getNextTarget();

	boolean hasMoreData();
}
