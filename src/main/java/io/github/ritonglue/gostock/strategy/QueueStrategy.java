package io.github.ritonglue.gostock.strategy;

import java.util.Iterator;
import java.util.Queue;

import io.github.ritonglue.gostock.StockManager.Trade;

public abstract class QueueStrategy implements Strategy {

	public abstract Queue<Trade> getQueue();

	@Override
	public Trade peek() {
		return getQueue().peek();
	}

	@Override
	public Trade remove() {
		return getQueue().remove();
	}

	@Override
	public boolean isEmpty() {
		return getQueue().isEmpty();
	}

	@Override
	public void add(Trade t) {
		getQueue().add(t);
	}

	@Override
	public void clear() {
		getQueue().clear();
	}

	@Override
	public Iterator<Trade> iterator() {
		return getQueue().iterator();
	}
}
