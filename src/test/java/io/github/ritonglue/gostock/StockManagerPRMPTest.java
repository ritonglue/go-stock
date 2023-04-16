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

import io.github.ritonglue.gostock.StockManager.Trade;

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
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
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
	}

	@Test
	public void testOneFullSell() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
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
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);
	}

	@Test
	public void testOnePartialSell() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		BigDecimal quantity = createQuantity(3);
		MonetaryAmount amount = createMoney(100);
		BigDecimal quantitySell = createQuantity(2);
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
		Assert.assertEquals(BigDecimal.ONE, position.getQuantity());
		Assert.assertEquals(createMoney("33.33"), position.getAmount());

		position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantitySell, position.getQuantity());
		Assert.assertEquals(createMoney("66.67"), position.getAmount());
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);
	}

	@Test
	public void testFullMultiPartialSell() {
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
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), BigDecimal.ONE);
		Assert.assertEquals(position.getAmount(), createMoney("33.33"));
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
		list.add(Trade.buy(createQuantity(3), createMoney(100), a));
		list.add(Trade.buy(createQuantity(4), createMoney("5.17"), b));
		list.add(Trade.sell(createQuantity(4), c));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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
	}

	/**
	 * from https://www.iotafinance.com/Definition-prix-de-revient-moyen-pondere-PRMP.html
	 */
	@Test
	public void testiotaFinance() {
		int id = 1;
		List<Trade> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		SourceTest b = new SourceTest(id++);
		SourceTest c = new SourceTest(id++);
		SourceTest d = new SourceTest(id++);
		SourceTest e = new SourceTest(id++);
		SourceTest f = new SourceTest(id++);
		SourceTest g = new SourceTest(id++);
		list.add(Trade.buy(createQuantity(100), createMoney(4500), a));
		list.add(Trade.buy(createQuantity(80), createMoney(3608), b));
		list.add(Trade.buy(createQuantity(70), createMoney(3136), c));
		list.add(Trade.sell(createQuantity(70), d));
		list.add(Trade.buy(createQuantity(25), createMoney(1105), e));
		list.add(Trade.sell(createQuantity(35), f));
		list.add(Trade.buy(createQuantity(40), createMoney(1802), g));
		StockManager manager = newStockManager();
		PositionLines result = manager.process(list);
		List<Position> opened = result.getOpenedPositions();
		List<Position> closed = result.getClosedPositions();
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

		position = closed.get(0);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(quantitySell, position.getQuantity());
		Assert.assertEquals(createMoney("66.67"), position.getAmount());
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
		SourceTest sell = position.getSell(SourceTest.class);
		Assert.assertEquals(b, sell);

		position = closed.get(1);
		Assert.assertTrue(position.isClosed());
		Assert.assertEquals(position.getQuantity(), BigDecimal.ONE);
		Assert.assertEquals(position.getAmount(), createMoney("23.33"));
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
	}
}
