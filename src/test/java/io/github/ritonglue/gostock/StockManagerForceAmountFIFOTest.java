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

	@Test
	public void testForceModification1() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		MonetaryAmount amount = createMoney("100.00");
		SourceTest a = new SourceTest(id++);
		TradeWrapper buy = TradeWrapper.buy(createQuantity(3), amount, a);
		TradeWrapper modification = TradeWrapper.modification(createMoney("-10.00"));
		list.add(buy);
		list.add(modification);
		StockManager manager = newStockManager();
		//this force modification is ignored
		manager.addBuyModificationMoney(buy, modification, createMoney("-200.00"));
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(createQuantity(3).compareTo(stock.getQuantity()) == 0);
		Assert.assertEquals(createMoney(90), stock.getAmount());

		List<Position> closedPositions = manager.getClosedPositions();
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(1, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertTrue(createQuantity(3).compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("90.00"), position.getAmount());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testForceModification2() {
		int id = 1;
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		TradeWrapper buy1 = TradeWrapper.buy(createQuantity(3), createMoney("123.11"), a);
		TradeWrapper buy2 = TradeWrapper.buy(createQuantity(4), createMoney("12.99"), b);
		TradeWrapper modification = TradeWrapper.modification(createMoney("-10.00"));
		List<TradeWrapper> list = List.of(buy1, buy2, modification);
		StockManager manager = newStockManager();
		manager.addBuyModificationMoney(buy1, modification, createMoney("-5.00"));
		manager.addBuyModificationMoney(buy2, modification, createMoney("-5.00"));
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(createQuantity(7).compareTo(stock.getQuantity()) == 0);
		Assert.assertEquals(createMoney("126.10"), stock.getAmount());

		List<Position> closedPositions = manager.getClosedPositions();
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(2, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertTrue(createQuantity(3).compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("118.11"), position.getAmount());

		position = openedPositions.get(1);
		Assert.assertTrue(position.isOpened());
		Assert.assertTrue(createQuantity(4).compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("7.99"), position.getAmount());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testForceModification3() {
		int id = 1;
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		TradeWrapper buy1 = TradeWrapper.buy(createQuantity(3), createMoney("123.11"), a);
		TradeWrapper buy2 = TradeWrapper.buy(createQuantity(4), createMoney("12.99"), b);
		TradeWrapper modification = TradeWrapper.modification(createMoney("-10.00"));
		List<TradeWrapper> list = List.of(buy1, buy2, modification);
		StockManager manager = newStockManager();
		manager.addBuyModificationMoney(buy1, modification, createMoney("-5.00"));
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(createQuantity(7).compareTo(stock.getQuantity()) == 0);
		Assert.assertEquals(createMoney("126.10"), stock.getAmount());

		List<Position> closedPositions = manager.getClosedPositions();
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(2, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertTrue(createQuantity(3).compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("118.11"), position.getAmount());

		position = openedPositions.get(1);
		Assert.assertTrue(position.isOpened());
		Assert.assertTrue(createQuantity(4).compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("7.99"), position.getAmount());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test(expected = IllegalStateException.class)
	public void testForceModification4() {
		int id = 1;
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		TradeWrapper buy1 = TradeWrapper.buy(createQuantity(3), createMoney("123.11"), a);
		TradeWrapper buy2 = TradeWrapper.buy(createQuantity(4), createMoney("12.99"), b);
		TradeWrapper modification = TradeWrapper.modification(createMoney("-10.00"));
		List<TradeWrapper> list = List.of(buy1, buy2, modification);
		StockManager manager = newStockManager();
		//overfull modification
		manager.addBuyModificationMoney(buy1, modification, createMoney("-5.00"));
		manager.addBuyModificationMoney(buy2, modification, createMoney("-6.00"));
		manager.process(list);
	}

	@Test
	public void testForceModification5() {
		int id = 1;
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		TradeWrapper buy1 = TradeWrapper.buy(createQuantity(3), createMoney("123.11"), a);
		TradeWrapper buy2 = TradeWrapper.buy(createQuantity(4), createMoney("12.99"), b);
		TradeWrapper modification = TradeWrapper.modification(createMoney("10.00"));
		List<TradeWrapper> list = List.of(buy1, buy2, modification);
		StockManager manager = newStockManager();
		manager.addBuyModificationMoney(buy1, modification, createMoney("5.00"));
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(createQuantity(7).compareTo(stock.getQuantity()) == 0);
		Assert.assertEquals(createMoney("146.10"), stock.getAmount());

		List<Position> closedPositions = manager.getClosedPositions();
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(2, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertTrue(createQuantity(3).compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("128.11"), position.getAmount());

		position = openedPositions.get(1);
		Assert.assertTrue(position.isOpened());
		Assert.assertTrue(createQuantity(4).compareTo(position.getQuantity()) == 0);
		Assert.assertEquals(createMoney("17.99"), position.getAmount());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}
}
