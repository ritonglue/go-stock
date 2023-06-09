package io.github.ritonglue.gostock;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;
import javax.money.MonetaryRounding;

import io.github.ritonglue.gostock.strategy.FIFOStrategy;
import io.github.ritonglue.gostock.strategy.LIFOStrategy;
import io.github.ritonglue.gostock.strategy.PRMPStrategy;
import io.github.ritonglue.gostock.strategy.Strategy;

/**
 * not thread safe
 *
 */
public class StockManager {
	private final Mode mode;
	private final Strategy strategy;
	private final MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
	private final List<Position> closedPositions = new ArrayList<>();
	private final MonetaryRounding rounding;
	
	/**
	 * A stock manager in FIFO mode with default rouding operator
	 */
	public StockManager() {
		this(Mode.FIFO);
	}

	/**
	 * A stock manager with default rouding operator
	 * @param mode
	 */
	public StockManager(Mode mode) {
		this(mode, Monetary.getDefaultRounding());

	}

	/**
	 * Build a stock manager
	 * @param mode
	 * @param rounding
	 */
	public StockManager(Mode mode, MonetaryRounding rounding) {
		this.mode = Objects.requireNonNull(mode, "mode null");
		switch(mode) {
		case FIFO:
			strategy = new FIFOStrategy();
			break;
		case LIFO:
			strategy = new LIFOStrategy();
			break;
		case PRMP:
			strategy = new PRMPStrategy();
			break;
		default:
			throw new AssertionError();
		}
		this.rounding = rounding;
	}

	public List<Position> getOpenedPositions() {
		List<Position> openedPositions = new ArrayList<>();
		Strategy strategy = this.getStrategy();
		for(TradeWrapper t : strategy) {
			Position position = new Position(t.getSource(), t.getQuantity(), t.getAmount());
			openedPositions.add(position);
		}
		return openedPositions;
	}

	public List<Position> getClosedPositions() {
		return Collections.unmodifiableList(this.closedPositions);
	}

	/**
	 * @param trades in ascending time order
	 */
	public void process(Iterable<TradeWrapper> trades) {
		for(TradeWrapper t : trades) {
			add(t);
		}
	}

	public void add(TradeWrapper trade) {
		if(trade == null) return;
		TradeType type = trade.getTradeType();
		switch(type) {
		case BUY:
			this.getStrategy().add(trade);
			break;
		case SELL:
			sell(trade);
			break;
		case MODIFICATION:
			modification(trade);
			break;
		case RBT:
			reimbursement(trade);
			break;
		}
	}

	private void reimbursement(TradeWrapper trade) {
		if(trade.getTradeType() != TradeType.RBT) return;
		BigDecimal quantity = trade.getQuantity();
		if(quantity == null) {
			//full reimbursement required
			quantity = this.getStrategy().getQuantity();
			trade.setQuantity(quantity);
		}
		sell(trade);
	}

	private void modification(TradeWrapper t) {
		if(t.getTradeType() != TradeType.MODIFICATION) return;
		Strategy strategy = this.getStrategy();
		MonetaryAmount modificationAmount = t.getAmount();
		Iterator<TradeWrapper> iter = strategy.iterator();
		if(strategy.size() == 1) {
			//easy case
			TradeWrapper tmp = iter.next();
			tmp.setAmount(tmp.getAmount().add(modificationAmount));
		} else {
			TradeWrapper tmp = iter.next();
			MonetaryAmount stockAmount = tmp.getAmount();
			MonetaryAmount stockAmountAbs = stockAmount.abs();
			BigDecimal stockQuantity = tmp.getQuantity();
			while(iter.hasNext()) {
				tmp = iter.next();
				MonetaryAmount amount = tmp.getAmount();
				stockAmount = stockAmount.add(amount);
				stockAmountAbs = stockAmountAbs.add(amount.abs());
				stockQuantity = stockQuantity.add(tmp.getQuantity());
			}
			iter = strategy.iterator();
			if(stockAmountAbs.signum() == 0) {
				if(stockQuantity.signum() == 0) {
					return;
				}
				//by quantity proportion
				modification(modificationAmount, stockQuantity, iter);
			} else {
				//by absolute amount proportion
				modification(modificationAmount, stockAmountAbs, stockAmount, iter);
			}
		}
	}
	
	private void modification(MonetaryAmount modificationAmount, BigDecimal stockQuantity, Iterator<TradeWrapper> iterator) {
		if(!iterator.hasNext()) return;
		TradeWrapper tmp = iterator.next();
		//proportion by quantity
		MonetaryAmount amount = tmp.getAmount();
		BigDecimal quantity = tmp.getQuantity();
		if(quantity.equals(stockQuantity)) {
			tmp.setAmount(amount.add(modificationAmount));
		} else {
			MonetaryAmount value = modificationAmount.multiply(quantity);
			try {
				value = value.divide(stockQuantity);
			} catch(ArithmeticException e) {
				double x = value.getNumber().doubleValue() / stockQuantity.doubleValue();
				value = factory.setNumber(x).setCurrency(amount.getCurrency()).create();
			}
			value = value.with(getRounding());
			tmp.setAmount(amount.add(value));
			modificationAmount = modificationAmount.subtract(value);
			stockQuantity = stockQuantity.subtract(quantity);
			modification(modificationAmount, stockQuantity, iterator);
		}
	}

	private void modification(MonetaryAmount modificationAmount, MonetaryAmount stockAmountAbs, MonetaryAmount stockAmount, Iterator<TradeWrapper> iterator) {
		if(!iterator.hasNext()) return;
		TradeWrapper tmp = iterator.next();
		//proportion by amount
		MonetaryAmount amount = tmp.getAmount();
		MonetaryAmount amountAbs = amount.abs();
		if(amount.equals(stockAmount)) {
			tmp.setAmount(amount.add(modificationAmount));
		} else {
			double factor = amountAbs.getNumber().doubleValue() / stockAmountAbs.getNumber().doubleValue();
			MonetaryAmount value = modificationAmount.multiply(factor).with(getRounding());
			tmp.setAmount(amount.add(value));
			stockAmount = stockAmount.subtract(amount);
			stockAmountAbs = stockAmountAbs.subtract(amountAbs);
			modificationAmount = modificationAmount.subtract(value);
			modification(modificationAmount, stockAmountAbs, stockAmount, iterator);
		}
	}

	public TradeWrapper getStock() {
		return getStrategy().getStock();
	}

	private void sell(TradeWrapper sell) {
		CloseCause closeCause = null;
		switch(sell.getTradeType()) {
		case SELL:
			closeCause = CloseCause.SELL;
			break;
		case RBT:
			closeCause = CloseCause.RBT;
			break;
		default:
			return;
		}
		Strategy strategy = this.getStrategy();
		if(strategy.isEmpty()) return;

		MonetaryAmountFactory<?> factory = this.getFactory();
		BigDecimal sellQuantity = sell.getQuantity();
		if(sellQuantity.signum() <= 0) return;

		TradeWrapper buy = strategy.peek();
		final BigDecimal stockQuantity = buy.getQuantity();
		final MonetaryAmount stockAmount = buy.getAmount();
		CurrencyUnit currency = stockAmount.getCurrency();
		factory = factory.setCurrency(currency);
		Object sellSource = sell.getSource();
		Object buySource = buy.getSource();
		if(sell.getAmount() == null) {
			//set to zero
			sell.setAmount(factory.setNumber(BigDecimal.ZERO).create());
		}
		int nsign = stockQuantity.compareTo(sellQuantity);
		if(nsign <= 0) {
			//sell everything
			buy.setQuantity(BigDecimal.ZERO);
			buy.setAmount(factory.setNumber(BigDecimal.ZERO).create());
			sell.setAmount(sell.getAmount().add(stockAmount));
			sell.setQuantity(sellQuantity.subtract(stockQuantity));
			Position position = new Position(buySource, sellSource, stockQuantity, stockAmount, closeCause);
			closedPositions.add(position);
			strategy.remove();
			if(nsign < 0) {
				sell(sell);
			}
		} else {
			//partial sell
			buy.setQuantity(stockQuantity.subtract(sellQuantity));
			MonetaryAmount m = stockAmount.multiply(sellQuantity);
			try {
				m = m.divide(stockQuantity);
			} catch(ArithmeticException e) {
				double x = m.getNumber().doubleValue() / stockQuantity.doubleValue();
				m = factory.setNumber(x).create();
			}
			m = m.with(getRounding());
			buy.setAmount(stockAmount.subtract(m));
			sell.setAmount(sell.getAmount().add(m));
			sell.setQuantity(BigDecimal.ZERO);
			Position position = new Position(buySource, sellSource, sellQuantity, m, closeCause);
			closedPositions.add(position);
		}
		sell.addBuyValues(buy);
	}

	public static class TradeWrapper implements Serializable {
		private static final long serialVersionUID = 1L;

		private BigDecimal quantity;
		private MonetaryAmount amount;
		private final TradeType tradeType;
		private final Object source;
		private final List<TradeWrapper> buyValues;
		
		public static class Builder {
			private BigDecimal quantity;
			private MonetaryAmount amount;
			private TradeType tradeType;
			private Object source;

			public Builder quantity(String quantity) {return quantity(new BigDecimal(quantity));}
			public Builder quantity(long quantity) {return quantity(new BigDecimal(quantity));}
			public Builder quantity(BigDecimal quantity) {this.quantity = quantity; return this;}
			public Builder amount(MonetaryAmount amount) {this.amount = amount; return this;}
			public Builder amount(Number number, String currency) {
				return amount(Monetary.getDefaultAmountFactory().setCurrency(currency).setNumber(number).create());
			}
			public Builder amount(double number, String currency) {
				return amount(Monetary.getDefaultAmountFactory().setCurrency(currency).setNumber(number).create());
			}
			public Builder amount(long number, String currency) {
				return amount(Monetary.getDefaultAmountFactory().setCurrency(currency).setNumber(number).create());
			}
			public Builder tradeType(TradeType tradeType) {this.tradeType = tradeType; return this;}
			public Builder source(Object source) {this.source = source; return this;}

			public TradeWrapper build() {
				List<TradeWrapper> buyValues = null;
				switch(tradeType) {
					case BUY:
						buyValues = Collections.emptyList();
						quantity = quantity.abs();
						break;
					case SELL:
						buyValues = new ArrayList<>();
						quantity = quantity.abs();
						amount = null;
						break;
					case MODIFICATION:
						buyValues = Collections.emptyList();
						source = null;
						break;
					case RBT:
						buyValues = new ArrayList<>();
						if(quantity != null) {
							quantity = quantity.abs();
						}
						amount = null;
						break;
				}
				return new TradeWrapper(quantity, amount, tradeType, source, buyValues);
			}
		}

		public static Builder tradeType(TradeType tradeType) {
			return new Builder().tradeType(tradeType);
		}

		private TradeWrapper(BigDecimal quantity, MonetaryAmount amount, TradeType tradeType, Object source, List<TradeWrapper> buyValues) {
			this.quantity = quantity;
			this.amount = amount;
			this.tradeType = tradeType;
			this.source = source;
			this.buyValues = buyValues;
		}
		
		public static TradeWrapper buy(BigDecimal quantity, MonetaryAmount amount, Object source) {
			return tradeType(TradeType.BUY).amount(amount).quantity(quantity).source(source).build();
		}

		/**
		 * create a buy value based on a unit amount
		 * @param quantity
		 * @param unitAmount amount for one quantity
		 * @param source
		 * @return same as buy(quantity, unitAmount.multiply(quantity), source)
		 */
		public static TradeWrapper buyUnitAmount(BigDecimal quantity, MonetaryAmount unitAmount, Object source) {
			return buy(quantity, unitAmount.multiply(quantity), source);
		}

		public static TradeWrapper sell(BigDecimal quantity, Object source) {
			return tradeType(TradeType.SELL).quantity(quantity).source(source).build();
		}

		/**
		 * @param amount. Can be negative or positive
		 * @return
		 */
		public static TradeWrapper modification(MonetaryAmount amount) {
			return tradeType(TradeType.MODIFICATION).amount(amount).build();
		}

		public static TradeWrapper reimbursement(BigDecimal quantity, Object source) {
			return tradeType(TradeType.RBT).quantity(quantity).source(source).build();
		}

		public static TradeWrapper reimbursement(Object source) {
			return reimbursement(null, source);
		}

		public BigDecimal getQuantity() {
			return quantity;
		}

		public MonetaryAmount getAmount() {
			return amount;
		}

		public Object getSource() {
			return source;
		}

		public TradeType getTradeType() {
			return tradeType;
		}

		@Override
		public String toString() {
			return String.format("Trade [quantity=%s, amount=%s, tradeType=%s, source=%s]", quantity, amount, tradeType,
					source);
		}

		private void setQuantity(BigDecimal quantity) {
			this.quantity = quantity;
		}

		private void setAmount(MonetaryAmount amount) {
			this.amount = amount;
		}

		public List<TradeWrapper> getBuyValues() {
			return buyValues;
		}
		
		public void addBuyValues(TradeWrapper t) {
			this.buyValues.add(t);
		}
	}
	
	public Mode getMode() {
		return mode;
	}

	private Strategy getStrategy() {
		return this.strategy;
	}

	private MonetaryAmountFactory<?> getFactory() {
		return factory;
	}

	public MonetaryRounding getRounding() {
		return rounding;
	}
}
