package io.github.ritonglue.gostock;

import java.io.Serializable;
import java.math.BigDecimal;

import javax.money.MonetaryAmount;

public class Position implements Serializable {
	private static final long serialVersionUID = 1L;

	private final Object buy;
	private final Object sell;
	private final BigDecimal quantity;
	private final MonetaryAmount amount;

	Position(Object buy, BigDecimal quantity, MonetaryAmount amount) {
		this(buy, null, quantity, amount);
	}

	Position(Object buy, Object sell, BigDecimal quantity, MonetaryAmount amount) {
		this.buy = buy;
		this.sell = sell;
		this.quantity = quantity;
		this.amount = amount;
	}

	public Object getBuy() {
		return buy;
	}

	public Object getSell() {
		return sell;
	}

	public <T> T getBuy(Class<T> clazz) {
		return clazz.cast(buy);
	}

	public <T> T getSell(Class<T> clazz) {
		return clazz.cast(sell);
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public MonetaryAmount getAmount() {
		return amount;
	}

	public boolean isOpened() {
		return this.sell == null;
	}

	public boolean isClosed() {
		return !isOpened();
	}

	@Override
	public String toString() {
		return String.format("Position [quantity=%s, amount=%s, buy=%s, sell=%s]", quantity, amount, buy, sell);
	}
}
