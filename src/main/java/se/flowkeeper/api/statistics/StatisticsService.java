package se.flowkeeper.api.statistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class StatisticsService {

	private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);

	private final EventStatisticsRepository eventStatisticsRepository;
	private final AccountMemberRepository accountMemberRepository;
	private final CurrentUserResolver currentUserResolver;

	public StatisticsService(EventStatisticsRepository eventStatisticsRepository,
			AccountMemberRepository accountMemberRepository,
			CurrentUserResolver currentUserResolver) {
		this.eventStatisticsRepository = eventStatisticsRepository;
		this.accountMemberRepository = accountMemberRepository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional(readOnly = true)
	public PersonalStatisticsResponse personalStatistics(Jwt jwt, UUID accountId, StatisticsPeriod period, LocalDate referenceDate) {
		User user = currentUserResolver.require(jwt);
		accountMemberRepository.findByAccount_IdAndUser(accountId, user)
			.orElseThrow(() -> new AccessDeniedException(
				"User %s is not a member of account %s".formatted(user.getId(), accountId)));

		// UTC for now — there's no per-user timezone preference stored yet.
		// A "day" here is a UTC day, not the user's local day; worth
		// revisiting once that preference exists.
		LocalDate date = referenceDate != null ? referenceDate : LocalDate.now(ZoneOffset.UTC);
		LocalDate rangeStart = period.startOf(date);
		LocalDate rangeEnd = period.endOf(date);
		Instant start = rangeStart.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant end = rangeEnd.atStartOfDay(ZoneOffset.UTC).toInstant();

		OverallCounts overall = eventStatisticsRepository.aggregateOverall(accountId, start, end);
		List<TypeBreakdown> byType = eventStatisticsRepository.aggregateByType(accountId, start, end).stream()
			.map(TypeCounts::toBreakdown)
			.toList();

		long total = overall.total() != null ? overall.total() : 0L;
		long completed = overall.completed() != null ? overall.completed() : 0L;

		log.debug("Computed {} statistics for account {} over [{}, {}): {} event(s), {} completed",
			period, accountId, rangeStart, rangeEnd, total, completed);

		return new PersonalStatisticsResponse(
			period, rangeStart, rangeEnd,
			total, completed, total - completed,
			overall.averageIngoingEnergy(), overall.averageEnergyDelta(),
			byType);
	}

}
