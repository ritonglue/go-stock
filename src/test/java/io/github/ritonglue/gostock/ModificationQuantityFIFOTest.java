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

public class ModificationQuantityFIFOTest {
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
	public void testQuantityMultiplicationSimple() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("33.33"), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("50.00"), b));
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.modifyQuantity(BigDecimal.ONE, createQuantity(3), 6, c));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(createQuantity(15).compareTo(stock.getQuantity()) == 0);;
		Assert.assertEquals(createMoney("83.33"), stock.getAmount());
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(2, openedPositions.size());
		Position position = openedPositions.get(0);
		Assert.assertTrue(createQuantity(9).compareTo(position.getQuantity()) == 0);;
		position = openedPositions.get(1);
		Assert.assertTrue(createQuantity(6).compareTo(position.getQuantity()) == 0);;
	}

	@Test
	public void testReduction() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(7), createMoney("33.33"), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("50.00"), b));
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("10.00"), c));
		SourceTest d = new SourceTest(id++);
		list.add(TradeWrapper.modifyQuantity(createQuantity(3), createQuantity(2), 6, d));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(createQuantity(8).compareTo(stock.getQuantity()) == 0);;
		Assert.assertEquals(createMoney("93.33"), stock.getAmount());
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(3, openedPositions.size());
		Position position = openedPositions.get(0);
		Assert.assertTrue(createQuantity("4.666667").compareTo(position.getQuantity()) == 0);;
		position = openedPositions.get(1);
		Assert.assertTrue(createQuantity("2").compareTo(position.getQuantity()) == 0);;
		position = openedPositions.get(2);
		Assert.assertTrue(createQuantity("1.333333").compareTo(position.getQuantity()) == 0);;

		SourceTest e = new SourceTest(id++);
		manager.add(TradeWrapper.sell(createQuantity(5), e));
		stock = manager.getStock();
		Assert.assertTrue(createQuantity(3).compareTo(stock.getQuantity()) == 0);;
		Assert.assertEquals(createMoney("51.67"), stock.getAmount());
		openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(2, openedPositions.size());
		position = openedPositions.get(0);
		Assert.assertTrue(createQuantity("1.666667").compareTo(position.getQuantity()) == 0);;
		Assert.assertEquals(createMoney("41.67"), position.getAmount());
		position = openedPositions.get(1);
		Assert.assertTrue(createQuantity("1.333333").compareTo(position.getQuantity()) == 0);;
		Assert.assertEquals(createMoney("10.00"), position.getAmount());

		SourceTest f = new SourceTest(id++);
		manager.add(TradeWrapper.sell(createQuantity(3), f));
		stock = manager.getStock();
		Assert.assertEquals(0, stock.getQuantity().signum());
		Assert.assertNull(stock.getAmount());
		openedPositions = manager.getOpenedPositions();
		Assert.assertTrue(openedPositions.isEmpty());
	}
}
