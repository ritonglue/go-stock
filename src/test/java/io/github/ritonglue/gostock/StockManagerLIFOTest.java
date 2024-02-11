package io.github.ritonglue.gostock;

import static io.github.ritonglue.gostock.StockManagerFIFOTest.createQuantity;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;

import org.junit.Assert;
import org.junit.Test;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;
import io.github.ritonglue.gostock.exception.StockAmountReductionException;

public class StockManagerLIFOTest {
	private final CurrencyUnit cu = Monetary.getCurrency("EUR");

	@Test
	public void testStack() {
		Deque<Integer> q = new ArrayDeque<>();
		q.addFirst(1);
		q.addFirst(2);
		q.addFirst(3);
		q.addFirst(4);
		Assert.assertEquals(4, (int) q.peek());
		q.remove();
		Assert.assertEquals(3, (int) q.peek());
	}

	private MonetaryAmount createMoney(String value) {
		MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
		return factory.setCurrency(cu).setNumber(createQuantity(value)).create();
	}

	private MonetaryAmount createMoney(long value) {
		MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
		return factory.setCurrency(cu).setNumber(value).create();
	}

	private static StockManager newStockManager() {
		return new StockManager(Mode.LIFO);
	}

	@Test
	public void testOneBuy() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
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
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
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

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testOnePartialSell() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
		BigDecimal quantitySell = createQuantity(2);
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

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	@Test
	public void testFullMultiPartialSell() {
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
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.sell(createQuantity(4), c));//closes 3 + 1
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
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.sell(createQuantity(4), c));//closes 3 + 1. 3 stays open
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(200), d));
		list.add(TradeWrapper.sell(createQuantity(3), e)); //closes 3. 4 stays open
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(2, opened.size());
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
		Assert.assertEquals(position.getAmount(), createMoney("85.71"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), createQuantity(4));
		Assert.assertEquals(position.getAmount(), createMoney("114.29"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);

		position = opened.get(1);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("3.88"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);

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
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.sell(createQuantity(4), c));//closes 3 + 1; 3 stays opened
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(200), d));
		list.add(TradeWrapper.sell(createQuantity(9), e));//closes 7 + 2. 1 stays opened
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("33.33"), f));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(2, opened.size());
		Assert.assertEquals(4, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(100), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(1), position.getQuantity());
		Assert.assertEquals(createMoney("1.29"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(2);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney(200), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = closed.get(3);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(2), position.getQuantity());
		Assert.assertEquals(createMoney("2.59"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), createQuantity(2));
		Assert.assertEquals(position.getAmount(), createMoney("33.33"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(f, buy);

		position = opened.get(1);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), createQuantity(1));
		Assert.assertEquals(position.getAmount(), createMoney("1.29"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);

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
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.sell(createQuantity(4), c));//closes 3 + 1; 3 stays opened
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(200), d));
		list.add(TradeWrapper.sell(createQuantity(9), e));//closes 7 + 2. 1 stays opened
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("33.33"), f));
		list.add(TradeWrapper.sell(createQuantity(3), g));//closes 2 + 1
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertTrue(opened.isEmpty());
		Assert.assertEquals(6, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(100), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(1), position.getQuantity());
		Assert.assertEquals(createMoney("1.29"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(2);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney(200), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = closed.get(3);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(2), position.getQuantity());
		Assert.assertEquals(createMoney("2.59"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = closed.get(4);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(2), position.getQuantity());
		Assert.assertEquals(createMoney("33.33"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(f, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(g, sell);

		position = closed.get(5);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(1), position.getQuantity());
		Assert.assertEquals(createMoney("1.29"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
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
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.sell(createQuantity(4), c));//closes 3 + 1. 3 stays open
		list.add(TradeWrapper.modification(createMoney("-1.50")));
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(200), d));
		list.add(TradeWrapper.sell(createQuantity(3), e)); //closes 3. 4 stays open
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(2, opened.size());
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
		Assert.assertEquals(position.getAmount(), createMoney("85.71"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(position.getQuantity(), createQuantity(4));
		Assert.assertEquals(position.getAmount(), createMoney("114.29"));
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(d, buy);

		position = opened.get(1);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("2.38"), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
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
		Assert.assertEquals(createQuantity(200), position.getQuantity());
		Assert.assertEquals(createMoney(200*150), position.getAmount());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(50), position.getQuantity());
		Assert.assertEquals(createMoney(100*50), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(100), position.getQuantity());
		Assert.assertEquals(createMoney(100*100), position.getAmount());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
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
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("129.23"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(8), position.getQuantity());
		Assert.assertEquals(createMoney("80.77"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
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
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney("69.69"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		SourceTest buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(c, buy);

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("123.88"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(b, buy);

		position = openedPositions.get(2);
		Assert.assertEquals(createQuantity(8), position.getQuantity());
		Assert.assertEquals(createMoney("77.43"), position.getAmount());
		Assert.assertTrue(position.isOpened());
		buy = position.getBuy(SourceTest.class);
		Assert.assertEquals(a, buy);
	}

	@Test(expected = StockAmountReductionException.class)
	public void testThrowSingleReduction() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(8), createMoney(0), a));
		list.add(TradeWrapper.modification(createMoney(-50)));
		StockManager manager = newStockManager();
		manager.process(list);
	}

	@Test(expected = StockAmountReductionException.class)
	public void testThrowMultiReduction() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(30), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(20), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), c));
		list.add(TradeWrapper.modification(createMoney(-60)));
		StockManager manager = newStockManager();
		manager.process(list);
	}

	@Test
	public void testFullMultiReduction() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(30), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(20), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), c));
		list.add(TradeWrapper.modification(createMoney(-50)));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney(0), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(3, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(0), position.getAmount());

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney(0), position.getAmount());

		position = openedPositions.get(2);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(0), position.getAmount());
	}

	@Test
	public void testMultiReduction() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(30), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(20), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), c));
		list.add(TradeWrapper.modification(createMoney(-40)));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney(10), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(3, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(0), position.getAmount());

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney(4), position.getAmount());

		position = openedPositions.get(2);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(6), position.getAmount());
	}

	@Test
	public void testOneReduction() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(30), a));
		list.add(TradeWrapper.modification(createMoney(-20)));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney(10), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(1, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(10), position.getAmount());
	}

	@Test
	public void testOneRaise() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(30), a));
		list.add(TradeWrapper.modification(createMoney(20)));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney(50), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(1, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(50), position.getAmount());
	}

	@Test
	public void testMultiRaise() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(30), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(20), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), c));
		list.add(TradeWrapper.modification(createMoney(30)));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney(80), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(3, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(9), position.getAmount());
		Assert.assertEquals(c, position.getBuy());

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney(32), position.getAmount());
		Assert.assertEquals(b, position.getBuy());

		position = openedPositions.get(2);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(39), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
	}

	@Test
	public void testMultiReductionWithSell() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest o = new SourceTest(id++);
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), c));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(20), b));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(40), a));
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(17), o));
		list.add(TradeWrapper.sell(createQuantity(8), d));
		list.add(TradeWrapper.modification(createMoney(-40)));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney(10), stock.getAmount());

		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertEquals(2, closedPositions.size());
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(3, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(6), position.getAmount());

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney(4), position.getAmount());

		position = openedPositions.get(2);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(0), position.getAmount());
	}

	@Test
	public void testMultiRaiseWithSell() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest o = new SourceTest(id++);
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), c));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(20), b));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(40), a));
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(17), o));
		list.add(TradeWrapper.sell(createQuantity(8), d));
		list.add(TradeWrapper.modification(createMoney(30)));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney(80), stock.getAmount());

		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertEquals(2, closedPositions.size());
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(3, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(39), position.getAmount());
		Assert.assertEquals(a, position.getBuy());

		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney(32), position.getAmount());
		Assert.assertEquals(b, position.getBuy());

		position = openedPositions.get(2);
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney(9), position.getAmount());
		Assert.assertEquals(c, position.getBuy());
	}
}
