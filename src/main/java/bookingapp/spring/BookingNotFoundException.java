package bookingapp.spring;

import org.springframework.http.HttpStatus;

public class BookingNotFoundException extends ApiException {

    public BookingNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
