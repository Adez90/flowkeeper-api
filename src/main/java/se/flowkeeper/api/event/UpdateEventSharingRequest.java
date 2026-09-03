package se.flowkeeper.api.event;

public record UpdateEventSharingRequest(boolean shareIngoingNoteAnonymously, boolean shareOutgoingNoteAnonymously) {
}
