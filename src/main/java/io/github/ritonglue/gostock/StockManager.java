package io.github.ritonglue.gostock;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;

import io.github.ritonglue.gostock.strategy.FIFOStrategy;
import io.github.ritonglue.gostock.strategy.LIFOStrategy;
import io.github.ritonglue.gostock.strategy.PRMPStrategy;
import io.github.ritonglue.gostock.strategy.Strategy;

public class StockManager {
	private final Mode mode;
	
	public StockManager() {
		this(Mode.FIFO);
	}

	public StockManager(Mode mode) {
		this.mode = Objects.requireNonNull(mode, "mode null");
	}

	private static class Context {
		private final MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
		private final PositionLines lines = new PositionLines();
		private final Strategy strategy;

		private Context(Strategy strategy) {
			this.strategy = strategy;
		}

		private PositionLines getLines() {
			return lines;
		}

		public List<Position> getClosedPositions() {
			return lines.getClosedPositions();
		}

		public List<Position> getOpenedPositions() {
			return lines.getOpenedPositions();
		}

		public MonetaryAmountFactory<?> getFactory() {
			return factory;
		}

		public Strategy getStrategy() {
			return strategy;
		}
	}

	private Context getContext() {
		Strategy strategy = null;
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
		Context context = new Context(strategy);
		return context;
	}

	/**
	 * @param trades in ascending time order
	 * @return
	 */
	public PositionLines process(Iterable<Trade> trades) {
		Context context = this.getContext();
		Strategy strategy = context.getStrategy();
		for(Trade t : trades) {
			if(t == null) continue;
			TradeType type = t.getTradeType();
			switch(type) {
			case BUY:
				strategy.add(t);
				break;
			case SELL:
				sell(t, context);
				break;
			case MODIFICATION:
				MonetaryAmount amount = t.getAmount();
				for(Trade tmp : strategy) {
					MonetaryAmount delta = amount.multiply(tmp.getQuantity());
					tmp.setAmount(tmp.getAmount().add(delta));
				}
				break;
			}
		}
		createOpenPosition(context);
		return context.getLines();
	}

	private void sell(Trade sell, Context context) {
		if(sell.getTradeType() != TradeType.SELL) return;
		Strategy strategy = context.getStrategy();
		if(strategy.isEmpty()) return;

		MonetaryAmountFactory<?> factory = context.getFactory();
		List<Position> closedPositions = context.getClosedPositions();

		BigDecimal sellQuantity = sell.getQuantity();
		if(sellQuantity.signum() <= 0) return;

		Trade buy = strategy.peek();
		final BigDecimal stockQuantity = buy.getQuantity();
		final MonetaryAmount stockAmount = buy.getAmount();
		CurrencyUnit currency = stockAmount.getCurrency();
		if(sell.getAmount() == null) {
			//set to zero
			sell.setAmount(factory.setCurrency(currency).setNumber(BigDecimal.ZERO).create());
		}
		int nsign = stockQuantity.compareTo(sellQuantity);
		if(nsign <= 0) {
			//sell everything
			buy.setQuantity(BigDecimal.ZERO);
			buy.setAmount(factory.setCurrency(currency).setNumber(BigDecimal.ZERO).create());
			sell.setAmount(sell.getAmount().add(stockAmount));
			sell.setQuantity(sell.getQuantity().subtract(stockQuantity));
			Position position = new Position(buy.getSource(), sell.getSource(), stockQuantity, stockAmount);
			closedPositions.add(position);
			strategy.remove();
			if(nsign < 0) {
				sell(sell, context);
			}
		} else {
			//partial sell
			buy.setQuantity(stockQuantity.subtract(sellQuantity));
			MonetaryAmount m = stockAmount.multiply(sellQuantity);
			try {
				m = m.divide(stockQuantity);
			} catch(ArithmeticException e) {
				double x = m.getNumber().doubleValue() / stockQuantity.doubleValue();
				m = factory.setCurrency(currency).setNumber(x).create();
			}
			m = m.with(Monetary.getDefaultRounding());
			buy.setAmount(stockAmount.subtract(m));
			sell.setAmount(sell.getAmount().add(m));
			sell.setQuantity(BigDecimal.ZERO);
			Position position = new Position(buy.getSource(), sell.getSource(), sellQuantity, m);
			closedPositions.add(position);
		}
		sell.addBuyValues(buy);
	}

	public static class Trade implements Serializable {
		private static final long serialVersionUID = 1L;

		private BigDecimal quantity;
		private MonetaryAmount amount;
		private final TradeType tradeType;
		private final Object source;
		private final List<Trade> buyValues;
		
		public static class Builder {
			private BigDecimal quantity;
			private MonetaryAmount amount;
			private MonetaryAmount unitAmount;
			private TradeType tradeType;
			private Object source;

			public Builder quantity(BigDecimal quantity) {this.quantity = quantity; return this;}
			public Builder amount(MonetaryAmount amount) {this.amount = amount; return this;}
			public Builder tradeType(TradeType tradeType) {this.tradeType = tradeType; return this;}
			public Builder source(Object source) {this.source = source; return this;}
			public Builder unitAmount(MonetaryAmount unitAmount) {this.unitAmount = unitAmount; return this;}

			public Trade build() {
				if(unitAmount != null && amount == null && quantity != null) {
					amount = unitAmount.multiply(quantity);
				}
				List<Trade> buyValues = null;
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
						if(quantity == null) quantity = BigDecimal.ONE;
						amount = amount.divide(quantity);
						quantity = BigDecimal.ONE;
						source = null;
						break;
				}
				return new Trade(quantity, amount, tradeType, source, buyValues);
			}
		}

		public static Builder tradeType(TradeType tradeType) {
			return new Builder().tradeType(tradeType);
		}

		private Trade(BigDecimal quantity, MonetaryAmount amount, TradeType tradeType, Object source, List<Trade> buyValues) {
			this.quantity = quantity;
			this.amount = amount;
			this.tradeType = tradeType;
			this.source = source;
			this.buyValues = buyValues;
		}
		
		public static Trade buy(BigDecimal quantity, MonetaryAmount amount, Object source) {
			return tradeType(TradeType.BUY).amount(amount).quantity(quantity).source(source).build();
		}

		public static Trade sell(BigDecimal quantity, Object source) {
			return tradeType(TradeType.SELL).quantity(quantity).source(source).build();
		}

		public static Trade modification(BigDecimal quantity, MonetaryAmount amount) {
			return modification(amount.divide(quantity));
		}

		/**
		 * build a modification trade unit
		 * @param amountUnit can be negative or positive
		 * @return
		 */
		public static Trade modification(MonetaryAmount amountUnit) {
			return tradeType(TradeType.MODIFICATION).amount(amountUnit).quantity(BigDecimal.ONE).build();
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

		public List<Trade> getBuyValues() {
			return buyValues;
		}
		
		public void addBuyValues(Trade t) {
			this.buyValues.add(t);
		}
	}
	
	private void createOpenPosition(Context context) {
		Strategy strategy = context.getStrategy();
		List<Position> openedPositions = context.getOpenedPositions();
		for(Trade t : strategy) {
			Position position = new Position(t.getSource(), t.getQuantity(), t.getAmount());
			openedPositions.add(position);
		}
		strategy.clear();
	}

	public Mode getMode() {
		return mode;
	}
}
