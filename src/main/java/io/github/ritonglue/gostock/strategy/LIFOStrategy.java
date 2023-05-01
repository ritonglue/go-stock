package io.github.ritonglue.gostock.strategy;

import java.util.ArrayDeque;
import java.util.Deque;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;

public final class LIFOStrategy extends QueueStrategy {
	private final Deque<TradeWrapper> stack = new ArrayDeque<>();

	@Override
	public boolean add(TradeWrapper t) {
		getQueue().addFirst(t);
		return true;
	}

	@Override
	public Deque<TradeWrapper> getQueue() {
		return stack;
	}
}
