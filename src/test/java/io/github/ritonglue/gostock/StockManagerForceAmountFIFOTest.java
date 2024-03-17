package io.github.ritonglue.gostock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;

import org.junit.Assert;
import org.junit.Test;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;

public class StockManagerForceAmountFIFOTest {
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

	private static StockManager newStockManager() {
		return new StockManager(Mode.FIFO);
	}

	@Test
	public void testForceSell1() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		MonetaryAmount amount = createMoney("100.00");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		TradeWrapper buy = TradeWrapper.buy(createQuantity(3), amount, a);
		TradeWrapper sell1 = TradeWrapper.sell(createQuantity(1), b);
		TradeWrapper sell2 = TradeWrapper.sell(createQuantity(1), c);
		TradeWrapper sell3 = TradeWrapper.sell(createQuantity(1), d);
		list.add(buy);
		list.add(sell1);
		list.add(sell2);
		list.add(sell3);
		StockManager manager = newStockManager();
		manager.addBuySellMoney(buy, sell1, createMoney("33.34"));
		manager.addBuySellMoney(buy, sell2, createMoney("33.34"));
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(BigDecimal.ZERO.compareTo(stock.getQuantity()) == 0);
		List<Position> closedPositions = manager.getClosedPositions();
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(3, closedPositions.size());
		Assert.assertTrue(openedPositions.isEmpty());

		Position position = closedPositions.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertTrue(BigDecimal.ONE.compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("33.34"), position.getAmount());

		position = closedPositions.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertTrue(BigDecimal.ONE.compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("33.34"), position.getAmount());

		position = closedPositions.get(2);
		Assert.assertTrue(position.isClosed());
		Assert.assertTrue(BigDecimal.ONE.compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("33.32"), position.getAmount());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testForceSell2() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		MonetaryAmount amount = createMoney("100.00");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		TradeWrapper buy = TradeWrapper.buy(createQuantity(3), amount, a);
		TradeWrapper sell1 = TradeWrapper.sell(createQuantity(1), b);
		TradeWrapper sell2 = TradeWrapper.sell(createQuantity(1), c);
		TradeWrapper sell3 = TradeWrapper.sell(createQuantity(1), d);
		list.add(buy);
		list.add(sell1);
		list.add(sell2);
		list.add(sell3);
		StockManager manager = newStockManager();
		manager.addBuySellMoney(buy, sell1, createMoney("33.34"));
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(BigDecimal.ZERO.compareTo(stock.getQuantity()) == 0);
		List<Position> closedPositions = manager.getClosedPositions();
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(3, closedPositions.size());
		Assert.assertTrue(openedPositions.isEmpty());

		Position position = closedPositions.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertTrue(BigDecimal.ONE.compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("33.34"), position.getAmount());

		position = closedPositions.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertTrue(BigDecimal.ONE.compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("33.33"), position.getAmount());

		position = closedPositions.get(2);
		Assert.assertTrue(position.isClosed());
		Assert.assertTrue(BigDecimal.ONE.compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("33.33"), position.getAmount());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}
}
