package io.github.ritonglue.gostock.strategy;

import java.util.LinkedList;
import java.util.Queue;

import io.github.ritonglue.gostock.StockManager.Trade;

public final class FIFOStrategy extends QueueStrategy {
	private final Queue<Trade> queue = new LinkedList<>();

	@Override
	public Queue<Trade> getQueue() {
		return queue;
	}
}
