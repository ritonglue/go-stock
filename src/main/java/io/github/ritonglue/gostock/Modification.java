package io.github.ritonglue.gostock;

import java.io.Serializable;
import java.math.BigDecimal;

import javax.money.MonetaryAmount;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;

public class Modification implements Serializable {
	private static final long serialVersionUID = 1L;

	private final TradeWrapper buy;
	private final TradeWrapper modification;
	private final BigDecimal quantity;
	private final MonetaryAmount amountBefore;
	private final MonetaryAmount amountAfter;

	Modification(TradeWrapper buy, TradeWrapper modification, BigDecimal quantity, MonetaryAmount amountBefore, MonetaryAmount amountAfter) {
		this.buy = buy;
		this.quantity = quantity;
		this.modification = modification;
		this.amountBefore = amountBefore;
		this.amountAfter = amountAfter;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public TradeWrapper getBuy() {
		return buy;
	}

	public TradeWrapper getModification() {
		return modification;
	}

	public MonetaryAmount getAmountBefore() {
		return amountBefore;
	}

	public MonetaryAmount getAmountAfter() {
		return amountAfter;
	}
}
