package io.github.ritonglue.gostock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;

import org.junit.Assert;
import org.junit.Test;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;

public class StockManagerFIFOTest {
	private final CurrencyUnit cu = Monetary.getCurrency("EUR");
	
	@Test
	public void testQueue() {
		Queue<Integer> q = new LinkedList<>();
		q.add(1);
		q.add(2);
		q.add(3);
		q.add(4);
		Assert.assertEquals(1, (int) q.peek());
		q.remove();
		Assert.assertEquals(2, (int) q.peek());
	}
	
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
	public void testOneBuy() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		StockManager manager = newStockManager();
		manager.process(list);
		Assert.assertTrue(manager.getClosedPositions().isEmpty());
		List<Position> positions = manager.getOpenedPositions();
		Assert.assertEquals(1, positions.size());
		Position position = positions.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(quantity, position.getQuantity());
		Assert.assertEquals(amount, position.getAmount());
		SourceTest b = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, b);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testOneFullSell() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		list.add(TradeWrapper.sell(quantity, b));
		StockManager manager = newStockManager();
		manager.process(list);
		Assert.assertTrue(manager.getOpenedPositions().isEmpty());
		List<Position> positions = manager.getClosedPositions();
		Assert.assertEquals(1, positions.size());
		Position position = positions.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantity, position.getQuantity());
		Assert.assertEquals(amount, position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testOnePartialSell() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		BigDecimal quantitySell = createQuantity("2");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		list.add(TradeWrapper.sell(quantitySell, b));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(1, opened.size());
		Assert.assertEquals(1, closed.size());

		Position position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), BigDecimal.ONE);
		Assert.assertEquals(position.getAmount(), createMoney("33.33"));
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);

		position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), quantitySell);
		Assert.assertEquals(position.getAmount(), createMoney("66.67"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testFullMultiPartialSell() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		BigDecimal quantitySell = createQuantity("2");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		list.add(TradeWrapper.sell(quantitySell, b));
		list.add(TradeWrapper.sell(BigDecimal.ONE, c));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertTrue(opened.isEmpty());
		Assert.assertEquals(2, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), quantitySell);
		Assert.assertEquals(position.getAmount(), createMoney("66.67"));
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), BigDecimal.ONE);
		Assert.assertEquals(position.getAmount(), createMoney("33.33"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		buyValues = list.get(2).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testMultiBuy1() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("100.00"), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.sell(createQuantity(4), c));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(1, opened.size());
		Assert.assertEquals(2, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney(100));
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(1));
		Assert.assertEquals(position.getAmount(), createMoney("1.29"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney("3.88"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);

		List<TradeWrapper> buyValues = list.get(2).getBuyValues();
		Assert.assertEquals(2, buyValues.size());
		Assert.assertEquals(b, buyValues.get(0).getSource());
		Assert.assertEquals(a, buyValues.get(1).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testMultiBuy2() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.sell(createQuantity(4), c));
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(200), d));
		list.add(TradeWrapper.sell(createQuantity(3), e));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(1, opened.size());
		Assert.assertEquals(3, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney(100));
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(1));
		Assert.assertEquals(position.getAmount(), createMoney("1.29"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(2);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney("3.88"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), createQuantity(7));
		Assert.assertEquals(position.getAmount(), createMoney(200));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);

		List<TradeWrapper> buyValues = list.get(2).getBuyValues();
		Assert.assertEquals(2, buyValues.size());
		Assert.assertEquals(b, buyValues.get(0).getSource());
		Assert.assertEquals(a, buyValues.get(1).getSource());

		buyValues = list.get(4).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(b, buyValues.get(0).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testMultiBuy3() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		SourceTest f = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.sell(createQuantity(4), c));//closes 3 + 1
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(200), d));
		list.add(TradeWrapper.sell(createQuantity(9), e));//closes 3 + 6. 1 stays opened
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("33.33"), f));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(2, opened.size());
		Assert.assertEquals(4, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney(100));
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(1));
		Assert.assertEquals(position.getAmount(), createMoney("1.29"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(2);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney("3.88"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = closed.get(3);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(6));
		Assert.assertEquals(position.getAmount(), createMoney("171.43"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), createQuantity(1));
		Assert.assertEquals(position.getAmount(), createMoney("28.57"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);

		position = opened.get(1);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), createQuantity(2));
		Assert.assertEquals(position.getAmount(), createMoney("33.33"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(f, buy);

		List<TradeWrapper> buyValues = list.get(2).getBuyValues();
		Assert.assertEquals(2, buyValues.size());
		Assert.assertEquals(b, buyValues.get(0).getSource());
		Assert.assertEquals(a, buyValues.get(1).getSource());

		buyValues = list.get(4).getBuyValues();
		Assert.assertEquals(2, buyValues.size());
		Assert.assertEquals(d, buyValues.get(0).getSource());
		Assert.assertEquals(b, buyValues.get(1).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testMultiBuy4() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		SourceTest f = new SourceTest(id++);
		SourceTest g = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.sell(createQuantity(4), c));//closes 3 + 1; 3 stays opened
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(200), d));
		list.add(TradeWrapper.sell(createQuantity(9), e));//closes 3 + 6. 1 stays opened
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("33.33"), f));
		list.add(TradeWrapper.sell(createQuantity(3), g));//closes 1 + 2
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertTrue(opened.isEmpty());
		Assert.assertEquals(6, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney(100));
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(1));
		Assert.assertEquals(position.getAmount(), createMoney("1.29"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(2);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney("3.88"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = closed.get(3);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(6));
		Assert.assertEquals(position.getAmount(), createMoney("171.43"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = closed.get(4);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(1));
		Assert.assertEquals(position.getAmount(), createMoney("28.57"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(g, sell);

		position = closed.get(5);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(2), position.getQuantity());
		Assert.assertEquals(createMoney("33.33"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(f, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(g, sell);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void modificationSimple() {
		StockManager manager = newStockManager();
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		list.add(TradeWrapper.modification(createMoney(-30)));

		manager.process(list);
		Assert.assertTrue(manager.getClosedPositions().isEmpty());
		List<Position> positions = manager.getOpenedPositions();
		Assert.assertEquals(1, positions.size());
		Position position = positions.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(quantity, position.getQuantity());
		Assert.assertEquals(createMoney(70), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void modification() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
		BigDecimal quantitySell = createQuantity(2);
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		list.add(TradeWrapper.sell(quantitySell, b));
		list.add(TradeWrapper.modification(createMoney(-10)));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(1, opened.size());
		Assert.assertEquals(1, closed.size());

		Position position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(BigDecimal.ONE, position.getQuantity());
		Assert.assertEquals(createMoney("23.33"), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);

		position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantitySell, position.getQuantity());
		Assert.assertEquals(createMoney("66.67"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testFullMultiPartialSellModification() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
		BigDecimal quantitySell = createQuantity(2);
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(quantity, amount, a));
		list.add(TradeWrapper.sell(quantitySell, b));
		list.add(TradeWrapper.modification(createMoney(-10)));
		list.add(TradeWrapper.sell(BigDecimal.ONE, c));

		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertTrue(opened.isEmpty());
		Assert.assertEquals(2, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), quantitySell);
		Assert.assertEquals(position.getAmount(), createMoney("66.67"));
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), BigDecimal.ONE);
		Assert.assertEquals(position.getAmount(), createMoney("23.33"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testModificationMultiBuy2() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.sell(createQuantity(4), c));
		list.add(TradeWrapper.modification(createMoney("-1.50")));
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(200), d));
		list.add(TradeWrapper.sell(createQuantity(3), e));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(1, opened.size());
		Assert.assertEquals(3, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney(100));
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(1));
		Assert.assertEquals(position.getAmount(), createMoney("1.29"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(2);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), createQuantity(3));
		Assert.assertEquals(position.getAmount(), createMoney("2.38"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), createQuantity(7));
		Assert.assertEquals(position.getAmount(), createMoney(200));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	/**
	 * https://www.l-expert-comptable.com/a/531806-la-valorisation-des-stocks-par-cout-le-moyen-pondere-ou-la-methode-fifo.html
	 */
	@Test
	public void testSimple2buy1OneSell() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(150), createMoney(150*100), a));
		list.add(TradeWrapper.buy(createQuantity(200), createMoney(200*150), b));
		list.add(TradeWrapper.sell(createQuantity(250), c));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(1, opened.size());
		Assert.assertEquals(2, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(150), position.getQuantity());
		Assert.assertEquals(createMoney(150*100), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(100), position.getQuantity());
		Assert.assertEquals(createMoney(100*150), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(100), position.getQuantity());
		Assert.assertEquals(createMoney(100*150), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testSimpleRbt() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(100), a));
		list.add(TradeWrapper.reimbursement(createQuantity(7), b));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertTrue(openedPositions.isEmpty());
		Assert.assertEquals(1, closedPositions.size());
		Position position = closedPositions.get(0);
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney(100), position.getAmount());
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(CloseCause.RBT, position.getCloseCause());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testSimpleRbtNullQuantity() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(100), a));
		list.add(TradeWrapper.reimbursement(b));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertTrue(openedPositions.isEmpty());
		Assert.assertEquals(1, closedPositions.size());
		Position position = closedPositions.get(0);
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney(100), position.getAmount());
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(CloseCause.RBT, position.getCloseCause());
		SourceTest buy = position.getBuy(SourceTest.class);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(a, buy);
		Assert.assertEquals(b, sell);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testBuySellRbt() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(100), a));
		list.add(TradeWrapper.sell(createQuantity(3), b));
		list.add(TradeWrapper.reimbursement(c));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertTrue(openedPositions.isEmpty());
		Assert.assertEquals(2, closedPositions.size());

		Position position = closedPositions.get(0);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("42.86"), position.getAmount());
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(CloseCause.SELL, position.getCloseCause());
		SourceTest buy = position.getBuy(SourceTest.class);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(a, buy);
		Assert.assertEquals(b, sell);

		position = closedPositions.get(1);
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney("57.14"), position.getAmount());
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(CloseCause.RBT, position.getCloseCause());
		buy = position.getBuy(SourceTest.class);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(a, buy);
		Assert.assertEquals(c, sell);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testBuySellBuyRbt() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(100), a));
		list.add(TradeWrapper.sell(createQuantity(3), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(160), c));
		list.add(TradeWrapper.reimbursement(d));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertTrue(openedPositions.isEmpty());
		Assert.assertEquals(3, closedPositions.size());

		Position position = closedPositions.get(0);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("42.86"), position.getAmount());
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(CloseCause.SELL, position.getCloseCause());
		SourceTest buy = position.getBuy(SourceTest.class);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(a, buy);
		Assert.assertEquals(b, sell);

		position = closedPositions.get(1);
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney("57.14"), position.getAmount());
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(CloseCause.RBT, position.getCloseCause());
		buy = position.getBuy(SourceTest.class);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(a, buy);
		Assert.assertEquals(d, sell);

		position = closedPositions.get(2);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(160), position.getAmount());
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(CloseCause.RBT, position.getCloseCause());
		buy = position.getBuy(SourceTest.class);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, buy);
		Assert.assertEquals(d, sell);

		List<TradeWrapper> buyValues = list.get(1).getBuyValues();
		Assert.assertEquals(1, buyValues.size());
		Assert.assertEquals(a, buyValues.get(0).getSource());

		buyValues = list.get(3).getBuyValues();
		Assert.assertEquals(2, buyValues.size());
		Assert.assertEquals(c, buyValues.get(0).getSource());
		Assert.assertEquals(a, buyValues.get(1).getSource());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testModification2() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(8), createMoney(100), a));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(160), b));
		list.add(TradeWrapper.modification(createMoney(-50)));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(2, openedPositions.size());
		Assert.assertEquals(createMoney(210)
			, openedPositions.stream().map(o -> o.getAmount())
			.reduce(createMoney(0), MonetaryAmount::add));

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(8), position.getQuantity());
		Assert.assertEquals(createMoney("80.77"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("129.23"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testModification3() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(8), createMoney(100), a));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(160), b));
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(90), c));
		list.add(TradeWrapper.modification(createMoney(-79)));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(3, openedPositions.size());
		Assert.assertEquals(createQuantity(8+3+7), stock.getQuantity());
		Assert.assertEquals(createMoney(100+160+90-79), stock.getAmount());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(8), position.getQuantity());
		Assert.assertEquals(createMoney("77.43"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("123.88"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);

		position = openedPositions.get(2);
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney("69.69"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(c, buy);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testStepByStep() {
		int id = 1;
		SourceTest a = new SourceTest(id++);
		StockManager manager = newStockManager();
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertTrue(openedPositions.isEmpty());
		Assert.assertTrue(closedPositions.isEmpty());
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createQuantity(0), stock.getQuantity());
		Assert.assertNull(stock.getAmount());

		manager.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		openedPositions = manager.getOpenedPositions();
		closedPositions = manager.getClosedPositions();
		stock = manager.getStock();
		Assert.assertEquals(createQuantity(3), stock.getQuantity());
		Assert.assertEquals(createMoney(100), stock.getAmount());
		Assert.assertEquals(1, openedPositions.size());
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(createQuantity(3), openedPositions.get(0).getQuantity());
		Assert.assertEquals(createMoney(100), openedPositions.get(0).getAmount());

		SourceTest b = new SourceTest(id++);
		manager.add(TradeWrapper.buy(createQuantity(9), createMoney(50), b));
		openedPositions = manager.getOpenedPositions();
		closedPositions = manager.getClosedPositions();
		stock = manager.getStock();
		Assert.assertEquals(createQuantity(12), stock.getQuantity());
		Assert.assertEquals(createMoney(150), stock.getAmount());
		Assert.assertEquals(2, openedPositions.size());
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(createQuantity(3), openedPositions.get(0).getQuantity());
		Assert.assertEquals(createMoney(100), openedPositions.get(0).getAmount());
		Assert.assertEquals(a, openedPositions.get(0).getBuy());
		Assert.assertEquals(createQuantity(9), openedPositions.get(1).getQuantity());
		Assert.assertEquals(createMoney(50), openedPositions.get(1).getAmount());
		Assert.assertEquals(b, openedPositions.get(1).getBuy());

		SourceTest c = new SourceTest(id++);
		manager.add(TradeWrapper.sell(createQuantity(4), c));
		openedPositions = manager.getOpenedPositions();
		closedPositions = manager.getClosedPositions();
		stock = manager.getStock();
		Assert.assertEquals(createQuantity(8), stock.getQuantity());
		Assert.assertEquals(createMoney("44.44"), stock.getAmount());
		Assert.assertEquals(1, openedPositions.size());
		Assert.assertEquals(2, closedPositions.size());
		Assert.assertEquals(createQuantity(8), openedPositions.get(0).getQuantity());
		Assert.assertEquals(createMoney("44.44"), openedPositions.get(0).getAmount());
		Assert.assertEquals(b, openedPositions.get(0).getBuy());

		Assert.assertEquals(createQuantity(3), closedPositions.get(0).getQuantity());
		Assert.assertEquals(createMoney(100), closedPositions.get(0).getAmount());
		Assert.assertEquals(a, closedPositions.get(0).getBuy());
		Assert.assertEquals(c, closedPositions.get(0).getSell());

		Assert.assertEquals(createQuantity(1), closedPositions.get(1).getQuantity());
		Assert.assertEquals(createMoney("5.56"), closedPositions.get(1).getAmount());
		Assert.assertEquals(b, closedPositions.get(1).getBuy());
		Assert.assertEquals(c, closedPositions.get(1).getSell());

		SourceTest d = new SourceTest(id++);
		manager.add(TradeWrapper.buy(createQuantity(4), createMoney(77), d));
		openedPositions = manager.getOpenedPositions();
		closedPositions = manager.getClosedPositions();
		stock = manager.getStock();
		Assert.assertEquals(createQuantity(12), stock.getQuantity());
		Assert.assertEquals(createMoney("121.44"), stock.getAmount());
		Assert.assertEquals(2, openedPositions.size());
		Assert.assertEquals(2, closedPositions.size());
		Assert.assertEquals(b, openedPositions.get(0).getBuy());
		Assert.assertEquals(createQuantity(8), openedPositions.get(0).getQuantity());
		Assert.assertEquals(createMoney("44.44"), openedPositions.get(0).getAmount());
		Assert.assertEquals(d, openedPositions.get(1).getBuy());
		Assert.assertEquals(createQuantity(4), openedPositions.get(1).getQuantity());
		Assert.assertEquals(createMoney(77), openedPositions.get(1).getAmount());

		manager.add(TradeWrapper.modification(createMoney(-100)));
		stock = manager.getStock();
		openedPositions = manager.getOpenedPositions();
		closedPositions = manager.getClosedPositions();
		Assert.assertEquals(createQuantity(12), stock.getQuantity());
		Assert.assertEquals(createMoney("21.44"), stock.getAmount());
		Assert.assertEquals(2, openedPositions.size());
		Assert.assertEquals(2, closedPositions.size());
		Assert.assertEquals(b, openedPositions.get(0).getBuy());
		Assert.assertEquals(d, openedPositions.get(1).getBuy());
		Assert.assertEquals(createQuantity(8), openedPositions.get(0).getQuantity());
		Assert.assertEquals(createMoney("7.85"), openedPositions.get(0).getAmount());
		Assert.assertEquals(createQuantity(4), openedPositions.get(1).getQuantity());
		Assert.assertEquals(createMoney("13.59"), openedPositions.get(1).getAmount());

		SourceTest e = new SourceTest(id++);
		manager.add(TradeWrapper.sell(createQuantity(12), e));
		stock = manager.getStock();
		openedPositions = manager.getOpenedPositions();
		closedPositions = manager.getClosedPositions();
		Assert.assertTrue(openedPositions.isEmpty());
		Assert.assertEquals(4, closedPositions.size());
		Assert.assertEquals(createQuantity(0), stock.getQuantity());
		Assert.assertNull(stock.getAmount());

		Assert.assertEquals(b, closedPositions.get(2).getBuy());
		Assert.assertEquals(e, closedPositions.get(2).getSell());
		Assert.assertEquals(d, closedPositions.get(3).getBuy());
		Assert.assertEquals(e, closedPositions.get(3).getSell());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testNullAmountSingleModification() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(8), createMoney(0), a));
		list.add(TradeWrapper.modification(createMoney(-50)));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(1, openedPositions.size());
		Assert.assertEquals(createMoney(-50), stock.getAmount());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(8), position.getQuantity());
		Assert.assertEquals(createMoney(-50), position.getAmount());
		Assert.assertTrue(position.isOpened());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testNullAmountModification() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(8), createMoney(0), a));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), b));
		list.add(TradeWrapper.modification(createMoney(+50)));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		TradeWrapper stock = manager.getStock();
		Assert.assertTrue(closedPositions.isEmpty());
		Assert.assertEquals(2, openedPositions.size());
		Assert.assertEquals(createMoney(50), stock.getAmount());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(8), position.getQuantity());
		Assert.assertTrue(createMoney("36.36").compareTo(position.getAmount()) == 0);
		Assert.assertEquals(createMoney("36.36"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("13.64"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}
}
