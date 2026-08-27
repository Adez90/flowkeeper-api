package se.flowkeeper.api.statistics;

import java.time.LocalDate;
import java.util.List;

public record PersonalTrendResponse(LocalDate rangeStart, LocalDate rangeEndExclusive, List<TrendPoint> points) {
}
