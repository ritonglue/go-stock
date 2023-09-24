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

public class SoldExcessTest {
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
	public void testEmptySell() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.sell(quantity, b));
		StockManager manager = newStockManager();
		manager.process(list);
		Assert.assertTrue(manager.getOpenedPositions().isEmpty());
		Assert.assertTrue(manager.getClosedPositions().isEmpty());
		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertEquals(1, orphanSells.size());
		Assert.assertEquals(b, orphanSells.get(0).getSource());
		Assert.assertEquals(quantity, orphanSells.get(0).getQuantity());
	}

	@Test
	public void testEmptyRbt() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.reimbursement(quantity, b));
		StockManager manager = newStockManager();
		manager.process(list);
		Assert.assertTrue(manager.getOpenedPositions().isEmpty());
		Assert.assertTrue(manager.getClosedPositions().isEmpty());
		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertEquals(1, orphanSells.size());
		Assert.assertEquals(b, orphanSells.get(0).getSource());
		Assert.assertEquals(quantity, orphanSells.get(0).getQuantity());
	}

	@Test
	public void testOneSellExcess() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		BigDecimal quantitySell = createQuantity("4");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		list.add(TradeWrapper.sell(quantitySell, b));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertTrue(opened.isEmpty());
		Assert.assertEquals(1, closed.size());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertEquals(1, orphanSells.size());
		Assert.assertEquals(b, orphanSells.get(0).getSource());
		Assert.assertEquals(createQuantity("1"), orphanSells.get(0).getQuantity());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantity, position.getQuantity());
		Assert.assertEquals(createMoney("100.00"), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());
	}

	@Test
	public void testOneSellAndBuyExcess() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		BigDecimal quantitySell = createQuantity("4");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		list.add(TradeWrapper.sell(quantitySell, b));
		list.add(TradeWrapper.buy(createQuantity(7), createMoney("13"), c));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(1, opened.size());
		Assert.assertEquals(1, closed.size());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertEquals(1, orphanSells.size());
		Assert.assertEquals(b, orphanSells.get(0).getSource());
		Assert.assertEquals(createQuantity("1"), orphanSells.get(0).getQuantity());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantity, position.getQuantity());
		Assert.assertEquals(createMoney("100.00"), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney("13"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(c, buy);
	}

	@Test
	public void testOneSellAndBuy2Excess() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		BigDecimal quantitySell = createQuantity("4");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		list.add(TradeWrapper.sell(quantitySell, b));
		list.add(TradeWrapper.buy(createQuantity(7), createMoney("13"), c));
		list.add(TradeWrapper.sell(createQuantity(8), d));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertTrue(opened.isEmpty());
		Assert.assertEquals(2, closed.size());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertEquals(2, orphanSells.size());
		Assert.assertEquals(b, orphanSells.get(0).getSource());
		Assert.assertEquals(createQuantity("1"), orphanSells.get(0).getQuantity());
		Assert.assertEquals(d, orphanSells.get(1).getSource());
		Assert.assertEquals(createQuantity("1"), orphanSells.get(1).getQuantity());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantity, position.getQuantity());
		Assert.assertEquals(createMoney("100.00"), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney("13"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(c, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(d, sell);

		buyValues = list.get(3).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(c, buyValues.get(0).getSource());
	}
}
