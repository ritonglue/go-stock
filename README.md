# GO STOCK
A library to manage a stock. The way quantities are sold can follow these rules :
- FIFO accounting (First In First Out)
- LIFO accounting (Last In Last Out)
- PRMP (Prix de Revient Moyen Pondéré) (Average Cost ?)

This library uses the JavaMoney API (JSR 354). You have to provide and implementation such as Moneta.

It's also possible to modify the value of the stock.

The trades must be ordered in time ascending order.

Rouding is taken into account. For example, you can buy 3 items at 100.00 euros and sell them one by one. The first item is sold at 33.33 euros. The second at 33.34 euros and the last one at 33.33 euros.

## Table of Contents
1. [Maven Dependency](#maven-dependency)
2. [TradeWrapper](#tradewrapper)
3. [Example](#example)
  1. [FIFO](#fifo)
  2. [LIFO](#lifo)
  3. [PRMP](#prmp)
4. [Modification](#modification)
5. [Reimbursement](#reimbursement)

## Maven Dependency
````
<dependency>
  <groupId>io.github.ritonglue</groupId>
  <artifactId>go-stock</artifactId>
  <version>2.1.1</version>
</dependency>
````

## TradeWrapper
This class stores an amount and a quantity. It's possible to provide the source of the information. The source is useful in a closed position. The getBuy method gives you the buy source and the getSell method gives you the sell source.


## Example
- buy 100 units at 50 each
- buy 125 units at 55 each
- buy 75 units at 59 each
- sell 210 units

Using this class as source :

``` java
	public class Transaction {
		private final int id;
		private final BigDecimal quantity;
		private final MonetaryAmount amount;

		Transaction(int id, long quantity, long unitAmount) {
			this.id = id;
			this.quantity = new BigDecimal(quantity);
			this.amount = Monetary.getDefaultAmountFactory().
			setCurrency("EUR").setNumber(new BigDecimal(unitAmount)).create()
			.multiply(quantity);
		}

		Transaction(int id, long quantity) {
			this.id = id;
			this.quantity = new BigDecimal(quantity);
			this.amount = null;
		}

		public BigDecimal getQuantity() {
			return quantity;
		}

		public MonetaryAmount getAmount() {
			return amount;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj == this) return true;
			if(!(obj instanceof Transaction)) return false;
			Transaction a = (Transaction) obj;
			return a.id == this.id;
		}
		
		@Override
		public int hashCode() {
			return id;
		}
	}
```

### FIFO
Use the StockManager in FIFO mode to solve this problem :

``` java
		StockManager manager = new StockManager(Mode.FIFO);
```

``` Java
		int id = 1;
		Transaction a = new Transaction(id++, 100, 50);
		Transaction b = new Transaction(id++, 125, 55);
		Transaction c = new Transaction(id++, 75, 59);
		Transaction d = new Transaction(id++, -210);
		manager.add(Trade.buy(a.getQuantity(), a.getAmount(), a));
		manager.add(Trade.buy(b.getQuantity(), b.getAmount(), b));
		manager.add(Trade.buy(c.getQuantity(), c.getAmount(), c));
		manager.add(Trade.sell(d.getQuantity(), d));
```
Check the stock :

``` Java
		Trade stock = manager.getStock();
		Assert.assertEquals(new BigDecimal(90), stock.getQuantity());
		Assert.assertEquals(createMoney(5250), stock.getAmount());
```

Check the closed positions :

``` Java
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertEquals(2, closedPositions.size());
		Position position = closedPositions.get(0);
		Assert.assertEquals(new BigDecimal(100), position.getQuantity());
		Assert.assertEquals(createMoney(5000), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
		Assert.assertEquals(d, position.getSell());
		position = closedPositions.get(1);
		Assert.assertEquals(createQuantity(110), position.getQuantity());
		Assert.assertEquals(createMoney(110*55), position.getAmount());
		Assert.assertEquals(b, position.getBuy());
		Assert.assertEquals(d, position.getSell());
```

Check the opened positions :

``` java
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(2, openedPositions.size());

		Assert.assertEquals(new BigDecimal(15), position.getQuantity());
		Assert.assertEquals(createMoney(15*55), position.getAmount());
		Assert.assertEquals(b, position.getBuy());
		position = openedPositions.get(1);
		Assert.assertEquals(createQuantity(75), position.getQuantity());
		Assert.assertEquals(createMoney(59*75), position.getAmount());
		Assert.assertEquals(c, position.getBuy());
```

### LIFO
Use the StockManager in LIFO mode to solve this problem :

``` java
		StockManager manager = new StockManager(Mode.LIFO);
```
Check the stock :

``` java
		Trade stock = manager.getStock();
		Assert.assertEquals(new BigDecimal(90), stock.getQuantity());
		Assert.assertEquals(createMoney(90*50), stock.getAmount());
```
Check the closed positions :

``` java
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertEquals(3, closedPositions.size());
		Position position = closedPositions.get(0);
		Assert.assertEquals(new BigDecimal(75), position.getQuantity());
		Assert.assertEquals(createMoney(75*59), position.getAmount());
		Assert.assertEquals(c, position.getBuy());
		Assert.assertEquals(d, position.getSell());
		position = closedPositions.get(1);
		Assert.assertEquals(createQuantity(125), position.getQuantity());
		Assert.assertEquals(createMoney(125*55), position.getAmount());
		Assert.assertEquals(b, position.getBuy());
		Assert.assertEquals(d, position.getSell());
		position = closedPositions.get(2);
		Assert.assertEquals(createQuantity(10), position.getQuantity());
		Assert.assertEquals(createMoney(10*50), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
		Assert.assertEquals(d, position.getSell());
```
Check the opened positions :

``` java
		List<Position> openedPositions = manager.getOpenedPositions();
		Assert.assertEquals(1, openedPositions.size());

		position = openedPositions.get(0);
		Assert.assertEquals(new BigDecimal(90), position.getQuantity());
		Assert.assertEquals(createMoney(90*50), position.getAmount());
		Assert.assertEquals(a, position.getBuy());
```

### PRMP
Use the StockManager in PRMP mode to solve this problem :

``` java
		StockManager manager = new StockManager(Mode.PRMP);
```

In PRMP mode, all buy values are mixed together and sold at the same price.

``` java
		List<Position> openedPositions = manager.getOpenedPositions();
		List<Position> closedPositions = manager.getClosedPositions();
		Assert.assertEquals(1, closedPositions.size());
		Assert.assertEquals(1, openedPositions.size());
		TradeWrapper stock = manager.getStock();
		Assert.assertEquals(new BigDecimal(90), stock.getQuantity());
		Assert.assertEquals(createMoney(4890), stock.getAmount());
```

``` java
		Position position = closedPositions.get(0);
		Assert.assertEquals(new BigDecimal(210), position.getQuantity());
		Assert.assertEquals(createMoney(11410), position.getAmount());
```

``` java
		position = openedPositions.get(0);
		Assert.assertEquals(new BigDecimal(90), position.getQuantity());
		Assert.assertEquals(createMoney(4890), position.getAmount());
```

## Modification
Just add a positive or negative amount to modify the amount of the stock. The quantity is unchanged. In case of multiple opened position, the modification amount is spread prorata their amounts.

``` java
		stock = manager.getStock();
		BigDecimal oldQuantity = stock.getQuantity();
		MonetaryAmount oldAmount = stock.getAmount();
		MonetaryAmount delta = oldAmount.multiply(2);
		manager.add(TradeWrapper.modification(delta));
		stock = manager.getStock();
		Assert.assertEquals(oldQuantity, stock.getQuantity());
		Assert.assertEquals(oldAmount.add(delta), stock.getAmount());
```

## Reimbursement
A reimbursement will close all opened positions. After that the stock is empty (quantity zero).

``` java
	Transaction rbt;
	manager.add(Trade.reimbursement(rbt));
	openedPositions = manager.getOpenedPositions();
	Assert.assertTrue(openedPositions.isEmpty());
	stock = manager.getStock();
	Assert.assertEquals(BigDecimal.ZERO, stock.getQuantity());
	Assert.assertNull(stock.getAmount());
```
