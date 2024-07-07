package io.github.ritonglue.gostock;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

import io.github.ritonglue.gostock.exception.EmptyPositionModificationException;
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
	private final ModificationMode modificationMode;
	private final Strategy strategy;
	private final MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
	private final List<Position> closedPositions = new ArrayList<>();
	private final Map<Object, List<Position>> mapClosedPositionsBySell = new HashMap<>();
	private final Map<Object, List<Position>> mapClosedPositionsByBuy = new HashMap<>();
	private final List<TradeWrapper> orphanSells = new ArrayList<>();
	private final List<Modification> modifications = new ArrayList<>();
	public List<Modification> getModifications() {
		return modifications;
	}

	private final MonetaryRounding rounding;
	//key is (buy, sell) tradeWrappers
	private final Map<BuySellKey, MonetaryAmount> mapBuySell = new HashMap<>();
	//key is a modification tradeWrapper
	private final Map<TradeWrapper, Map<TradeWrapper, MonetaryAmount>> mapModification = new HashMap<>();

	public static class Builder {
		private Mode mode;
		private ModificationMode modificationMode;
		private MonetaryRounding rounding;

		public Builder mode(Mode mode) {this.mode = mode; return this;}
		public Builder modificationMode(ModificationMode modificationMode) {this.modificationMode = modificationMode; return this;}
		public Builder rouding(MonetaryRounding rounding) {this.rounding = rounding; return this;}

		public StockManager build() {
			return new StockManager(mode, rounding, modificationMode);
		}
	}

	public static Builder create(Mode mode) {
		return new Builder().mode(mode);
	}

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
		this(mode, null);
	}

	public StockManager(Mode mode, MonetaryRounding rounding) {
		this(mode, rounding, ModificationMode.MIXED);
	}

	/**
	 * Build a stock manager
	 * @param mode
	 * @param rounding
	 */
	public StockManager(Mode mode, MonetaryRounding rounding, ModificationMode modificationMode) {
		this.modificationMode = Objects.requireNonNull(modificationMode, "modification mode null");
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
		this.rounding = rounding == null ? Monetary.getDefaultRounding() : rounding;
	}

	/**
	 * Force amount reduction to the buy value sold by sell value
	 * Only used if there are many buy values for this sell.
	 *
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
	 * Force modification amount for this buy value.
	 * Only used if there are many buy values for this modification.
	 *
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
		case MODIFICATION_QUANTITY:
			modificationQuantity(trade);
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
		if(diff.isNegative()) {
			throw new IllegalStateException("bad total modication amount %s %s".formatted(modificationAmount, sum));
		}
	}

	/**
	 * check if you can apply the reduction modificationAmount
	 * @param modificationAmount
	 */
	private void checkModifications(MonetaryAmount modificationAmount) {
		if(modificationAmount.isPositiveOrZero()) return;
		if(this.isEmpty()) {
			throw new EmptyPositionModificationException();
		}
		TradeWrapper stock = getStock();
		MonetaryAmount amount = stock.getAmount();
		MonetaryAmount diff = amount.add(modificationAmount);
		if(diff.isNegative()) {
			throw new StockAmountReductionException(amount, modificationAmount);
		}
	}

	private void modification(TradeWrapper t) {
		if(t.getTradeType() != TradeType.MODIFICATION) return;
		List<TradeWrapper> list = buildListTradeWrappers();
		checkModifications(t.getAmount());
		modification(t, list);
	}

	private void modificationQuantity(TradeWrapper t) {
		if(t.getTradeType() != TradeType.MODIFICATION_QUANTITY) return;
		List<TradeWrapper> list = buildListTradeWrappers();
		modificationQuantity(t, list);
	}

	private List<TradeWrapper> buildListTradeWrappers() {
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
		return list;
	}

	/**
	 * apply modification quantity t to list of buy values
	 * @param t
	 * @param buys
	 */
	private void modificationQuantity(TradeWrapper t, List<TradeWrapper> buys) {
		if(buys.isEmpty()) return;
		BigDecimal quantityBefore = t.getQuantityBefore();
		BigDecimal quantityAfter = t.getQuantityAfter();
		if(BigDecimal.ONE.compareTo(quantityBefore) == 0) {
			for(TradeWrapper b : buys) {
				b.setQuantity(b.getQuantity().multiply(quantityAfter));
			}
		} else {
			int scale = t.getScale();
			RoundingMode roundingMode = t.getRoundingMode();
			BigDecimal totalQuantity = buys.stream().map(TradeWrapper::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
			totalQuantity = totalQuantity.multiply(quantityAfter).divide(quantityBefore, scale, roundingMode);
			TradeWrapper first = buys.get(0);
			for(int i = 1, n = buys.size() ; i < n ; ++i) {
				TradeWrapper a = buys.get(i);
				BigDecimal quantity = a.getQuantity();
				quantity = quantity.multiply(quantityAfter).divide(quantityBefore, scale, roundingMode);
				a.setQuantity(quantity);
				totalQuantity = totalQuantity.subtract(quantity);
			}
			first.setQuantity(totalQuantity);
		}
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
			TradeWrapper buy = iter.next();
			MonetaryAmount stockAmount = buy.getAmount();
			MonetaryAmount diff = stockAmount.add(modificationAmount);
			if(diff.isNegative()) {
				throw new StockAmountReductionException(stockAmount, modificationAmount);
			}
			buy.setAmount(diff);
			Modification modification = new Modification(buy, t, buy.getQuantity(), stockAmount, buy.getAmount());
			this.modifications.add(modification);
		} else {
			//multiple buy values
			Map<TradeWrapper, MonetaryAmount> modifications = this.mapModification.get(t);
			if(modifications != null) {
				checkModifications(modificationAmount, modifications.values());
				//build new buys list
				buys = new ArrayList<>();
				//force modification amount
				while(iter.hasNext()) {
					TradeWrapper buy = iter.next();
					MonetaryAmount amount = modifications.get(buy);
					if(amount != null) {
						modificationAmount = modificationAmount.subtract(amount);
						MonetaryAmount stockAmount = buy.getAmount();
						MonetaryAmount a = stockAmount.add(amount);
						if(a.isNegative()) {
							throw new StockAmountReductionException(stockAmount, amount);
						}
						buy.setAmount(a);
						Modification modification = new Modification(buy, t, buy.getQuantity(), amount, buy.getAmount());
						this.modifications.add(modification);
					} else {
						//this buy value is not forced
						buys.add(buy);
					}
				}
				if(buys.isEmpty()) {
					return;
				}
			}
			int sign = modificationAmount.signum();
			ModificationMode modificationMode = this.getModificationMode(t);
			if(sign > 0) {
				switch(modificationMode) {
				case MIXED:
				case QUANTITY:
				case QUANTITY_FIRST:
					modificationByQuantity(t, buys, modificationAmount);
					break;
				case MONEY:
					modificationByAmount(t, buys, modificationAmount);
					break;
				}
			} else if(sign < 0) {
				switch(modificationMode) {
				case MIXED:
				case MONEY:
					modificationByAmount(t, buys, modificationAmount);
					break;
				case QUANTITY:
					modificationByQuantity(t, buys, modificationAmount);
					break;
				case QUANTITY_FIRST:
					try {
						modificationByQuantity(t, buys, modificationAmount);
					} catch(StockAmountReductionException s) {
						modificationByAmount(t, buys, modificationAmount);
					}
					break;
				}
			}
		}
	}

	private void modificationByQuantity(TradeWrapper t, Collection<TradeWrapper> buys, MonetaryAmount modificationAmount) {
		BigDecimal stockQuantity = buys.stream().map(TradeWrapper::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
		if(modificationAmount.isNegative()) {
			//set to zero those below threshold
			List<TradeWrapper> list = new ArrayList<>();
			MonetaryAmount modificationUnit = modificationAmount.divide(stockQuantity);
			for(TradeWrapper u : buys) {
				MonetaryAmount amount = u.getAmount();
				BigDecimal quantity = u.getQuantity();
				MonetaryAmount diff = amount.add(modificationUnit.multiply(quantity));
				if(diff.isNegative()) {
					if(getModificationMode(t) == ModificationMode.QUANTITY_FIRST) {
						//rollback to modificationAmount
						throw new StockAmountReductionException(amount, modificationAmount);
					}
					//force zero amount;
					u.setAmount(amount.subtract(amount));
					modificationAmount = modificationAmount.add(amount);
					stockQuantity = stockQuantity.subtract(quantity);
				} else {
					list.add(u);
				}
			}
			buys = list;
		}
		Iterator<TradeWrapper> iter = buys.iterator();
		modification(t, modificationAmount, stockQuantity, iter);
	}

	private void modificationByAmount(TradeWrapper t, Collection<TradeWrapper> buys, MonetaryAmount modificationAmount) {
		Iterator<TradeWrapper> iter = buys.iterator();
		if(!iter.hasNext()) {
			throw new EmptyPositionModificationException();
		}
		TradeWrapper buy = iter.next();
		MonetaryAmount stockAmount = buy.getAmount();
		while(iter.hasNext()) {
			buy = iter.next();
			stockAmount = stockAmount.add(buy.getAmount());
		}
		MonetaryAmount diff = stockAmount.add(modificationAmount);
		if(diff.isNegative()) {
			throw new StockAmountReductionException(stockAmount, modificationAmount);
		}
		iter = buys.iterator();
		modification(t, modificationAmount, stockAmount, iter);
	}
	
	private void modification(TradeWrapper t, MonetaryAmount modificationAmount, BigDecimal stockQuantity, Iterator<TradeWrapper> iterator) {
		if(!iterator.hasNext()) {
			throw new EmptyPositionModificationException();
		}
		TradeWrapper buy = iterator.next();
		//proportion by quantity
		MonetaryAmount amount = buy.getAmount();
		BigDecimal quantity = buy.getQuantity();
		if(quantity.equals(stockQuantity)) {
			MonetaryAmount diff = amount.add(modificationAmount);
			if(diff.isNegative()) {
				throw new StockAmountReductionException(amount, modificationAmount);
			}
			buy.setAmount(diff);
			Modification modification = new Modification(buy, t, buy.getQuantity(), amount, buy.getAmount());
			this.modifications.add(modification);
		} else {
			MonetaryAmount value = modificationAmount.multiply(quantity);
			try {
				value = value.divide(stockQuantity);
			} catch(ArithmeticException e) {
				double x = value.getNumber().doubleValue() / stockQuantity.doubleValue();
				value = factory.setNumber(x).setCurrency(amount.getCurrency()).create();
			}
			value = value.with(getRounding());
			MonetaryAmount diff = amount.add(value);
			if(diff.isNegative()) {
				throw new StockAmountReductionException(amount, value);
			}
			buy.setAmount(diff);
			modificationAmount = modificationAmount.subtract(value);
			stockQuantity = stockQuantity.subtract(quantity);
			Modification modification = new Modification(buy, t, buy.getQuantity(), amount, buy.getAmount());
			this.modifications.add(modification);
			modification(t, modificationAmount, stockQuantity, iterator);
		}
	}

	private void modification(TradeWrapper t, MonetaryAmount modificationAmount, MonetaryAmount stockAmount, Iterator<TradeWrapper> iterator) {
		if(!iterator.hasNext()) return;
		TradeWrapper buy = iterator.next();
		//proportion by amount
		MonetaryAmount amount = buy.getAmount();
		if(amount.equals(stockAmount)) {
			buy.setAmount(amount.add(modificationAmount));
			Modification modification = new Modification(buy, t, buy.getQuantity(), amount, buy.getAmount());
			this.modifications.add(modification);
		} else {
			double factor = amount.getNumber().doubleValue() / stockAmount.getNumber().doubleValue();
			MonetaryAmount value = modificationAmount.multiply(factor).with(getRounding());
			buy.setAmount(amount.add(value));
			stockAmount = stockAmount.subtract(amount);
			modificationAmount = modificationAmount.subtract(value);
			Modification modification = new Modification(buy, t, buy.getQuantity(), amount, buy.getAmount());
			this.modifications.add(modification);
			modification(t, modificationAmount, stockAmount, iterator);
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
		BigDecimal sellQuantity = sell.getQuantity();
		if(this.isEmpty()) {
			if(sellQuantity.signum() > 0) {
				this.orphanSells.add(sell);
			}
			return;
		}

		MonetaryAmountFactory<?> factory = this.getFactory();
		if(sellQuantity.signum() <= 0) return;

		Strategy strategy = this.getStrategy();
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
			this.addClosedPosition(position);
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
			this.addClosedPosition(position);
		}
		sell.addBuyValues(buy);
	}

	public static class TradeWrapper implements Serializable {
		private static final long serialVersionUID = 1L;

		private final RoundingMode roundingMode = RoundingMode.HALF_UP;
		private int scale;
		private BigDecimal quantityBefore;
		private BigDecimal quantityAfter;

		private BigDecimal quantity;
		private MonetaryAmount amount;
		private final TradeType tradeType;
		private final Object source;
		private final List<TradeWrapper> buyValues;
		private final ModificationMode modificationMode;
		
		public static class Builder {
			private int scale;
			private BigDecimal quantityBefore;
			private BigDecimal quantityAfter;
			private BigDecimal quantity;
			private MonetaryAmount amount;
			private TradeType tradeType;
			private Object source;
			private ModificationMode modificationMode;

			public Builder scale(int scale) {this.scale = scale; return this;}
			public Builder quantityAfter(String quantity) {return quantityAfter(new BigDecimal(quantity));}
			public Builder quantityAfter(long quantity) {return quantityAfter(new BigDecimal(quantity));}
			public Builder quantityAfter(BigDecimal quantity) {this.quantityAfter = quantity; return this;}

			public Builder quantityBefore(String quantity) {return quantityBefore(new BigDecimal(quantity));}
			public Builder quantityBefore(long quantity) {return quantityBefore(new BigDecimal(quantity));}
			public Builder quantityBefore(BigDecimal quantity) {this.quantityBefore = quantity; return this;}

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
			public Builder modificationMode(ModificationMode modificationMode) {this.modificationMode = modificationMode; return this;}

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
					case MODIFICATION_QUANTITY:
						buyValues = Collections.emptyList();
						break;
					case RBT:
						buyValues = new ArrayList<>();
						if(quantity != null) {
							quantity = quantity.abs();
						}
						amount = null;
						break;
				}
				return new TradeWrapper(scale, modificationMode, quantityBefore, quantityAfter, quantity, amount, tradeType, source, buyValues);
			}
		}

		public static Builder tradeType(TradeType tradeType) {
			return new Builder().tradeType(tradeType);
		}

		private TradeWrapper(
			  int scale
			, ModificationMode modificationMode
			, BigDecimal quantityBefore
			, BigDecimal quantityAfter
			, BigDecimal quantity
			, MonetaryAmount amount, TradeType tradeType, Object source, List<TradeWrapper> buyValues) {
			this.scale = scale;
			this.quantityAfter = quantityAfter;
			this.quantityBefore = quantityBefore;
			this.quantity = quantity;
			this.amount = amount;
			this.tradeType = tradeType;
			this.source = source;
			this.buyValues = buyValues;
			this.modificationMode = modificationMode;
		}

		public static TradeWrapper modifyQuantity(BigDecimal quantityBefore, BigDecimal quantityAfter, int scale, Object source) {
			return tradeType(TradeType.MODIFICATION_QUANTITY).scale(scale).quantityBefore(quantityBefore).quantityAfter(quantityAfter).source(source).build();
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
			return modification(amount, null);
		}

		/**
		 * @param amount. Can be negative or positive
		 * @param source
		 * @return
		 */
		public static TradeWrapper modification(MonetaryAmount amount, Object source) {
			return tradeType(TradeType.MODIFICATION).amount(amount).source(source).build();
		}

		public static TradeWrapper modification(MonetaryAmount amount, ModificationMode modificationMode, Object source) {
			return tradeType(TradeType.MODIFICATION).amount(amount).source(source).modificationMode(modificationMode).build();
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

		public int getScale() {
			return scale;
		}

		public void setScale(int scale) {
			this.scale = scale;
		}

		public BigDecimal getQuantityBefore() {
			return quantityBefore;
		}

		public void setQuantityBefore(BigDecimal quantityBefore) {
			this.quantityBefore = quantityBefore;
		}

		public BigDecimal getQuantityAfter() {
			return quantityAfter;
		}

		public void setQuantityAfter(BigDecimal quantityAfter) {
			this.quantityAfter = quantityAfter;
		}

		public RoundingMode getRoundingMode() {
			return roundingMode;
		}

		public ModificationMode getModificationMode() {
			return modificationMode;
		}
	}
	
	public Mode getMode() {
		return mode;
	}

	private Strategy getStrategy() {
		return this.strategy;
	}

	public boolean isEmpty() {
		return getStrategy().isEmpty();
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

	private void addClosedPosition(Position position) {
		if(position == null) return;
		closedPositions.add(position);
		Object sell = position.getSell();
		if(sell == null) return;
		Object buy = position.getBuy();
		this.mapClosedPositionsBySell.computeIfAbsent(sell, o -> new ArrayList<>()).add(position);
		this.mapClosedPositionsByBuy.computeIfAbsent(buy, o -> new ArrayList<>()).add(position);
	}

	/**
	 * @param buyValue a buy position
	 * @return the list of closed positions attached to this buy position
	 */
	public List<Position> getClosedPositionsByBuy(Object buyValue) {
		List<Position> list = this.mapClosedPositionsByBuy.get(buyValue);
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
	}

	/**
	 * @param sellValue a sell position
	 * @return the list of closed positions attached to this sell position
	 */
	public List<Position> getClosedPositionsBySell(Object sellValue) {
		List<Position> list = this.mapClosedPositionsBySell.get(sellValue);
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
	}

	public ModificationMode getModificationMode() {
		return modificationMode;
	}

	private ModificationMode getModificationMode(TradeWrapper t) {
		ModificationMode modificationMode = t.getModificationMode();
		return modificationMode == null ? this.getModificationMode() : modificationMode;
	}
}
