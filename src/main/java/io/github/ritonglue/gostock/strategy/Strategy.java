package io.github.ritonglue.gostock.strategy;

import io.github.ritonglue.gostock.StockManager.Trade;

public interface Strategy extends Iterable<Trade> {
	void add(Trade t);
	Trade peek();
	Trade remove();
	boolean isEmpty();
	void clear();
}