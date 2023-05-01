package io.github.ritonglue.gostock.strategy;

import java.math.BigDecimal;
import java.util.Iterator;

import javax.money.MonetaryAmount;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;

public interface Strategy extends Iterable<TradeWrapper> {
	boolean add(TradeWrapper t);
	TradeWrapper peek();
	TradeWrapper remove();
	boolean isEmpty();
	void clear();
	int size();

	/**
	 * @return the quantity in stock
	 */
	default BigDecimal getQuantity() {
		Iterator<TradeWrapper> iterator = this.iterator();
		BigDecimal quantity = BigDecimal.ZERO;
		while(iterator.hasNext()) {
			TradeWrapper tmp = iterator.next();
			quantity = quantity.add(tmp.getQuantity());
		}
		return quantity;
	}

	/**
	 * @return the quantity and buy value amount
	 */
	default TradeWrapper getStock() {
		Iterator<TradeWrapper> iterator = this.iterator();
		BigDecimal quantity = BigDecimal.ZERO;
		//if stock is empty, amount is null : missing currency.
		MonetaryAmount amount = null;
		if(iterator.hasNext()) {
			TradeWrapper tmp = iterator.next();
			quantity = tmp.getQuantity();
			amount = tmp.getAmount();
		}
		while(iterator.hasNext()) {
			TradeWrapper tmp = iterator.next();
			quantity = quantity.add(tmp.getQuantity());
			amount = amount.add(tmp.getAmount());
		}
		return TradeWrapper.buy(quantity, amount, null);
	}
}
