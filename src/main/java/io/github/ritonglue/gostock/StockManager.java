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

	private Context newContext() {
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
		Context context = this.newContext();
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
				modification(t, context);
				break;
			case RBT:
				reimbursement(t, context);
				break;
			}
		}
		createOpenPosition(context);
		return context.getLines();
	}

	private void reimbursement(Trade trade, Context context) {
		Strategy strategy = context.getStrategy();
		if(trade.getTradeType() != TradeType.RBT) return;
		BigDecimal quantity = trade.getQuantity();
		if(quantity == null) {
			quantity = BigDecimal.ZERO;
			for(Trade t : strategy) {
				quantity = quantity.add(t.getQuantity());
			}
			trade.setQuantity(quantity);
		}
		sell(trade, context);
	}

	private void modification(Trade t, Context context) {
		if(t.getTradeType() != TradeType.MODIFICATION) return;
		Strategy strategy = context.getStrategy();
		MonetaryAmountFactory<?> factory = context.getFactory();

		MonetaryAmount modificationAmount = t.getAmount();
		CurrencyUnit currency = modificationAmount.getCurrency();
		factory = factory.setCurrency(currency);
		BigDecimal stockQuantity = strategy.getQuantity();

		for(Trade tmp : strategy) {
			BigDecimal quantity = tmp.getQuantity();
			MonetaryAmount amount = t.getAmount();
			if(tmp.getAmount().signum() < 0) {
				throw new RuntimeException("amount is < 0: " + t.getSource());
			}
			MonetaryAmount m = null;
			if(quantity.compareTo(stockQuantity) == 0) {
				m = amount;
			} else {
				m = amount.multiply(quantity);
				try {
					m = m.divide(stockQuantity);
				} catch(ArithmeticException e) {
					double x = m.getNumber().doubleValue() / stockQuantity.doubleValue();
					m = factory.setNumber(x).create();
				}
				m = m.with(Monetary.getDefaultRounding());
			}
			tmp.setAmount(tmp.getAmount().add(m));
			t.setAmount(amount.subtract(m));
			stockQuantity = stockQuantity.subtract(quantity);
		}
	}

	private void sell(Trade sell, Context context) {
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
			sell.setQuantity(sell.getQuantity().subtract(stockQuantity));
			Position position = new Position(buySource, sellSource, stockQuantity, stockAmount, closeCause);
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
				m = factory.setNumber(x).create();
			}
			m = m.with(Monetary.getDefaultRounding());
			buy.setAmount(stockAmount.subtract(m));
			sell.setAmount(sell.getAmount().add(m));
			sell.setQuantity(BigDecimal.ZERO);
			Position position = new Position(buySource, sellSource, sellQuantity, m, closeCause);
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

		/**
		 * @param amount. Can be negative or positive
		 * @return
		 */
		public static Trade modification(MonetaryAmount amount) {
			return tradeType(TradeType.MODIFICATION).amount(amount).build();
		}

		public static Trade reimbursement(BigDecimal quantity, Object source) {
			return tradeType(TradeType.RBT).quantity(quantity).source(source).build();
		}

		public static Trade reimbursement(Object source) {
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
