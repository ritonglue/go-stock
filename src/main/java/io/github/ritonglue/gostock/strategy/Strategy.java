package io.github.ritonglue.gostock.strategy;

import java.math.BigDecimal;
import java.util.Iterator;

import io.github.ritonglue.gostock.StockManager.Trade;

public interface Strategy extends Iterable<Trade> {
	boolean add(Trade t);
	Trade peek();
	Trade remove();
	boolean isEmpty();
	void clear();
	int size();

	default BigDecimal getQuantity() {
		Iterator<Trade> iterator = this.iterator();
		BigDecimal stockQuantity = null;
		if(iterator.hasNext()) {
			Trade tmp = iterator.next();
			stockQuantity = tmp.getQuantity();
		}
		while(iterator.hasNext()) {
			Trade tmp = iterator.next();
			stockQuantity = stockQuantity.add(tmp.getQuantity());
		}
		return stockQuantity;
	}
}
