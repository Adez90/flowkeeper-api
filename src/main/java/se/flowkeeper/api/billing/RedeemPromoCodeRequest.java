package se.flowkeeper.api.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RedeemPromoCodeRequest(@NotNull UUID accountId, @NotBlank String code) {
}
