package se.flowkeeper.api.statistics;

import java.util.List;

public record OrganisationFeedbackResponse(int memberCount, boolean belowMinimumSize, List<AnonymousFeedbackItem> items) {
}
