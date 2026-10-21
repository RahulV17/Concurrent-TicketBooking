public class Booking {

    private final String customerName;
    private final int seatNumber;

    public Booking(String customerName, int seatNumber) {
        this.customerName = customerName;
        this.seatNumber = seatNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    @Override
    public String toString() {
        return "Booking[Customer: " + customerName + ", Seat: " + seatNumber + "]";
    }
}
