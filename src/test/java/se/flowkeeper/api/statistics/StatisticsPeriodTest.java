package se.flowkeeper.api.statistics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsPeriodTest {

	@Test
	void dayIsJustTheDateItself() {
		LocalDate date = LocalDate.of(2026, 3, 12);
		assertThat(StatisticsPeriod.DAY.startOf(date)).isEqualTo(LocalDate.of(2026, 3, 12));
		assertThat(StatisticsPeriod.DAY.endOf(date)).isEqualTo(LocalDate.of(2026, 3, 13));
	}

	@Test
	void weekStartsOnMondayEvenWhenReferenceDateIsSunday() {
		LocalDate sunday = LocalDate.of(2026, 3, 15);
		assertThat(StatisticsPeriod.WEEK.startOf(sunday)).isEqualTo(LocalDate.of(2026, 3, 9));
		assertThat(StatisticsPeriod.WEEK.endOf(sunday)).isEqualTo(LocalDate.of(2026, 3, 16));
	}

	@Test
	void weekStartsOnItselfWhenReferenceDateIsAlreadyMonday() {
		LocalDate monday = LocalDate.of(2026, 3, 9);
		assertThat(StatisticsPeriod.WEEK.startOf(monday)).isEqualTo(monday);
	}

	@Test
	void monthSpansTheWholeCalendarMonthAcrossAFebruaryBoundary() {
		LocalDate midFeb = LocalDate.of(2026, 2, 18);
		assertThat(StatisticsPeriod.MONTH.startOf(midFeb)).isEqualTo(LocalDate.of(2026, 2, 1));
		assertThat(StatisticsPeriod.MONTH.endOf(midFeb)).isEqualTo(LocalDate.of(2026, 3, 1));
	}

}
