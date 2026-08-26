package se.flowkeeper.api.statistics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * A reference date is enough to ask for a period — the actual range is
 * derived from it, so the client never computes date arithmetic itself.
 */
public enum StatisticsPeriod {

	DAY {
		@Override
		LocalDate startOf(LocalDate date) {
			return date;
		}

		@Override
		LocalDate endOf(LocalDate date) {
			return date.plusDays(1);
		}
	},
	WEEK {
		@Override
		LocalDate startOf(LocalDate date) {
			return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		}

		@Override
		LocalDate endOf(LocalDate date) {
			return startOf(date).plusWeeks(1);
		}
	},
	MONTH {
		@Override
		LocalDate startOf(LocalDate date) {
			return date.withDayOfMonth(1);
		}

		@Override
		LocalDate endOf(LocalDate date) {
			return startOf(date).plusMonths(1);
		}
	};

	/** Inclusive start of the range containing this date. */
	abstract LocalDate startOf(LocalDate date);

	/** Exclusive end of the range containing this date. */
	abstract LocalDate endOf(LocalDate date);

}
