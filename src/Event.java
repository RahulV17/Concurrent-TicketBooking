public class Event {

    private final String eventName;
    private final int totalSeats;

    public Event(String eventName, int totalSeats) {
        if (totalSeats <= 0) {
            throw new IllegalArgumentException(
                    "Total seats must be a positive number. Received: " + totalSeats);
        }
        this.eventName = eventName;
        this.totalSeats = totalSeats;
    }

    public String getEventName() {
        return eventName;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    @Override
    public String toString() {
        return "Event[Name: " + eventName + ", Total Seats: " + totalSeats + "]";
    }
}
