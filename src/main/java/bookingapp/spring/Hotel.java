package bookingapp.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hotels")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "rooms"})
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    private String address;
    private Integer stars;

    @Column(length = 2000)
    private String description;

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Room> rooms = new ArrayList<>();

    public Hotel() {}

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Room> getRooms() { return rooms; }

    public void addRoom(Room room) {
        rooms.add(room);
        room.setHotel(this);
    }

    public void removeRoom(Room room) {
        rooms.remove(room);
        room.setHotel(null);
    }
}

@Service
@Transactional
class HotelService {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Hotel> getHotels(String city) {
        if (city == null || city.trim().isEmpty()) {
            return entityManager.createQuery("select h from Hotel h order by h.id", Hotel.class).getResultList();
        }

        return entityManager.createQuery(
                        "select h from Hotel h where lower(h.city) = lower(:city) order by h.id",
                        Hotel.class
                )
                .setParameter("city", city.trim())
                .getResultList();
    }

    public Hotel getHotelById(Long id) {
        Hotel hotel = entityManager.find(Hotel.class, id);
        if (hotel == null) {
            throw new ResourceNotFoundException("Отель с id " + id + " не найден");
        }
        return hotel;
    }

    public Hotel createHotel(Hotel hotel) {
        validateHotel(hotel);
        normalizeHotel(hotel);
        entityManager.persist(hotel);
        return hotel;
    }

    public Hotel updateHotel(Long id, Hotel updatedHotel) {
        Hotel existing = getHotelById(id);
        validateHotel(updatedHotel);
        normalizeHotel(updatedHotel);

        existing.setName(updatedHotel.getName());
        existing.setCity(updatedHotel.getCity());
        existing.setAddress(updatedHotel.getAddress());
        existing.setStars(updatedHotel.getStars());
        existing.setDescription(updatedHotel.getDescription());

        return entityManager.merge(existing);
    }

    public void deleteHotel(Long id) {
        Hotel hotel = getHotelById(id);
        entityManager.remove(hotel);
    }

    public List<Room> getHotelRooms(Long hotelId) {
        getHotelById(hotelId);
        return entityManager.createQuery(
                        "select r from Room r where r.hotel.id = :hotelId order by r.id",
                        Room.class
                )
                .setParameter("hotelId", hotelId)
                .getResultList();
    }

    private void validateHotel(Hotel hotel) {
        if (hotel == null) {
            throw new BadRequestException("Тело запроса обязательно");
        }
        if (isBlank(hotel.getName())) {
            throw new BadRequestException("Название отеля обязательно");
        }
        if (isBlank(hotel.getCity())) {
            throw new BadRequestException("Город обязателен");
        }
        if (hotel.getStars() != null && (hotel.getStars() < 1 || hotel.getStars() > 5)) {
            throw new BadRequestException("Количество звезд должно быть от 1 до 5");
        }
    }

    private void normalizeHotel(Hotel hotel) {
        hotel.setName(hotel.getName().trim());
        hotel.setCity(hotel.getCity().trim());
        if (hotel.getAddress() != null) {
            hotel.setAddress(hotel.getAddress().trim());
        }
        if (hotel.getDescription() != null) {
            hotel.setDescription(hotel.getDescription().trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

@RestController
@RequestMapping("/hotels")
class HotelController {

    private final HotelService hotelService;

    HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @GetMapping
    public ResponseEntity<List<Hotel>> getHotels(@RequestParam(required = false) String city) {
        return ResponseEntity.ok(hotelService.getHotels(city));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Hotel> getHotelById(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getHotelById(id));
    }

    @PostMapping
    public ResponseEntity<Hotel> createHotel(@Valid @RequestBody Hotel hotel) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotelService.createHotel(hotel));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Hotel> updateHotel(@PathVariable Long id, @Valid @RequestBody Hotel hotel) {
        return ResponseEntity.ok(hotelService.updateHotel(id, hotel));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHotel(@PathVariable Long id) {
        hotelService.deleteHotel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/rooms")
    public ResponseEntity<List<Room>> getHotelRooms(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getHotelRooms(id));
    }
}
