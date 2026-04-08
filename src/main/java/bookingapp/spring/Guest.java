package bookingapp.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Entity
@Table(
        name = "guests",
        indexes = @Index(name = "ix_guest_email", columnList = "email")
)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    public Guest() {}

    public Long getId() { return id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}

@Service
@Transactional
class GuestService {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Guest> getAllGuests() {
        return entityManager.createQuery("select g from Guest g order by g.id", Guest.class).getResultList();
    }

    public Guest getGuestById(Long id) {
        Guest guest = entityManager.find(Guest.class, id);
        if (guest == null) {
            throw new ResourceNotFoundException("Гость с id " + id + " не найден");
        }
        return guest;
    }

    public Guest createGuest(Guest guest) {
        validateGuest(guest, null);
        normalizeGuest(guest);
        ensureUniqueEmail(guest.getEmail(), null);
        entityManager.persist(guest);
        return guest;
    }

    public Guest updateGuest(Long id, Guest updatedGuest) {
        Guest existing = getGuestById(id);
        validateGuest(updatedGuest, id);
        normalizeGuest(updatedGuest);
        ensureUniqueEmail(updatedGuest.getEmail(), id);

        existing.setFirstName(updatedGuest.getFirstName());
        existing.setLastName(updatedGuest.getLastName());
        existing.setEmail(updatedGuest.getEmail());
        existing.setPhone(updatedGuest.getPhone());
        return entityManager.merge(existing);
    }

    public void deleteGuest(Long id) {
        Guest guest = getGuestById(id);
        entityManager.remove(guest);
    }

    private void validateGuest(Guest guest, Long guestId) {
        if (guest == null) {
            throw new BadRequestException("Тело запроса обязательно");
        }
        if (isBlank(guest.getFirstName())) {
            throw new BadRequestException("Имя гостя обязательно");
        }
        if (isBlank(guest.getLastName())) {
            throw new BadRequestException("Фамилия гостя обязательна");
        }
        if (isBlank(guest.getEmail())) {
            throw new BadRequestException("Email гостя обязателен");
        }
        if (!guest.getEmail().contains("@")) {
            throw new BadRequestException("Некорректный формат email");
        }
    }

    private void normalizeGuest(Guest guest) {
        guest.setFirstName(guest.getFirstName().trim());
        guest.setLastName(guest.getLastName().trim());
        guest.setEmail(guest.getEmail().trim().toLowerCase());
        if (guest.getPhone() != null) {
            guest.setPhone(guest.getPhone().trim());
        }
    }

    private void ensureUniqueEmail(String email, Long guestIdToExclude) {
        Long count = entityManager.createQuery(
                        "select count(g.id) from Guest g where lower(g.email) = lower(:email) " +
                                "and (:guestIdToExclude is null or g.id <> :guestIdToExclude)",
                        Long.class
                )
                .setParameter("email", email)
                .setParameter("guestIdToExclude", guestIdToExclude)
                .getSingleResult();

        if (count != null && count > 0) {
            throw new ConflictException("Гость с таким email уже существует");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

@RestController
@RequestMapping("/guests")
class GuestController {

    private final GuestService guestService;

    GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    @GetMapping
    public ResponseEntity<List<Guest>> getAllGuests() {
        return ResponseEntity.ok(guestService.getAllGuests());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Guest> getGuestById(@PathVariable Long id) {
        return ResponseEntity.ok(guestService.getGuestById(id));
    }

    @PostMapping
    public ResponseEntity<Guest> createGuest(@Valid @RequestBody Guest guest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestService.createGuest(guest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Guest> updateGuest(@PathVariable Long id, @Valid @RequestBody Guest guest) {
        return ResponseEntity.ok(guestService.updateGuest(id, guest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGuest(@PathVariable Long id) {
        guestService.deleteGuest(id);
        return ResponseEntity.noContent().build();
    }
}
