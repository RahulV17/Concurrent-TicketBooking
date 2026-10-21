import java.util.ArrayList;
import java.util.List;

public class BookingService {

    private final Event event;
    private final List<Integer> availableSeats = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();

    public BookingService(Event event) {
        this.event = event;
        for (int seat = 1; seat <= event.getTotalSeats(); seat++) {
            availableSeats.add(seat);
        }
    }

    // synchronized so the check and the reserve happen as one step
    // otherwise two threads can both see the same seat as free
    public synchronized Booking bookSeat(String customerName, int seatNumber)
            throws InvalidBookingException {
        validateSeat(seatNumber);

        if (!availableSeats.contains(seatNumber)) {
            throw new InvalidBookingException(
                    "Seat " + seatNumber + " is already booked or not available.");
        }

        availableSeats.remove(Integer.valueOf(seatNumber));
        Booking booking = new Booking(customerName, seatNumber);
        bookings.add(booking);
        return booking;
    }

    // copy out, the internal list should not be modifiable from outside
    public synchronized List<Integer> getAvailableSeats() {
        return new ArrayList<>(availableSeats);
    }

    public synchronized List<Booking> getBookings() {
        return new ArrayList<>(bookings);
    }

    private void validateSeat(int seatNumber) throws InvalidBookingException {
        if (seatNumber < 1 || seatNumber > event.getTotalSeats()) {
            throw new InvalidBookingException("Seat " + seatNumber
                    + " is outside the valid range 1 to " + event.getTotalSeats() + ".");
        }
    }

    public synchronized void displaySummary() {
        System.out.println("Event: " + event.getEventName()
                + " | Total: " + event.getTotalSeats()
                + " | Booked: " + bookings.size()
                + " | Remaining: " + availableSeats.size());
    }
}
