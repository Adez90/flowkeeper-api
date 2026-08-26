package se.flowkeeper.api.statistics;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class StatisticsController {

	private final StatisticsService statisticsService;

	public StatisticsController(StatisticsService statisticsService) {
		this.statisticsService = statisticsService;
	}

	/**
	 * date just needs to fall anywhere inside the period you want — the
	 * actual range is derived from it. Defaults to today (UTC) if omitted.
	 */
	@GetMapping("/api/v1/statistics/personal")
	public PersonalStatisticsResponse personal(
			Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam StatisticsPeriod period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return statisticsService.personalStatistics(jwt, accountId, period, date);
	}

}
