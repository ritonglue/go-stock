package io.github.ritonglue.gostock.strategy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.money.MonetaryAmount;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;

public final class PRMPStrategy implements Strategy {
	private MonetaryAmount buyValue;
	private BigDecimal buyQuantity = BigDecimal.ZERO;

	private TradeWrapper stock;

	@Override
	public Iterator<TradeWrapper> iterator() {
		return isEmpty() ? Collections.emptyIterator() :  List.of(stock).iterator();
	}

	@Override
	public boolean add(TradeWrapper t) {
		if(buyValue == null) {
			buyValue = t.getAmount();
			buyQuantity = t.getQuantity();
		} else {
			buyValue = stock.getAmount().add(t.getAmount());
			buyQuantity = stock.getQuantity().add(t.getQuantity());
		}
		stock = TradeWrapper.buy(buyQuantity, buyValue, null);
		return true;
	}

	@Override
	public BigDecimal getQuantity() {
		return stock == null ? BigDecimal.ZERO : stock.getQuantity();
	}

	@Override
	public TradeWrapper getStock() {
		return stock == null ? TradeWrapper.buy(BigDecimal.ZERO, null, null) : stock;
	}

	@Override
	public TradeWrapper peek() {
		return getStock();
	}

	@Override
	public TradeWrapper remove() {
		buyQuantity = BigDecimal.ZERO;
		buyValue = null;
		stock = null;
		return getStock();
	}

	@Override
	public boolean isEmpty() {
		TradeWrapper stock = getStock();
		return stock == null || stock.getQuantity().signum() == 0;
	}

	@Override
	public int size() {
		return isEmpty() ? 0 : 1;
	}

	@Override
	public void clear() {
		stock = null;
		buyValue = null;
		buyQuantity = BigDecimal.ZERO;
	}
}
