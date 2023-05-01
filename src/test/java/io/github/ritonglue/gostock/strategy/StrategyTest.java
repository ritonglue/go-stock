package io.github.ritonglue.gostock.strategy;

import java.math.BigDecimal;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;

import org.junit.Assert;
import org.junit.Test;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;
import io.github.ritonglue.gostock.StockManagerFIFOTest;

public class StrategyTest {
	private final CurrencyUnit cu = Monetary.getCurrency("EUR");

	private MonetaryAmount createMoney(String value) {
		MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
		return factory.setCurrency(cu).setNumber(StockManagerFIFOTest.createQuantity(value)).create();
	}

	private MonetaryAmount createMoney(long value) {
		MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
		return factory.setCurrency(cu).setNumber(value).create();
	}

	@Test
	public void testFIFO() {
		Strategy q = new FIFOStrategy();
		Assert.assertTrue(q.isEmpty());
		TradeWrapper a = TradeWrapper.buy(new BigDecimal(1), createMoney(10), null);
		q.add(a);
		Assert.assertEquals(1, q.size());
		Assert.assertEquals(a, q.peek());
		Assert.assertEquals(new BigDecimal(1), q.getQuantity());

		TradeWrapper b = TradeWrapper.buy(new BigDecimal(2), createMoney(20), null);
		q.add(b);
		Assert.assertEquals(2, q.size());
		Assert.assertEquals(a, q.peek());
		Assert.assertEquals(new BigDecimal(3), q.getQuantity());

		TradeWrapper c = TradeWrapper.buy(new BigDecimal(3), createMoney(30), null);
		q.add(c);
		Assert.assertEquals(3, q.size());
		Assert.assertEquals(a, q.peek());
		Assert.assertEquals(new BigDecimal(6), q.getQuantity());

		q.remove();
		Assert.assertEquals(2, q.size());
		Assert.assertEquals(b, q.peek());

		q.remove();
		Assert.assertEquals(1, q.size());
		Assert.assertEquals(c, q.peek());

		q.remove();
		Assert.assertTrue(q.isEmpty());
	}

	@Test
	public void testLIFO() {
		Strategy q = new LIFOStrategy();
		Assert.assertTrue(q.isEmpty());
		TradeWrapper a = TradeWrapper.buy(new BigDecimal(1), createMoney(10), null);
		q.add(a);
		Assert.assertEquals(1, q.size());
		Assert.assertEquals(a, q.peek());
		Assert.assertEquals(new BigDecimal(1), q.getQuantity());

		TradeWrapper b = TradeWrapper.buy(new BigDecimal(2), createMoney(20), null);
		q.add(b);
		Assert.assertEquals(2, q.size());
		Assert.assertEquals(b, q.peek());
		Assert.assertEquals(new BigDecimal(3), q.getQuantity());

		TradeWrapper c = TradeWrapper.buy(new BigDecimal(3), createMoney(30), null);
		q.add(c);
		Assert.assertEquals(3, q.size());
		Assert.assertEquals(c, q.peek());
		Assert.assertEquals(new BigDecimal(6), q.getQuantity());

		q.remove();
		Assert.assertEquals(2, q.size());
		Assert.assertEquals(b, q.peek());

		q.remove();
		Assert.assertEquals(1, q.size());
		Assert.assertEquals(a, q.peek());

		q.remove();
		Assert.assertTrue(q.isEmpty());
	}

	@Test
	public void testPRMP() {
		Strategy q = new PRMPStrategy();
		Assert.assertTrue(q.isEmpty());
		TradeWrapper a = TradeWrapper.buy(new BigDecimal(1), createMoney(10), null);
		q.add(a);
		Assert.assertEquals(1, q.size());
		Assert.assertEquals(new BigDecimal(1), q.getQuantity());
		Assert.assertEquals(new BigDecimal(1), q.peek().getQuantity());

		TradeWrapper b = TradeWrapper.buy(new BigDecimal(2), createMoney(20), null);
		q.add(b);
		Assert.assertEquals(1, q.size());
		Assert.assertEquals(new BigDecimal(3), q.peek().getQuantity());
		Assert.assertEquals(new BigDecimal(3), q.getQuantity());

		TradeWrapper c = TradeWrapper.buy(new BigDecimal(3), createMoney(30), null);
		q.add(c);
		Assert.assertEquals(1, q.size());
		Assert.assertEquals(new BigDecimal(6), q.peek().getQuantity());
		Assert.assertEquals(new BigDecimal(6), q.getQuantity());

		q.remove();
		Assert.assertTrue(q.isEmpty());
	}
}
