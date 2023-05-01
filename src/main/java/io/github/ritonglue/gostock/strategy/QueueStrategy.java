package io.github.ritonglue.gostock.strategy;

import java.util.Iterator;
import java.util.Queue;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;

public abstract class QueueStrategy implements Strategy {

	public abstract Queue<TradeWrapper> getQueue();

	@Override
	public TradeWrapper peek() {
		return getQueue().peek();
	}

	@Override
	public TradeWrapper remove() {
		return getQueue().remove();
	}

	@Override
	public boolean isEmpty() {
		return getQueue().isEmpty();
	}

	@Override
	public boolean add(TradeWrapper t) {
		return getQueue().add(t);
	}

	@Override
	public void clear() {
		getQueue().clear();
	}

	@Override
	public Iterator<TradeWrapper> iterator() {
		return getQueue().iterator();
	}

	@Override
	public int size() {
		return getQueue().size();
	}
}
