package io.github.ritonglue.gostock;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;
import javax.money.MonetaryRounding;

import io.github.ritonglue.gostock.exception.StockAmountReductionException;
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
	private final List<TradeWrapper> orphanSells = new ArrayList<>();
	private final MonetaryRounding rounding;
	//key is (buy, sell) tradeWrappers
	private final Map<BuySellKey, MonetaryAmount> mapBuySell = new HashMap<>();
	//key is a modification tradeWrapper
	private final Map<TradeWrapper, Map<TradeWrapper, MonetaryAmount>> mapModification = new HashMap<>();

	private static class BuySellKey {
		private final TradeWrapper buy;
		private final TradeWrapper sell;

		private BuySellKey(TradeWrapper sell, TradeWrapper buy) {
			this.buy = buy;
			this.sell = sell;
		}

		@Override
		public boolean equals(Object obj) {
			if(!(obj instanceof BuySellKey)) return false;
			BuySellKey key = (BuySellKey) obj;
			return Objects.equals(key.buy, this.buy)
			&& Objects.equals(key.sell, this.sell);
		}
		@Override
		public int hashCode() {
			return Objects.hash(buy, sell);
		}
	}

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

	/**
	 * force amount reduction to the buy value sold by sell value
	 * @param buy
	 * @param sell
	 * @param amount
	 */
	public void addBuySellMoney(TradeWrapper buy, TradeWrapper sell, MonetaryAmount amount) {
		if(buy == null) return;
		if(sell == null) return;
		if(amount == null) return;
		if(amount.signum() > 0) {
			this.mapBuySell.put(new BuySellKey(sell, buy), amount);
		}
	}

	/**
	 * force modification amount for this buy value
	 * @param buy
	 * @param modification
	 * @param amount
	 */
	public void addBuyModificationMoney(TradeWrapper buy, TradeWrapper modification, MonetaryAmount amount) {
		if(buy == null) return;
		if(modification == null) return;
		if(amount == null) return;
		MonetaryAmount modificationAmount = modification.getAmount();
		//check same sign
		if(amount.signum() * modificationAmount.signum() > 0) {
			this.mapModification.computeIfAbsent(modification, o -> new HashMap<>())
				.put(buy, amount);
		}
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

	/**
	 * check if collection amount doesn't exceed modificiationAmount
	 * @param modificationAmount
	 * @param modifications
	 */
	private static void checkModifications(MonetaryAmount modificationAmount, Collection<MonetaryAmount> modifications) {
		if(modifications.isEmpty()) return;
		Iterator<MonetaryAmount> iter = modifications.iterator();
		MonetaryAmount sum = iter.next().abs();
		while(iter.hasNext()) {
			sum = sum.add(iter.next().abs());
		}
		MonetaryAmount modificationAmountAbs = modificationAmount.abs();
		MonetaryAmount diff = modificationAmountAbs.subtract(sum);
		if(diff.signum() < 0) {
			throw new IllegalStateException("bad total modication amount %s %s".formatted(modificationAmount, sum));
		}
	}

	private void modification(TradeWrapper t) {
		if(t.getTradeType() != TradeType.MODIFICATION) return;
		Strategy strategy = this.getStrategy();
		Iterator<TradeWrapper> iter = strategy.iterator();
		List<TradeWrapper> list = null;
		if(strategy.size() == 1) {
			//easy case
			list = List.of(iter.next());
		} else {
			list = new ArrayList<>();
			while(iter.hasNext()) {
				list.add(iter.next());
			}
		}
		modification(t, list);
	}

	/**
	 * apply modification t to list of buy values
	 * @param t
	 * @param buys
	 */
	private void modification(TradeWrapper t, List<TradeWrapper> buys) {
		MonetaryAmount modificationAmount = t.getAmount();
		Iterator<TradeWrapper> iter = buys.iterator();
		if(buys.size() == 1) {
			//easy case
			TradeWrapper tmp = iter.next();
			MonetaryAmount stockAmount = tmp.getAmount();
			MonetaryAmount diff = stockAmount.add(modificationAmount);
			if(diff.signum() < 0) {
				throw new StockAmountReductionException(stockAmount, modificationAmount);
			}
			tmp.setAmount(diff);
		} else {
			//multiple buy values
			Map<TradeWrapper, MonetaryAmount> modifications = this.mapModification.get(t);
			if(modifications != null) {
				checkModifications(modificationAmount, modifications.values());
				//build new buys list
				buys = new ArrayList<>();
				//force modification amount
				while(iter.hasNext()) {
					TradeWrapper tmp = iter.next();
					MonetaryAmount amount = modifications.get(tmp);
					if(amount != null) {
						modificationAmount = modificationAmount.subtract(amount);
						MonetaryAmount a = tmp.getAmount().add(amount);
						if(a.signum() < 0) {
							throw new StockAmountReductionException(tmp.getAmount(), amount);
						}
						tmp.setAmount(a);
					} else {
						//this buy value is not forced
						buys.add(tmp);
					}
				}
				if(buys.isEmpty()) {
					return;
				}
				iter = buys.iterator();
			}
			TradeWrapper tmp = iter.next();
			int sign = modificationAmount.signum();
			if(sign > 0) {
				//per quantity
				BigDecimal stockQuantity = tmp.getQuantity();
				while(iter.hasNext()) {
					tmp = iter.next();
					stockQuantity = stockQuantity.add(tmp.getQuantity());
				}
				iter = buys.iterator();
				modification(modificationAmount, stockQuantity, iter);
			} else if(sign < 0) {
				//per amount
				MonetaryAmount stockAmount = tmp.getAmount();
				while(iter.hasNext()) {
					tmp = iter.next();
					stockAmount = stockAmount.add(tmp.getAmount());
				}
				MonetaryAmount diff = stockAmount.add(modificationAmount);
				if(diff.signum() < 0) {
					throw new StockAmountReductionException(stockAmount, modificationAmount);
				}
				iter = buys.iterator();
				modification(modificationAmount, stockAmount, iter);
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

	private void modification(MonetaryAmount modificationAmount, MonetaryAmount stockAmount, Iterator<TradeWrapper> iterator) {
		if(!iterator.hasNext()) return;
		TradeWrapper tmp = iterator.next();
		//proportion by amount
		MonetaryAmount amount = tmp.getAmount();
		if(amount.equals(stockAmount)) {
			tmp.setAmount(amount.add(modificationAmount));
		} else {
			double factor = amount.getNumber().doubleValue() / stockAmount.getNumber().doubleValue();
			MonetaryAmount value = modificationAmount.multiply(factor).with(getRounding());
			tmp.setAmount(amount.add(value));
			stockAmount = stockAmount.subtract(amount);
			modificationAmount = modificationAmount.subtract(value);
			modification(modificationAmount, stockAmount, iterator);
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
		BigDecimal sellQuantity = sell.getQuantity();
		if(strategy.isEmpty()) {
			if(sellQuantity.signum() > 0) {
				this.orphanSells.add(sell);
			}
			return;
		}

		MonetaryAmountFactory<?> factory = this.getFactory();
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
			BuySellKey key = new BuySellKey(sell, buy);
			buy.setQuantity(stockQuantity.subtract(sellQuantity));
			//is the amount provided ?
			MonetaryAmount m = this.mapBuySell.get(key);
			if(m == null) {
				//compute amount m
				m = stockAmount.multiply(sellQuantity);
				try {
					m = m.divide(stockQuantity);
				} catch(ArithmeticException e) {
					double x = m.getNumber().doubleValue() / stockQuantity.doubleValue();
					m = factory.setNumber(x).create();
				}
				m = m.with(getRounding());
			}
			buy.setAmount(stockAmount.subtract(m));
			if(buy.getAmount().signum() < 0) {
				throw new IllegalStateException("buy amount negative");
			}
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

		public <T> T getSource(Class<T> clazz) {
			return clazz.cast(source);
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

	public List<TradeWrapper> getOrphanSells() {
		return orphanSells;
	}
}
