package se.flowkeeper.api.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

	Optional<PromoCode> findByCode(String code);

	/**
	 * Row-locked (SELECT ... FOR UPDATE) — the redemption path only.
	 * Without this, two concurrent redemptions of a near-exhausted
	 * multi-use code (e.g. a company code shared across several
	 * employees, redeeming at close to the same moment) can both read
	 * redemptionCount < maxRedemptions before either commits, then both
	 * write back a count that's missing one increment — a classic lost
	 * update that lets the code exceed maxRedemptions. Locking the row
	 * for the duration of the transaction serializes concurrent
	 * redemptions of the SAME code; different codes are unaffected since
	 * this only locks the one matching row, not the table.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PromoCode> findWithLockByCode(String code);

	List<PromoCode> findAllByOrderByCreatedAtDesc();

}
