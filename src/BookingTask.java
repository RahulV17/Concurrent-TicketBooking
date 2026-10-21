public class BookingTask implements Runnable {

    private final BookingService bookingService;
    private final String customerName;
    private final int seatNumber;

    public BookingTask(BookingService bookingService, String customerName, int seatNumber) {
        this.bookingService = bookingService;
        this.customerName = customerName;
        this.seatNumber = seatNumber;
    }

    // catch the rejection so one failed booking does not kill the other tasks
    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        try {
            Booking booking = bookingService.bookSeat(customerName, seatNumber);
            System.out.printf("[%s] [SUCCESS] %s successfully booked Seat %d!%n",
                    threadName, booking.getCustomerName(), booking.getSeatNumber());
        } catch (InvalidBookingException e) {
            System.out.printf("[%s] [REJECTED] %s could not book Seat %d: %s%n",
                    threadName, customerName, seatNumber, e.getMessage());
        } finally {
            System.out.printf("[%s] [DONE] Booking attempt for %s (Seat %d) completed.%n",
                    threadName, customerName, seatNumber);
        }
    }
}
