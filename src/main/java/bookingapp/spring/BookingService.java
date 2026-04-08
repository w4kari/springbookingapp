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
                "select b from Booking b join fetch b.room r join fetch r.hotel order by b.id", Booking.class
        ).getResultList();
    }

    public List<Booking> getBookingsByGuestEmail(String email) {
        if (isBlank(email)) {
            throw new BadRequestException("Email гостя обязателен");
        }

        return entityManager.createQuery(
                        "select b from Booking b join fetch b.room r join fetch r.hotel " +
                                "where lower(b.guestEmail) = lower(:email) order by b.id",
                        Booking.class
                )
                .setParameter("email", email.trim())
                .getResultList();
    }

    public Booking getBookingById(Long id) {
        List<Booking> bookings = entityManager.createQuery(
                        "select b from Booking b join fetch b.room r join fetch r.hotel where b.id = :id",
                        Booking.class
                )
                .setParameter("id", id)
                .getResultList();
        if (bookings.isEmpty()) {
            throw new BookingNotFoundException("Бронирование с id " + id + " не найдено");
        }
        return bookings.get(0);
    }

    public Booking createBooking(Booking booking) {
        validateBookingInput(booking);

        Room room = findRoom(booking.getHotelName(), booking.getRoomNumber());
        validateRoomAvailability(room);
        validateRoomCapacity(room, booking.getGuestsCount());
        ensureRoomIsFree(room, booking.getCheckInDate(), booking.getCheckOutDate(), null);

        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        BigDecimal totalPrice = room.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        booking.setRoom(room);
        booking.setHotelName(room.getHotel().getName());
        booking.setRoomNumber(room.getNumber());
        booking.setGuestName(booking.getGuestName().trim());
        booking.setGuestEmail(booking.getGuestEmail().trim().toLowerCase());
        booking.setStatus(Booking.BookingStatus.ACTIVE);
        booking.setTotalPrice(totalPrice);

        entityManager.persist(booking);
        return booking;
    }

    public Booking updateBooking(Long id, Booking updatedBooking) {
        Booking existingBooking = getBookingById(id);
        if (existingBooking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new ConflictException("Нельзя изменять отмененное бронирование");
        }

        validateBookingInput(updatedBooking);

        Room room = findRoom(updatedBooking.getHotelName(), updatedBooking.getRoomNumber());
        validateRoomAvailability(room);
        validateRoomCapacity(room, updatedBooking.getGuestsCount());
        ensureRoomIsFree(room, updatedBooking.getCheckInDate(), updatedBooking.getCheckOutDate(), id);

        long nights = ChronoUnit.DAYS.between(updatedBooking.getCheckInDate(), updatedBooking.getCheckOutDate());
        BigDecimal totalPrice = room.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        existingBooking.setGuestName(updatedBooking.getGuestName().trim());
        existingBooking.setGuestEmail(updatedBooking.getGuestEmail().trim().toLowerCase());
        existingBooking.setHotelName(room.getHotel().getName());
        existingBooking.setRoomNumber(room.getNumber());
        existingBooking.setCheckInDate(updatedBooking.getCheckInDate());
        existingBooking.setCheckOutDate(updatedBooking.getCheckOutDate());
        existingBooking.setGuestsCount(updatedBooking.getGuestsCount());
        existingBooking.setRoom(room);
        existingBooking.setStatus(Booking.BookingStatus.ACTIVE);
        existingBooking.setTotalPrice(totalPrice);

        return entityManager.merge(existingBooking);
    }

    public Booking cancelBooking(Long id) {
        Booking booking = getBookingById(id);
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        return entityManager.merge(booking);
    }

    public void deleteBooking(Long id) {
        cancelBooking(id);
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
        if (booking.getGuestsCount() == null || booking.getGuestsCount() <= 0) {
            throw new BadRequestException("Количество гостей должно быть больше 0");
        }
        if (booking.getCheckInDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Нельзя создать бронирование в прошлом");
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

    private void validateRoomCapacity(Room room, Integer guestsCount) {
        if (room.getCapacity() == null || room.getCapacity() <= 0) {
            throw new BadRequestException("Для комнаты не указана корректная вместимость");
        }
        if (guestsCount > room.getCapacity()) {
            throw new ConflictException("Количество гостей превышает вместимость комнаты");
        }
    }

    private void ensureRoomIsFree(Room room, LocalDate checkInDate, LocalDate checkOutDate, Long bookingIdToExclude) {
        Long count = entityManager.createQuery(
                        "select count(b.id) from Booking b where b.room.id = :roomId " +
                                "and b.status = :activeStatus " +
                                "and (:bookingIdToExclude is null or b.id <> :bookingIdToExclude) " +
                                "and b.checkInDate < :checkOutDate and b.checkOutDate > :checkInDate",
                        Long.class
                )
                .setParameter("roomId", room.getId())
                .setParameter("activeStatus", Booking.BookingStatus.ACTIVE)
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
