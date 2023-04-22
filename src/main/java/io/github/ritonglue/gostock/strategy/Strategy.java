package io.github.ritonglue.gostock.strategy;

import java.math.BigDecimal;
import java.util.Iterator;

import javax.money.MonetaryAmount;

import io.github.ritonglue.gostock.StockManager.Trade;

public interface Strategy extends Iterable<Trade> {
	boolean add(Trade t);
	Trade peek();
	Trade remove();
	boolean isEmpty();
	void clear();
	int size();

	/**
	 * @return the quantity in stock
	 */
	default BigDecimal getQuantity() {
		Iterator<Trade> iterator = this.iterator();
		BigDecimal quantity = BigDecimal.ZERO;
		while(iterator.hasNext()) {
			Trade tmp = iterator.next();
			quantity = quantity.add(tmp.getQuantity());
		}
		return quantity;
	}

	/**
	 * @return the quantity and buy value amount
	 */
	default Trade getStock() {
		Iterator<Trade> iterator = this.iterator();
		BigDecimal quantity = BigDecimal.ZERO;
		//if stock is empty, amount is null : missing currency.
		MonetaryAmount amount = null;
		if(iterator.hasNext()) {
			Trade tmp = iterator.next();
			quantity = tmp.getQuantity();
			amount = tmp.getAmount();
		}
		while(iterator.hasNext()) {
			Trade tmp = iterator.next();
			quantity = quantity.add(tmp.getQuantity());
			amount = amount.add(tmp.getAmount());
		}
		return Trade.buy(quantity, amount, null);
	}
}
