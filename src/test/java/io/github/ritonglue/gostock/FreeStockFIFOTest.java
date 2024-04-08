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

public class FreeStockFIFOTest {
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
		SourceTest a = new SourceTest(id++);
		list.add(TradeWrapper.buy(createQuantity(3), createMoney(0), a));
		SourceTest b = new SourceTest(id++);
		list.add(TradeWrapper.sell(createQuantity(2), b));
		StockManager manager = newStockManager();
		manager.process(list);
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(createQuantity(1), stock.getQuantity());
		Assert.assertEquals(createMoney(0), stock.getAmount());

		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertEquals(1, openedPositions.size());
		Assert.assertEquals(1, closedPositions.size());
		Position position = openedPositions.get(0);
		Assert.assertEquals(createQuantity(1), position.getQuantity());
		Assert.assertEquals(a, position.getBuy());

		position = closedPositions.get(0);
		Assert.assertEquals(createQuantity(2), position.getQuantity());
		Assert.assertEquals(createMoney(0), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
		Assert.assertEquals(b, position.getSell());
	}
}
