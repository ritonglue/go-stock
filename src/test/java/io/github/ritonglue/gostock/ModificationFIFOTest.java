package io.github.ritonglue.gostock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;

import org.junit.Assert;
import org.junit.Test;

import io.github.ritonglue.gostock.StockManager.TradeWrapper;
import io.github.ritonglue.gostock.exception.StockAmountReductionException;

public class ModificationFIFOTest {
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

	private static StockManager newStockManager(ModificationMode modificationMode) {
		return new StockManager(Mode.FIFO, null, modificationMode);
	}

	@Test
	public void testBuySellModification() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("33.33"), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.sell(createQuantity(2), b));
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.modification(createMoney(20), c));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createQuantity(1), stock.getQuantity());
		Assert.assertEquals(createMoney("31.11"), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		List<Modification> modifications = manager.getModifications();
		Assert.assertEquals(1, openedPositions.size());
		Assert.assertEquals(1, closedPositions.size());
		Assert.assertEquals(1, modifications.size());
		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(1), position.getQuantity());
		Assert.assertEquals(a, position.getBuy());

		position = closedPositions.get(0);
		Assert.assertEquals(createQuantity(2), position.getQuantity());
		Assert.assertEquals(createMoney("22.22"), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
		Assert.assertEquals(b, position.getSell());

		Modification modification = modifications.get(0);
		Assert.assertEquals(createQuantity(1), modification.getQuantity());
		Assert.assertEquals(createMoney(20), modification.getModification().getAmount());
		Assert.assertEquals(c, modification.getModification().getSource());
		Assert.assertEquals(createMoney("11.11"), modification.getAmountBefore());
		Assert.assertEquals(createMoney("31.11"), modification.getAmountAfter());
	}

	@Test
	public void testBuySellReduction() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("33.33"), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.sell(createQuantity(2), b));
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.modification(createMoney(-10), c));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createQuantity(1), stock.getQuantity());
		Assert.assertEquals(createMoney("1.11"), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		List<Modification> modifications = manager.getModifications();
		Assert.assertEquals(1, openedPositions.size());
		Assert.assertEquals(1, closedPositions.size());
		Assert.assertEquals(1, modifications.size());
		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(1), position.getQuantity());
		Assert.assertEquals(a, position.getBuy());

		position = closedPositions.get(0);
		Assert.assertEquals(createQuantity(2), position.getQuantity());
		Assert.assertEquals(createMoney("22.22"), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
		Assert.assertEquals(b, position.getSell());

		Modification modification = modifications.get(0);
		Assert.assertEquals(createQuantity(1), modification.getQuantity());
		Assert.assertEquals(createMoney(-10), modification.getModification().getAmount());
		Assert.assertEquals(c, modification.getModification().getSource());
		Assert.assertEquals(createMoney("11.11"), modification.getAmountBefore());
		Assert.assertEquals(createMoney("1.11"), modification.getAmountAfter());
	}

	@Test
	public void testReductionByQuantityOnePositionToZero() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("33.33"), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("45.01"), b));
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.modification(createMoney(-78), c));
		StockManager manager = newStockManager(ModificationMode.QUANTITY);
		manager.process(list);
		Map<Object, Position> openedPositions = manager.getOpenedPositions().stream().collect(Collectors.toMap(Position::getBuy, Function.identity()));
		Assert.assertEquals(2, openedPositions.size());
		Position position = openedPositions.get(a);
		Assert.assertTrue(position.getAmount().isZero());
		position = openedPositions.get(b);
		Assert.assertEquals(createMoney("0.34"), position.getAmount());
	}

	@Test
	public void testReductionByQuantityToZero() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("33.33"), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("45.01"), b));
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.modification(createMoney("-78.34"), c));
		StockManager manager = newStockManager(ModificationMode.QUANTITY);
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney(0), stock.getAmount());
		Assert.assertTrue(createQuantity(7).compareTo(stock.getQuantity()) == 0);
		Map<Object, Position> openedPositions = manager.getOpenedPositions().stream().collect(Collectors.toMap(Position::getBuy, Function.identity()));
		Assert.assertEquals(2, openedPositions.size());
		Position position = openedPositions.get(a);
		Assert.assertTrue(position.getAmount().isZero());
		position = openedPositions.get(b);
		Assert.assertTrue(position.getAmount().isZero());
	}

	@Test
	public void testReductionByQuantity1() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("45.01"), b));
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("33.33"), a));
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.modification(createMoney("-78.00"), c));
		StockManager manager = newStockManager(ModificationMode.QUANTITY);
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney("0.34"), stock.getAmount());
		Assert.assertTrue(createQuantity(7).compareTo(stock.getQuantity()) == 0);
		Map<Object, Position> openedPositions = manager.getOpenedPositions().stream().collect(Collectors.toMap(Position::getBuy, Function.identity()));
		Assert.assertEquals(2, openedPositions.size());
		Position position = openedPositions.get(a);
		Assert.assertTrue(position.getAmount().isZero());
		position = openedPositions.get(b);
		Assert.assertEquals(createMoney("0.34"), position.getAmount());
	}

	@Test
	public void testReductionByQuantity2() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("33.33"), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(4), createMoney("45.01"), b));
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.modification(createMoney("-78.00"), c));//there is a left over
		StockManager manager = newStockManager(ModificationMode.QUANTITY);
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney("0.34"), stock.getAmount());
		Assert.assertTrue(createQuantity(7).compareTo(stock.getQuantity()) == 0);
		Map<Object, Position> openedPositions = manager.getOpenedPositions().stream().collect(Collectors.toMap(Position::getBuy, Function.identity()));
		Assert.assertEquals(2, openedPositions.size());
		Position position = openedPositions.get(a);
		Assert.assertTrue(position.getAmount().isZero());
		position = openedPositions.get(b);
		Assert.assertEquals(createMoney("0.34"), position.getAmount());
	}

	@Test
	public void testReductionByQuantity3() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("1600"), b));
		SourceTest c = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("2700"), c));
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(1), createMoney("100"), a));
		SourceTest d = new SourceTest(id++);
		//reduction by 200 per unit
		list.add(TradeWrapper.modification(createMoney("-1100"), ModificationMode.QUANTITY, d));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createMoney("3300"), stock.getAmount());
		Assert.assertTrue(createQuantity(6).compareTo(stock.getQuantity()) == 0);
		Map<Object, Position> openedPositions = manager.getOpenedPositions().stream().collect(Collectors.toMap(Position::getBuy, Function.identity()));
		Assert.assertEquals(3, openedPositions.size());
		Position position = openedPositions.get(a);
		Assert.assertTrue(position.getAmount().isZero());
		position = openedPositions.get(b);
		Assert.assertEquals(createMoney("1200"), position.getAmount());
		position = openedPositions.get(c);
		Assert.assertEquals(createMoney("2100"), position.getAmount());
	}

	@Test(expected = StockAmountReductionException.class)
	public void testReductionException1() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("16"), b));
		SourceTest d = new SourceTest(id++);
		list.add(TradeWrapper.modification(createMoney("-20"), ModificationMode.QUANTITY, d));
		StockManager manager = newStockManager();
		manager.process(list);
	}

	@Test(expected = StockAmountReductionException.class)
	public void testReductionException2ByQuantity() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("10"), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("16"), b));
		SourceTest d = new SourceTest(id++);
		list.add(TradeWrapper.modification(createMoney("-28"), ModificationMode.QUANTITY, d));
		StockManager manager = newStockManager();
		manager.process(list);
	}

	@Test(expected = StockAmountReductionException.class)
	public void testReductionException2ByMoney() {
		int id = 1;
		List<TradeWrapper> list = new ArrayList<>();
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney("10"), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(2), createMoney("16"), b));
		SourceTest d = new SourceTest(id++);
		list.add(TradeWrapper.modification(createMoney("-28"), ModificationMode.MONEY, d));
		StockManager manager = newStockManager();
		manager.process(list);
	}
}
