package io.github.ritonglue.gostock;

import java.math.BigDecimal;
import java.util.List;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;

import org.junit.Assert;
import org.junit.Test;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;

public class OtherTest {
	private final CurrencyUnit cu = Monetary.getCurrency("EUR");

	private MonetaryAmount createMoney(String value) {
		MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
		return factory.setCurrency(cu).setNumber(createQuantity(value)).create();
	}

	private MonetaryAmount createMoney(long value) {
		MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
		return factory.setCurrency(cu).setNumber(value).create();
	}

	public static BigDecimal createQuantity(String value) {
		return new BigDecimal(value);
	}

	static BigDecimal createQuantity(int value) {
		switch(value) {
		case 1: return BigDecimal.ONE;
		case 0: return BigDecimal.ZERO;
		case 10: return BigDecimal.TEN;
		default: return new BigDecimal(value);
		}
	}

	private static class Transaction {
		private final int id;
		private final BigDecimal quantity;
		private final MonetaryAmount amount;

		Transaction(int id, long quantity, long unitAmount) {
			this.id = id;
			this.quantity = new BigDecimal(quantity);
			this.amount = Monetary.getDefaultAmountFactory().
			setCurrency("EUR").setNumber(new BigDecimal(unitAmount)).create()
			.multiply(quantity);
		}

		Transaction(int id, long quantity) {
			this.id = id;
			this.quantity = new BigDecimal(quantity);
			this.amount = null;
		}

		public BigDecimal getQuantity() {
			return quantity;
		}

		public MonetaryAmount getAmount() {
			return amount;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj == this) return true;
			if(!(obj instanceof Transaction)) return false;
			Transaction a = (Transaction) obj;
			return a.id == this.id;
		}

		@Override
		public int hashCode() {
			return id;
		}
	}

	/**
	 * https://en.wikipedia.org/wiki/FIFO_and_LIFO_accounting
	 */
	@Test
	public void testFIFO() {
		int id = 1;
		Transaction a = new Transaction(id++, 100, 50);
		Transaction b = new Transaction(id++, 125, 55);
		Transaction c = new Transaction(id++, 75, 59);
		Transaction d = new Transaction(id++, -210);
		StockManager manager = new StockManager(Mode.FIFO);
		manager.add(TradeWrapper.buy(a.getQuantity(), a.getAmount(), a));
		manager.add(TradeWrapper.buy(b.getQuantity(), b.getAmount(), b));
		manager.add(TradeWrapper.buy(c.getQuantity(), c.getAmount(), c));
		manager.add(TradeWrapper.sell(d.getQuantity(), d));
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertEquals(2, closedPositions.size());
		Assert.assertEquals(2, openedPositions.size());
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(new BigDecimal(90), stock.getQuantity());
		Assert.assertEquals(createMoney(5250), stock.getAmount());
		Position position = closedPositions.get(0);
		Assert.assertEquals(new BigDecimal(100), position.getQuantity());
		Assert.assertEquals(createMoney(5000), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
		Assert.assertEquals(d, position.getSell());
		position = closedPositions.get(1);
		Assert.assertEquals(createQuantity(110), position.getQuantity());
		Assert.assertEquals(createMoney(110*55), position.getAmount());
		Assert.assertEquals(b, position.getBuy());
		Assert.assertEquals(d, position.getSell());

		position = openedPositions.get(0);
		Assert.assertEquals(new BigDecimal(15), position.getQuantity());
		Assert.assertEquals(createMoney(15*55), position.getAmount());
		Assert.assertEquals(b, position.getBuy());
		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(75), position.getQuantity());
		Assert.assertEquals(createMoney(59*75), position.getAmount());
		Assert.assertEquals(c, position.getBuy());

		BigDecimal oldQuantity = stock.getQuantity();
		MonetaryAmount oldAmount = stock.getAmount();
		MonetaryAmount delta = oldAmount.multiply(2);
		manager.add(TradeWrapper.modification(delta));
		stock = manager.getStock();
		Assert.assertEquals(oldQuantity, stock.getQuantity());
		Assert.assertEquals(oldAmount.add(delta), stock.getAmount());

		Transaction f = new Transaction(id++, 0, 0);
		manager.add(TradeWrapper.reimbursement(f));
		openedPositions = manager.getOpenedPositions();
		closedPositions = manager.getClosedPositions();
		Assert.assertEquals(4, closedPositions.size());
		Assert.assertTrue(openedPositions.isEmpty());
		stock = manager.getStock();
		Assert.assertEquals(BigDecimal.ZERO, stock.getQuantity());
		Assert.assertNull(stock.getAmount());
	}

	/**
	 * https://en.wikipedia.org/wiki/FIFO_and_LIFO_accounting
	 */
	@Test
	public void testLIFO() {
		int id = 1;
		Transaction a = new Transaction(id++, 100, 50);
		Transaction b = new Transaction(id++, 125, 55);
		Transaction c = new Transaction(id++, 75, 59);
		Transaction d = new Transaction(id++, -210);
		StockManager manager = new StockManager(Mode.LIFO);
		manager.add(TradeWrapper.buy(a.getQuantity(), a.getAmount(), a));
		manager.add(TradeWrapper.buy(b.getQuantity(), b.getAmount(), b));
		manager.add(TradeWrapper.buy(c.getQuantity(), c.getAmount(), c));
		manager.add(TradeWrapper.sell(d.getQuantity(), d));
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertEquals(3, closedPositions.size());
		Assert.assertEquals(1, openedPositions.size());
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(new BigDecimal(90), stock.getQuantity());
		Assert.assertEquals(createMoney(90*50), stock.getAmount());
		Position position = closedPositions.get(0);
		Assert.assertEquals(new BigDecimal(75), position.getQuantity());
		Assert.assertEquals(createMoney(75*59), position.getAmount());
		Assert.assertEquals(c, position.getBuy());
		Assert.assertEquals(d, position.getSell());
		position = closedPositions.get(1);
		Assert.assertEquals(createQuantity(125), position.getQuantity());
		Assert.assertEquals(createMoney(125*55), position.getAmount());
		Assert.assertEquals(b, position.getBuy());
		Assert.assertEquals(d, position.getSell());
		position = closedPositions.get(2);
		Assert.assertEquals(createQuantity(10), position.getQuantity());
		Assert.assertEquals(createMoney(10*50), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
		Assert.assertEquals(d, position.getSell());

		position = openedPositions.get(0);
		Assert.assertEquals(new BigDecimal(90), position.getQuantity());
		Assert.assertEquals(createMoney(90*50), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
	}

	@Test
	public void testPRMP() {
		int id = 1;
		Transaction a = new Transaction(id++, 100, 50);
		Transaction b = new Transaction(id++, 125, 55);
		Transaction c = new Transaction(id++, 75, 59);
		Transaction d = new Transaction(id++, -210);
		StockManager manager = new StockManager(Mode.PRMP);
		manager.add(TradeWrapper.buy(a.getQuantity(), a.getAmount(), a));
		manager.add(TradeWrapper.buy(b.getQuantity(), b.getAmount(), b));
		manager.add(TradeWrapper.buy(c.getQuantity(), c.getAmount(), c));
		manager.add(TradeWrapper.sell(d.getQuantity(), d));
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertEquals(1, closedPositions.size());
		Assert.assertEquals(1, openedPositions.size());
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(new BigDecimal(90), stock.getQuantity());
		Assert.assertEquals(createMoney(4890), stock.getAmount());

		Position position = closedPositions.get(0);
		Assert.assertEquals(new BigDecimal(210), position.getQuantity());
		Assert.assertEquals(createMoney(11410), position.getAmount());

		position = openedPositions.get(0);
		Assert.assertEquals(new BigDecimal(90), position.getQuantity());
		Assert.assertEquals(createMoney(4890), position.getAmount());
	}
}
