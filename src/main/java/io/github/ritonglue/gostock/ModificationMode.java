package io.github.ritonglue.gostock;

/**
 * Apply modification according to the quantity or the money amount
 * Only used if there is more than one position
 */
public enum ModificationMode {
	  QUANTITY //by quantity
	, MONEY //by monetary amount
	, MIXED;//reduction : by moneraty amount. augmentation : by quantity
}
