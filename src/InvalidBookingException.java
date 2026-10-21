// Checked on purpose, a rejected booking has to be handled and not ignored
public class InvalidBookingException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidBookingException(String message) {
        super(message);
    }
}
