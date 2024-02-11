package io.github.ritonglue.gostock;

import static io.github.ritonglue.gostock.StockManagerFIFOTest.createQuantity;

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
import io.github.ritonglue.gostock.exception.StockAmountReductionException;

public class StockManagerPRMPTest {
	private final CurrencyUnit cu = Monetary.getCurrency("EUR");

	private MonetaryAmount createMoney(String value) {
		MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
		return factory.setCurrency(cu).setNumber(createQuantity(value)).create();
	}

	private MonetaryAmount createMoney(long value) {
		MonetaryAmountFactory<?> factory = Monetary.getDefaultAmountFactory();
		return factory.setCurrency(cu).setNumber(value).create();
	}

	private static StockManager newStockManager() {
		return new StockManager(Mode.PRMP);
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
		Assert.assertEquals(BigDecimal.ONE, position.getQuantity());
		Assert.assertEquals(createMoney("33.33"), position.getAmount());

		position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantitySell, position.getQuantity());
		Assert.assertEquals(createMoney("66.67"), position.getAmount());
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
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), BigDecimal.ONE);
		Assert.assertEquals(position.getAmount(), createMoney("33.33"));
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
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(100), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(TradeWrapper.sell(createQuantity(4), c));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(1, opened.size());
		Assert.assertEquals(1, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney("60.10"), position.getAmount());
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("45.07"), position.getAmount());

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
		Assert.assertEquals(2, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney("60.10"), position.getAmount());
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("73.52"), position.getAmount());
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney("171.55"), position.getAmount());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
	}

	/**
	 * from https://www.iotafinance.com/Definition-prix-de-revient-moyen-pondere-PRMP.html
	 */
	@Test
	public void testiotaFinance() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		SourceTest f = new SourceTest(id++);
		SourceTest g = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(100), createMoney(4500), a));
		list.add(TradeWrapper.buy(createQuantity(80), createMoney(3608), b));
		list.add(TradeWrapper.buy(createQuantity(70), createMoney(3136), c));
		list.add(TradeWrapper.sell(createQuantity(70), d));
		list.add(TradeWrapper.buy(createQuantity(25), createMoney(1105), e));
		list.add(TradeWrapper.sell(createQuantity(35), f));
		list.add(TradeWrapper.buy(createQuantity(40), createMoney(1802), g));
		StockManager manager = newStockManager();
		manager.process(list);
		List<Position> opened = manager.getOpenedPositions();
		List<Position> closed = manager.getClosedPositions();
		Assert.assertEquals(1, opened.size());
		Assert.assertEquals(2, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(70), position.getQuantity());
		Assert.assertEquals(createMoney("3148.32"), position.getAmount());
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(d, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(35), position.getQuantity());
		Assert.assertEquals(createMoney("1570.85"), position.getAmount());
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(f, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(210), position.getQuantity());
		Assert.assertEquals(createMoney("9431.83"), position.getAmount());

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

		position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantitySell, position.getQuantity());
		Assert.assertEquals(createMoney("66.67"), position.getAmount());
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
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), BigDecimal.ONE);
		Assert.assertEquals(position.getAmount(), createMoney("23.33"));
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
		Assert.assertEquals(2, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(4), position.getQuantity());
		Assert.assertEquals(createMoney("60.10"), position.getAmount());
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(3), position.getQuantity());
		Assert.assertEquals(createMoney("73.07"), position.getAmount());
		sell = position.getSell(SourceTest.class);
		Assert.assertEquals(e, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(7), position.getQuantity());
		Assert.assertEquals(createMoney("170.50"), position.getAmount());

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
		Assert.assertEquals(1, closed.size());

		Position position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(createQuantity(250), position.getQuantity());
		Assert.assertEquals(createMoney("32142.86"), position.getAmount());
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(c, sell);

		position = opened.get(0);
		Assert.assertTrue(position.isOpened());
		Assert.assertEquals(createQuantity(100), position.getQuantity());
		Assert.assertEquals(createMoney("12857.14"), position.getAmount());

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
		Assert.assertEquals(1, openedPositions.size());
		Assert.assertEquals(createMoney(210)
			, openedPositions.stream().map(o -> o.getAmount())
			.reduce(createMoney(0), MonetaryAmount::add));

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(11), position.getQuantity());
		Assert.assertEquals(createMoney(210), position.getAmount());
		Assert.assertTrue(position.isOpened());

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
		Assert.assertEquals(1, openedPositions.size());
		Assert.assertEquals(createQuantity(8+3+7), stock.getQuantity());
		Assert.assertEquals(createMoney(100+160+90-79), stock.getAmount());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(8+3+7), position.getQuantity());
		Assert.assertEquals(createMoney(100+160+90-79), position.getAmount());
		Assert.assertTrue(position.isOpened());

		List<TradeWrapper> orphanSells = manager.getOrphanSells();
		Assert.assertTrue(orphanSells.isEmpty());
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
		Assert.assertEquals(1, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(10), position.getQuantity());
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
		Assert.assertEquals(1, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(10), position.getQuantity());
		Assert.assertEquals(createMoney(10), position.getAmount());
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
		Assert.assertEquals(1, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(10), position.getQuantity());
		Assert.assertEquals(createMoney(80), position.getAmount());
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
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(17), o));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(40), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(20), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), c));
		list.add(TradeWrapper.sell(createQuantity(8), d));
		list.add(TradeWrapper.modification(createMoney(-40)));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createQuantity(10), stock.getQuantity());
		Assert.assertEquals(createMoney("2.78"), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(1, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(10), position.getQuantity());
		Assert.assertEquals(createMoney("2.78"), position.getAmount());
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
		list.add(TradeWrapper.buy(createQuantity(7), createMoney(17), o));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(40), a));
		list.add(TradeWrapper.buy(createQuantity(4), createMoney(20), b));
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), c));
		list.add(TradeWrapper.sell(createQuantity(8), d));
		list.add(TradeWrapper.modification(createMoney(30)));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createQuantity(10), stock.getQuantity());
		Assert.assertEquals(createMoney("72.78"), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(1, openedPositions.size());

		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(10), position.getQuantity());
		Assert.assertEquals(createMoney("72.78"), position.getAmount());
	}
}
