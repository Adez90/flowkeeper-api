package se.flowkeeper.api.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import se.flowkeeper.api.AbstractIntegrationTest;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.integrations.ExternalProvider;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventRepositoryIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	EventRepository eventRepository;
	@Autowired
	EventTypeRepository eventTypeRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	AccountRepository accountRepository;

	/**
	 * Regression test for a real production bug (Sentry FLOWKEEPER-API-5):
	 * without an explicit @Query projection, Spring Data ran
	 * "SELECT e FROM Event e WHERE ..." regardless of the method name's
	 * leading "findExternalId", returning full Event entities that then
	 * failed to convert to List<String> — but only once there was at least
	 * one match, since an empty list needs no per-element conversion. This
	 * only reproduces against a real Postgres query executor, not a mock.
	 */
	@Test
	void findExternalIdByUserAndProviderProjectsJustTheIdInsteadOfTheWholeEntity() {
		User user = userRepository.save(new User("kc-" + UUID.randomUUID(), "Repo Tester", "repo-tester-" + UUID.randomUUID() + "@example.com"));
		Account account = accountRepository.save(new Account(AccountType.PERSONAL, "Repo Tester"));
		EventType eventType = eventTypeRepository.findByAccountIdIsNullOrAccountId(account.getId()).get(0);

		eventRepository.save(new Event(user, account, eventType, Instant.now(), Instant.now(),
			ExternalProvider.STRAVA, "strava-activity-123"));

		List<String> externalIds = eventRepository.findExternalIdByUser_IdAndExternalProvider(user.getId(), ExternalProvider.STRAVA);

		assertThat(externalIds).containsExactly("strava-activity-123");
	}

	@Test
	void findExternalIdByUserAndProviderReturnsEmptyRatherThanOtherProvidersItems() {
		User user = userRepository.save(new User("kc-" + UUID.randomUUID(), "Repo Tester 2", "repo-tester2-" + UUID.randomUUID() + "@example.com"));
		Account account = accountRepository.save(new Account(AccountType.PERSONAL, "Repo Tester 2"));
		EventType eventType = eventTypeRepository.findByAccountIdIsNullOrAccountId(account.getId()).get(0);

		eventRepository.save(new Event(user, account, eventType, Instant.now(), Instant.now(),
			ExternalProvider.GOOGLE_CALENDAR, "google-event-456"));

		List<String> externalIds = eventRepository.findExternalIdByUser_IdAndExternalProvider(user.getId(), ExternalProvider.STRAVA);

		assertThat(externalIds).isEmpty();
	}

}
