package bookingapp.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(name = "uk_room_hotel_number", columnNames = {"hotel_id", "number"})
)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Room {

    public enum RoomType { SINGLE, DOUBLE, SUITE }
    public enum RoomStatus { AVAILABLE, OUT_OF_SERVICE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    @JsonIgnoreProperties({"rooms", "hibernateLazyInitializer", "handler"})
    private Hotel hotel;

    @Column(nullable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerNight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.AVAILABLE;

    public Room() {}

    public Long getId() { return id; }

    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public RoomType getType() { return type; }
    public void setType(RoomType type) { this.type = type; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public BigDecimal getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(BigDecimal pricePerNight) { this.pricePerNight = pricePerNight; }

    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }
}

@Service
@Transactional
class RoomService {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Room> getRooms(Integer minCapacity, BigDecimal minPrice, BigDecimal maxPrice, Long hotelId) {
        return entityManager.createQuery(
                        "select r from Room r where (:hotelId is null or r.hotel.id = :hotelId) " +
                                "and (:minCapacity is null or r.capacity >= :minCapacity) " +
                                "and (:minPrice is null or r.pricePerNight >= :minPrice) " +
                                "and (:maxPrice is null or r.pricePerNight <= :maxPrice) " +
                                "order by r.id",
                        Room.class
                )
                .setParameter("hotelId", hotelId)
                .setParameter("minCapacity", minCapacity)
                .setParameter("minPrice", minPrice)
                .setParameter("maxPrice", maxPrice)
                .getResultList();
    }

    public List<Room> getFreeRooms(LocalDate checkInDate, LocalDate checkOutDate, Integer minCapacity, BigDecimal maxPrice) {
        if (checkInDate == null || checkOutDate == null) {
            throw new BadRequestException("Для поиска свободных комнат обязательны checkInDate и checkOutDate");
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new BadRequestException("Дата выезда должна быть позже даты заезда");
        }

        return entityManager.createQuery(
                        "select r from Room r where r.status = :availableStatus " +
                                "and (:minCapacity is null or r.capacity >= :minCapacity) " +
                                "and (:maxPrice is null or r.pricePerNight <= :maxPrice) " +
                                "and not exists (" +
                                "select b.id from Booking b where b.room.id = r.id and b.status = :activeBookingStatus " +
                                "and b.checkInDate < :checkOutDate and b.checkOutDate > :checkInDate" +
                                ") order by r.id",
                        Room.class
                )
                .setParameter("availableStatus", Room.RoomStatus.AVAILABLE)
                .setParameter("activeBookingStatus", Booking.BookingStatus.ACTIVE)
                .setParameter("minCapacity", minCapacity)
                .setParameter("maxPrice", maxPrice)
                .setParameter("checkInDate", checkInDate)
                .setParameter("checkOutDate", checkOutDate)
                .getResultList();
    }

    public Room getRoomById(Long id) {
        Room room = entityManager.find(Room.class, id);
        if (room == null) {
            throw new ResourceNotFoundException("Комната с id " + id + " не найдена");
        }
        return room;
    }

    public Room createRoom(Room room) {
        validateRoom(room);
        normalizeRoom(room);

        Long hotelId = extractHotelId(room);
        Hotel hotel = entityManager.find(Hotel.class, hotelId);
        if (hotel == null) {
            throw new ResourceNotFoundException("Отель с id " + hotelId + " не найден");
        }

        ensureUniqueRoomNumber(hotelId, room.getNumber(), null);
        room.setHotel(hotel);

        entityManager.persist(room);
        return room;
    }

    public Room updateRoom(Long id, Room updatedRoom) {
        Room existing = getRoomById(id);
        validateRoom(updatedRoom);
        normalizeRoom(updatedRoom);

        Long hotelId = extractHotelId(updatedRoom);
        Hotel hotel = entityManager.find(Hotel.class, hotelId);
        if (hotel == null) {
            throw new ResourceNotFoundException("Отель с id " + hotelId + " не найден");
        }

        ensureUniqueRoomNumber(hotelId, updatedRoom.getNumber(), id);

        existing.setHotel(hotel);
        existing.setNumber(updatedRoom.getNumber());
        existing.setType(updatedRoom.getType());
        existing.setCapacity(updatedRoom.getCapacity());
        existing.setPricePerNight(updatedRoom.getPricePerNight());
        existing.setStatus(updatedRoom.getStatus());

        return entityManager.merge(existing);
    }

    public void deleteRoom(Long id) {
        Room room = getRoomById(id);
        entityManager.remove(room);
    }

    private void validateRoom(Room room) {
        if (room == null) {
            throw new BadRequestException("Тело запроса обязательно");
        }
        if (extractHotelId(room) == null) {
            throw new BadRequestException("Для комнаты обязательно указать hotel.id");
        }
        if (isBlank(room.getNumber())) {
            throw new BadRequestException("Номер комнаты обязателен");
        }
        if (room.getType() == null) {
            throw new BadRequestException("Тип комнаты обязателен");
        }
        if (room.getCapacity() == null || room.getCapacity() <= 0) {
            throw new BadRequestException("Вместимость комнаты должна быть больше 0");
        }
        if (room.getPricePerNight() == null || room.getPricePerNight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Цена комнаты должна быть больше 0");
        }
        if (room.getStatus() == null) {
            throw new BadRequestException("Статус комнаты обязателен");
        }
    }

    private void normalizeRoom(Room room) {
        room.setNumber(room.getNumber().trim());
    }

    private Long extractHotelId(Room room) {
        if (room == null || room.getHotel() == null) {
            return null;
        }
        return room.getHotel().getId();
    }

    private void ensureUniqueRoomNumber(Long hotelId, String roomNumber, Long roomIdToExclude) {
        Long count = entityManager.createQuery(
                        "select count(r.id) from Room r where r.hotel.id = :hotelId and lower(r.number) = lower(:roomNumber) " +
                                "and (:roomIdToExclude is null or r.id <> :roomIdToExclude)",
                        Long.class
                )
                .setParameter("hotelId", hotelId)
                .setParameter("roomNumber", roomNumber)
                .setParameter("roomIdToExclude", roomIdToExclude)
                .getSingleResult();

        if (count != null && count > 0) {
            throw new ConflictException("Комната с таким номером уже существует в этом отеле");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

@RestController
@RequestMapping("/rooms")
class RoomController {

    private final RoomService roomService;

    RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public ResponseEntity<List<Room>> getRooms(
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Long hotelId
    ) {
        return ResponseEntity.ok(roomService.getRooms(minCapacity, minPrice, maxPrice, hotelId));
    }

    @GetMapping("/free")
    public ResponseEntity<List<Room>> getFreeRooms(
            @RequestParam LocalDate checkInDate,
            @RequestParam LocalDate checkOutDate,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        return ResponseEntity.ok(roomService.getFreeRooms(checkInDate, checkOutDate, minCapacity, maxPrice));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Room> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @PostMapping
    public ResponseEntity<Room> createRoom(@Valid @RequestBody Room room) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(room));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Room> updateRoom(@PathVariable Long id, @Valid @RequestBody Room room) {
        return ResponseEntity.ok(roomService.updateRoom(id, room));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
}
