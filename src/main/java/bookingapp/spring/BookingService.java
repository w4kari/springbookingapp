package bookingapp.spring;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class BookingService {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Booking> getAllBookings() {
        return entityManager.createQuery(
                "select b from Booking b " +
                        "join fetch b.room r " +
                        "join fetch r.hotel " +
                        "left join fetch b.guest " +
                        "order by b.id",
                Booking.class
        ).getResultList();
    }

    public List<Booking> getBookingsByGuestEmail(String email) {
        if (isBlank(email)) {
            throw new BadRequestException("Guest email is required");
        }

        return entityManager.createQuery(
                        "select b from Booking b " +
                                "join fetch b.room r " +
                                "join fetch r.hotel " +
                                "left join fetch b.guest g " +
                                "where lower(g.email) = lower(:email) " +
                                "order by b.id",
                        Booking.class
                )
                .setParameter("email", email.trim())
                .getResultList();
    }

    public List<Booking> getBookingsByGuestId(Long guestId) {
        if (guestId == null || guestId <= 0) {
            throw new BadRequestException("Guest id is required");
        }

        return entityManager.createQuery(
                        "select b from Booking b " +
                                "join fetch b.room r " +
                                "join fetch r.hotel " +
                                "left join fetch b.guest g " +
                                "where g.id = :guestId " +
                                "order by b.id",
                        Booking.class
                )
                .setParameter("guestId", guestId)
                .getResultList();
    }

    public Booking getBookingById(Long id) {
        List<Booking> bookings = entityManager.createQuery(
                        "select b from Booking b " +
                                "join fetch b.room r " +
                                "join fetch r.hotel " +
                                "left join fetch b.guest " +
                                "where b.id = :id",
                        Booking.class
                )
                .setParameter("id", id)
                .getResultList();

        if (bookings.isEmpty()) {
            throw new BookingNotFoundException("Booking with id " + id + " was not found");
        }

        return bookings.get(0);
    }

    public Booking createBooking(Booking booking) {
        validateBookingInput(booking);

        String normalizedGuestName = normalizeGuestName(booking.getGuestName());
        String normalizedGuestEmail = normalizeGuestEmail(booking.getGuestEmail());
        Room room = findRoom(booking.getHotelName(), booking.getRoomNumber());

        validateRoomAvailability(room);
        validateRoomCapacity(room, booking.getGuestsCount());
        ensureRoomIsFree(room, booking.getCheckInDate(), booking.getCheckOutDate(), null);

        Guest guest = findOrCreateGuest(normalizedGuestName, normalizedGuestEmail);
        BigDecimal totalPrice = calculateTotalPrice(room, booking.getCheckInDate(), booking.getCheckOutDate());

        booking.setGuest(guest);
        booking.setRoom(room);
        booking.setGuestName(normalizedGuestName);
        booking.setGuestEmail(normalizedGuestEmail);
        booking.setHotelName(room.getHotel().getName());
        booking.setRoomNumber(room.getNumber());
        booking.setStatus(Booking.BookingStatus.ACTIVE);
        booking.setTotalPrice(totalPrice);

        entityManager.persist(booking);
        return booking;
    }

    public Booking updateBooking(Long id, Booking updatedBooking) {
        Booking existingBooking = getBookingById(id);
        if (existingBooking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new ConflictException("Cancelled bookings cannot be updated");
        }

        validateBookingInput(updatedBooking);

        String normalizedGuestName = normalizeGuestName(updatedBooking.getGuestName());
        String normalizedGuestEmail = normalizeGuestEmail(updatedBooking.getGuestEmail());
        Room room = findRoom(updatedBooking.getHotelName(), updatedBooking.getRoomNumber());

        validateRoomAvailability(room);
        validateRoomCapacity(room, updatedBooking.getGuestsCount());
        ensureRoomIsFree(room, updatedBooking.getCheckInDate(), updatedBooking.getCheckOutDate(), id);

        Guest guest = findOrCreateGuest(normalizedGuestName, normalizedGuestEmail);
        BigDecimal totalPrice = calculateTotalPrice(room, updatedBooking.getCheckInDate(), updatedBooking.getCheckOutDate());

        existingBooking.setGuest(guest);
        existingBooking.setRoom(room);
        existingBooking.setGuestName(normalizedGuestName);
        existingBooking.setGuestEmail(normalizedGuestEmail);
        existingBooking.setHotelName(room.getHotel().getName());
        existingBooking.setRoomNumber(room.getNumber());
        existingBooking.setCheckInDate(updatedBooking.getCheckInDate());
        existingBooking.setCheckOutDate(updatedBooking.getCheckOutDate());
        existingBooking.setGuestsCount(updatedBooking.getGuestsCount());
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
            throw new BadRequestException("Request body is required");
        }
        if (isBlank(booking.getGuestName())) {
            throw new BadRequestException("Guest name is required");
        }
        if (isBlank(booking.getGuestEmail())) {
            throw new BadRequestException("Guest email is required");
        }
        if (isBlank(booking.getHotelName())) {
            throw new BadRequestException("Hotel name is required");
        }
        if (isBlank(booking.getRoomNumber())) {
            throw new BadRequestException("Room number is required");
        }
        if (booking.getCheckInDate() == null) {
            throw new BadRequestException("Check-in date is required");
        }
        if (booking.getCheckOutDate() == null) {
            throw new BadRequestException("Check-out date is required");
        }
        if (booking.getGuestsCount() == null || booking.getGuestsCount() <= 0) {
            throw new BadRequestException("Guests count must be greater than zero");
        }
        if (booking.getCheckInDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Check-in date cannot be in the past");
        }
        if (!booking.getCheckOutDate().isAfter(booking.getCheckInDate())) {
            throw new BadRequestException("Check-out date must be after check-in date");
        }
    }

    private Room findRoom(String hotelName, String roomNumber) {
        List<Room> rooms = entityManager.createQuery(
                        "select r from Room r " +
                                "join fetch r.hotel h " +
                                "where lower(h.name) = lower(:hotelName) and r.number = :roomNumber",
                        Room.class
                )
                .setParameter("hotelName", hotelName.trim())
                .setParameter("roomNumber", roomNumber.trim())
                .getResultList();

        if (rooms.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Room " + roomNumber + " in hotel '" + hotelName + "' was not found"
            );
        }

        return rooms.get(0);
    }

    private Guest findOrCreateGuest(String guestName, String guestEmail) {
        List<Guest> guests = entityManager.createQuery(
                        "select g from Guest g where lower(g.email) = lower(:email) order by g.id",
                        Guest.class
                )
                .setParameter("email", guestEmail)
                .setMaxResults(1)
                .getResultList();

        if (!guests.isEmpty()) {
            return guests.get(0);
        }

        GuestNameParts nameParts = splitGuestName(guestName);
        Guest guest = new Guest();
        guest.setFirstName(nameParts.firstName());
        guest.setLastName(nameParts.lastName());
        guest.setEmail(guestEmail);
        entityManager.persist(guest);
        return guest;
    }

    private GuestNameParts splitGuestName(String guestName) {
        String[] parts = guestName.trim().split("\\s+");
        String firstName = parts[0];
        String lastName = parts.length > 1
                ? String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length))
                : "-";
        return new GuestNameParts(firstName, lastName);
    }

    private BigDecimal calculateTotalPrice(Room room, LocalDate checkInDate, LocalDate checkOutDate) {
        long nights = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        return room.getPricePerNight().multiply(BigDecimal.valueOf(nights));
    }

    private String normalizeGuestName(String guestName) {
        return guestName.trim();
    }

    private String normalizeGuestEmail(String guestEmail) {
        return guestEmail.trim().toLowerCase(Locale.ROOT);
    }

    private void validateRoomAvailability(Room room) {
        if (room.getStatus() != Room.RoomStatus.AVAILABLE) {
            throw new ConflictException("Room is not available for booking");
        }
        if (room.getPricePerNight() == null) {
            throw new BadRequestException("Room price per night is not set");
        }
    }

    private void validateRoomCapacity(Room room, Integer guestsCount) {
        if (room.getCapacity() == null || room.getCapacity() <= 0) {
            throw new BadRequestException("Room capacity is invalid");
        }
        if (guestsCount > room.getCapacity()) {
            throw new ConflictException("Guests count exceeds room capacity");
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
            throw new ConflictException("Room is already booked for the selected dates");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record GuestNameParts(String firstName, String lastName) {
    }
}
