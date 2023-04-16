package io.github.ritonglue.gostock.strategy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.money.MonetaryAmount;

import io.github.ritonglue.gostock.StockManager.Trade;

public final class PRMPStrategy implements Strategy {
	private MonetaryAmount buyValue;
	private BigDecimal buyQuantity = BigDecimal.ZERO;

	private Trade stock;

	@Override
	public Iterator<Trade> iterator() {
		return isEmpty() ? Collections.emptyIterator() :  List.of(stock).iterator();
	}

	@Override
	public void add(Trade t) {
		if(buyValue == null) {
			buyValue = t.getAmount();
			buyQuantity = t.getQuantity();
			stock = Trade.buy(buyQuantity, buyValue, null);
		} else {
			BigDecimal stockQuantity = stock.getQuantity();
			MonetaryAmount stockValue = stock.getAmount();
			buyValue = stockValue = stockValue.add(t.getAmount());
			buyQuantity = stockQuantity = stockQuantity.add(t.getQuantity());
			stock = Trade.buy(buyQuantity, buyValue, null);
		}
	}

	private Trade getStock() {
		return stock;
	}

	@Override
	public Trade peek() {
		return getStock();
	}

	@Override
	public Trade remove() {
		buyQuantity = BigDecimal.ZERO;
		buyValue = null;
		stock = null;
		return getStock();
	}

	@Override
	public boolean isEmpty() {
		Trade stock = getStock();
		return stock == null || stock.getQuantity().signum() == 0;
	}

	@Override
	public void clear() {
		stock = null;
		buyValue = null;
		buyQuantity = BigDecimal.ZERO;
	}
}
