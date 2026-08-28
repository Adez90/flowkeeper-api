package se.flowkeeper.api.billing;

/**
 * A subscription's length, independent of scope — Business's "quarterly"
 * and "yearly" are the same durations as Personal's "3 months" and "12
 * months", so there's one shared period set rather than parallel
 * per-scope naming.
 */
public enum BillingPeriod {
	ONE_MONTH,
	THREE_MONTHS,
	SIX_MONTHS,
	TWELVE_MONTHS,
	TWO_YEARS,
	THREE_YEARS,
	FOUR_YEARS,
	FIVE_YEARS
}
