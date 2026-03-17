package bookingapp.spring;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class BookingService {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Booking> getAllBookings() {
        return entityManager.createQuery(
                "select b from Booking b order by b.id", Booking.class
        ).getResultList();
    }

    public Booking getBookingById(Long id) {
        Booking booking = entityManager.find(Booking.class, id);
        if (booking == null) {
            throw new BookingNotFoundException("Бронирование с id " + id + " не найдено");
        }
        return booking;
    }

    public Booking createBooking(Booking booking) {
        validateBookingInput(booking);

        Room room = findRoom(booking.getHotelName(), booking.getRoomNumber());
        validateRoomAvailability(room);
        ensureRoomIsFree(room, booking.getCheckInDate(), booking.getCheckOutDate(), null);

        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        BigDecimal totalPrice = room.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        booking.setRoom(room);
        booking.setHotelName(room.getHotel().getName());
        booking.setRoomNumber(room.getNumber());
        booking.setTotalPrice(totalPrice);

        entityManager.persist(booking);
        return booking;
    }

    public Booking updateBooking(Long id, Booking updatedBooking) {
        Booking existingBooking = getBookingById(id);
        validateBookingInput(updatedBooking);

        Room room = findRoom(updatedBooking.getHotelName(), updatedBooking.getRoomNumber());
        validateRoomAvailability(room);
        ensureRoomIsFree(room, updatedBooking.getCheckInDate(), updatedBooking.getCheckOutDate(), id);

        long nights = ChronoUnit.DAYS.between(updatedBooking.getCheckInDate(), updatedBooking.getCheckOutDate());
        BigDecimal totalPrice = room.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        existingBooking.setGuestName(updatedBooking.getGuestName().trim());
        existingBooking.setGuestEmail(updatedBooking.getGuestEmail().trim().toLowerCase());
        existingBooking.setHotelName(room.getHotel().getName());
        existingBooking.setRoomNumber(room.getNumber());
        existingBooking.setCheckInDate(updatedBooking.getCheckInDate());
        existingBooking.setCheckOutDate(updatedBooking.getCheckOutDate());
        existingBooking.setRoom(room);
        existingBooking.setTotalPrice(totalPrice);

        return entityManager.merge(existingBooking);
    }

    public void deleteBooking(Long id) {
        Booking booking = getBookingById(id);
        entityManager.remove(booking);
    }

    private void validateBookingInput(Booking booking) {
        if (booking == null) {
            throw new BadRequestException("Тело запроса обязательно");
        }
        if (isBlank(booking.getGuestName())) {
            throw new BadRequestException("Имя гостя обязательно");
        }
        if (isBlank(booking.getGuestEmail())) {
            throw new BadRequestException("Email гостя обязателен");
        }
        if (isBlank(booking.getHotelName())) {
            throw new BadRequestException("Название отеля обязательно");
        }
        if (isBlank(booking.getRoomNumber())) {
            throw new BadRequestException("Номер комнаты обязателен");
        }
        if (booking.getCheckInDate() == null) {
            throw new BadRequestException("Дата заезда обязательна");
        }
        if (booking.getCheckOutDate() == null) {
            throw new BadRequestException("Дата выезда обязательна");
        }
        if (!booking.getCheckOutDate().isAfter(booking.getCheckInDate())) {
            throw new BadRequestException("Дата выезда должна быть позже даты заезда");
        }
    }

    private Room findRoom(String hotelName, String roomNumber) {
        List<Room> rooms = entityManager.createQuery(
                "select r from Room r join fetch r.hotel h where lower(h.name) = lower(:hotelName) and r.number = :roomNumber",
                Room.class
        )
                .setParameter("hotelName", hotelName.trim())
                .setParameter("roomNumber", roomNumber.trim())
                .getResultList();

        if (rooms.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Комната " + roomNumber + " в отеле '" + hotelName + "' не найдена"
            );
        }

        return rooms.get(0);
    }

    private void validateRoomAvailability(Room room) {
        if (room.getStatus() != Room.RoomStatus.AVAILABLE) {
            throw new ConflictException("Комната недоступна для бронирования");
        }
        if (room.getPricePerNight() == null) {
            throw new BadRequestException("Для комнаты не указана цена за ночь");
        }
    }

    private void ensureRoomIsFree(Room room, LocalDate checkInDate, LocalDate checkOutDate, Long bookingIdToExclude) {
        Long count = entityManager.createQuery(
                "select count(b.id) from Booking b where b.room.id = :roomId " +
                        "and (:bookingIdToExclude is null or b.id <> :bookingIdToExclude) " +
                        "and b.checkInDate < :checkOutDate and b.checkOutDate > :checkInDate",
                Long.class
        )
                .setParameter("roomId", room.getId())
                .setParameter("bookingIdToExclude", bookingIdToExclude)
                .setParameter("checkInDate", checkInDate)
                .setParameter("checkOutDate", checkOutDate)
                .getSingleResult();

        if (count != null && count > 0) {
            throw new ConflictException("Комната уже занята на выбранные даты");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String message) {
        super(message);
    }
}

class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}

class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
