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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

		ZoneId zone = resolveZone(user);
		LocalDate date = referenceDate != null ? referenceDate : LocalDate.now(zone);
		LocalDate rangeStart = period.startOf(date);
		LocalDate rangeEnd = period.endOf(date);
		Instant start = rangeStart.atStartOfDay(zone).toInstant();
		Instant end = rangeEnd.atStartOfDay(zone).toInstant();

		OverallCounts overall = eventStatisticsRepository.aggregateOverall(accountId, start, end);
		List<TypeBreakdown> byType = eventStatisticsRepository.aggregateByType(accountId, start, end).stream()
			.map(TypeCounts::toBreakdown)
			.toList();

		long total = overall.total() != null ? overall.total() : 0L;
		long completed = overall.completed() != null ? overall.completed() : 0L;

		log.debug("Computed {} statistics for account {} over [{}, {}) in {}: {} event(s), {} completed",
			period, accountId, rangeStart, rangeEnd, zone, total, completed);

		return new PersonalStatisticsResponse(
			period, rangeStart, rangeEnd,
			total, completed, total - completed,
			overall.averageIngoingEnergy(), overall.averageEnergyDelta(),
			byType);
	}

	private ZoneId resolveZone(User user) {
		try {
			return ZoneId.of(user.getTimezone());
		} catch (DateTimeException e) {
			// Shouldn't happen — timezone is validated on write — but a
			// "day" still has to mean something if it somehow does.
			log.warn("User {} has an invalid stored timezone '{}', falling back to UTC", user.getId(), user.getTimezone());
			return ZoneOffset.UTC;
		}
	}

}
