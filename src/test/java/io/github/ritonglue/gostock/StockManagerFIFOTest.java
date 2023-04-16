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

import io.github.ritonglue.gostock.StockManager.Trade;

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

	static BigDecimal createQuantity(String value) {
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
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		SourceTest a = new SourceTest(id++);
		list.add(Trade.buy(quantity, amount, a));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		Assert.assertTrue(result.getClosedPositions().isEmpty());
		List<Position> positions = result.getOpenedPositions();
		Assert.assertEquals(1, positions.size());
		Position position = positions.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(quantity, position.getQuantity());
		Assert.assertEquals(amount, position.getAmount());
		SourceTest b = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, b);
	}

	@Test
	public void testOneFullSell() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(Trade.buy(quantity, amount, a));
		list.add(Trade.sell(quantity, b));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		Assert.assertTrue(result.getOpenedPositions().isEmpty());
		List<Position> positions = result.getClosedPositions();
		Assert.assertEquals(1, positions.size());
		Position position = positions.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantity, position.getQuantity());
		Assert.assertEquals(amount, position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);
	}

	@Test
	public void testOnePartialSell() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		BigDecimal quantitySell = createQuantity("2");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(Trade.buy(quantity, amount, a));
		list.add(Trade.sell(quantitySell, b));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}

	@Test
	public void testFullMultiPartialSell() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity("3");
		MonetaryAmount amount = createMoney("100.00");
		BigDecimal quantitySell = createQuantity("2");
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(Trade.buy(quantity, amount, a));
		list.add(Trade.sell(quantitySell, b));
		list.add(Trade.sell(BigDecimal.ONE, c));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}

	@Test
	public void testMultiBuy1() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(Trade.buy(createQuantity(3), createMoney("100.00"), a));
		list.add(Trade.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(Trade.sell(createQuantity(4), c));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}

	@Test
	public void testMultiBuy2() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		list.add(Trade.buy(createQuantity(3), createMoney(100), a));
		list.add(Trade.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(Trade.sell(createQuantity(4), c));
		list.add(Trade.buy(createQuantity(7), createMoney(200), d));
		list.add(Trade.sell(createQuantity(3), e));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}

	@Test
	public void testMultiBuy3() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		SourceTest f = new SourceTest(id++);
		list.add(Trade.buy(createQuantity(3), createMoney(100), a));
		list.add(Trade.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(Trade.sell(createQuantity(4), c));//closes 3 + 1
		list.add(Trade.buy(createQuantity(7), createMoney(200), d));
		list.add(Trade.sell(createQuantity(9), e));//closes 3 + 6. 1 stays opened
		list.add(Trade.buy(createQuantity(2), createMoney("33.33"), f));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}

	@Test
	public void testMultiBuy4() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		SourceTest f = new SourceTest(id++);
		SourceTest g = new SourceTest(id++);
		list.add(Trade.buy(createQuantity(3), createMoney(100), a));
		list.add(Trade.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(Trade.sell(createQuantity(4), c));//closes 3 + 1; 3 stays opened
		list.add(Trade.buy(createQuantity(7), createMoney(200), d));
		list.add(Trade.sell(createQuantity(9), e));//closes 3 + 6. 1 stays opened
		list.add(Trade.buy(createQuantity(2), createMoney("33.33"), f));
		list.add(Trade.sell(createQuantity(3), g));//closes 1 + 2
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}

	@Test
	public void modificationSimple() {
		StockManager manager = newStockManager();
		int id = 1;
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
		SourceTest a = new SourceTest(id++);
		list.add(Trade.buy(quantity, amount, a));
		list.add(Trade.modification(createMoney(-10)));//reduce by 10 every units

		PositionLines result = manager.process(list);
		Assert.assertTrue(result.getClosedPositions().isEmpty());
		List<Position> positions = result.getOpenedPositions();
		Assert.assertEquals(1, positions.size());
		Position position = positions.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(quantity, position.getQuantity());
		Assert.assertEquals(createMoney(70), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
	}

	@Test
	public void modification() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
		BigDecimal quantitySell = createQuantity(2);
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		list.add(Trade.buy(quantity, amount, a));
		list.add(Trade.sell(quantitySell, b));
		list.add(Trade.modification(createMoney(-10)));//reduce by 10 every units
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}

	@Test
	public void testFullMultiPartialSellModification() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
		BigDecimal quantitySell = createQuantity(2);
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(Trade.buy(quantity, amount, a));
		list.add(Trade.sell(quantitySell, b));
		list.add(Trade.modification(createMoney(-10)));//reduce by 10 every units
		list.add(Trade.sell(BigDecimal.ONE, c));

		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}

	@Test
	public void testModificationMultiBuy2() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		list.add(Trade.buy(createQuantity(3), createMoney(100), a));
		list.add(Trade.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(Trade.sell(createQuantity(4), c));
		list.add(Trade.modification(createMoney("-0.50")));
		list.add(Trade.buy(createQuantity(7), createMoney(200), d));
		list.add(Trade.sell(createQuantity(3), e));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}
}
