package bookingapp.spring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
public class Booking {

    public enum BookingStatus { ACTIVE, CANCELLED, OUT_OF_SERVICE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @Column(name = "guest_name", nullable = false)
    @NotBlank(message = "Guest name is required")
    private String guestName;

    @Transient
    private String guestEmail;

    @Column(name = "hotel_name", nullable = false)
    @NotBlank(message = "Hotel name is required")
    private String hotelName;

    @Column(name = "room_number", nullable = false)
    @NotBlank(message = "Room number is required")
    private String roomNumber;

    @Column(name = "check_in_date", nullable = false)
    @NotNull(message = "Check-in date is required")
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    @NotNull(message = "Check-out date is required")
    private LocalDate checkOutDate;

    @Column(name = "guests_count", nullable = false)
    @NotNull(message = "Guests count is required")
    @Positive(message = "Guests count must be greater than zero")
    private Integer guestsCount = 1;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    @JsonIgnore
    private Guest guest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @JsonIgnore
    private Room room;

    public Booking() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    @JsonProperty("guestEmail")
    public String getGuestEmail() {
        if (guest != null && guest.getEmail() != null) {
            return guest.getEmail();
        }
        return guestEmail;
    }

    @JsonProperty("guestEmail")
    public void setGuestEmail(String guestEmail) {
        this.guestEmail = guestEmail;
    }

    public String getHotelName() {
        return hotelName;
    }

    public void setHotelName(String hotelName) {
        this.hotelName = hotelName;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public Integer getGuestsCount() {
        return guestsCount;
    }

    public void setGuestsCount(Integer guestsCount) {
        this.guestsCount = guestsCount;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Guest getGuest() {
        return guest;
    }

    public void setGuest(Guest guest) {
        this.guest = guest;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getGuestId() {
        return guest != null ? guest.getId() : null;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getRoomId() {
        return room != null ? room.getId() : null;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}
