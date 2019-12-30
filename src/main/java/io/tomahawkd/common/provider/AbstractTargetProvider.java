package io.tomahawkd.common.provider;

public abstract class AbstractTargetProvider<T> implements TargetProvider<T> {

	private State state = State.INITIAL;

	protected final void setStatus(State state) {
		this.state = state;
	}

	public boolean hasMoreData() {
		return this.state == State.RUNNING ||
				this.state == State.WAITING ||
				this.state == State.FINISHING;
	}

	public State getStatus() {
		return state;
	}

	public abstract T getNextTarget();
}
