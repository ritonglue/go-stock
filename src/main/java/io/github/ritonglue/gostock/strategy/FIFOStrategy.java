package io.github.ritonglue.gostock.strategy;

import java.util.LinkedList;
import java.util.Queue;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;

public final class FIFOStrategy extends QueueStrategy {
	private final Queue<TradeWrapper> queue = new LinkedList<>();

	@Override
	public Queue<TradeWrapper> getQueue() {
		return queue;
	}
}
