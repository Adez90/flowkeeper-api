package se.flowkeeper.api.billing;

import java.util.List;
import java.util.UUID;

public record PlanResponse(UUID id, String code, PlanScope scope, String name, List<PriceResponse> prices) {

	public static PlanResponse from(Plan plan, List<Price> prices) {
		return new PlanResponse(
			plan.getId(),
			plan.getCode(),
			plan.getScope(),
			plan.getName(),
			prices.stream().map(PriceResponse::from).toList());
	}

}
