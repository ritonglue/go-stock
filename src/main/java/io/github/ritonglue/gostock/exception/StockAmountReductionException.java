package io.github.ritonglue.gostock.exception;

import javax.money.MonetaryAmount;

@SuppressWarnings("serial")
public class StockAmountReductionException extends RuntimeException {
	private final MonetaryAmount stockAmount;
	private final MonetaryAmount modificationAmount;

	public StockAmountReductionException(MonetaryAmount stockAmount, MonetaryAmount modificationAmount) {
		super("Modification (%s) exceeds stock (%s)".formatted(modificationAmount, stockAmount));
		this.stockAmount = stockAmount;
		this.modificationAmount = modificationAmount;
	}

	public MonetaryAmount getStockAmount() {
		return stockAmount;
	}

	public MonetaryAmount getModificationAmount() {
		return modificationAmount;
	}
}
