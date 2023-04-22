package io.github.ritonglue.gostock.strategy;

import java.util.ArrayDeque;
import java.util.Deque;

import io.github.ritonglue.gostock.StockManager.Trade;

public final class LIFOStrategy extends QueueStrategy {
	private final Deque<Trade> stack = new ArrayDeque<>();

	@Override
	public boolean add(Trade t) {
		getQueue().addFirst(t);
		return true;
	}

	@Override
	public Deque<Trade> getQueue() {
		return stack;
	}
}
