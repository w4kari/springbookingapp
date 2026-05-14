const API = {
    hotels: "/hotels",
    rooms: "/rooms",
    bookings: "/bookings"
};

const state = {
    hotels: [],
    rooms: [],
    bookings: []
};

document.addEventListener("DOMContentLoaded", () => {
    initNavigation();
    initRefresh();
    initBookingForm();
    loadAllData();
});

function initNavigation() {
    const buttons = document.querySelectorAll(".nav-item");
    const sections = document.querySelectorAll(".section");
    const pageTitle = document.getElementById("pageTitle");

    buttons.forEach(button => {
        button.addEventListener("click", () => {
            const sectionId = button.dataset.section;

            buttons.forEach(btn => btn.classList.remove("active"));
            button.classList.add("active");

            sections.forEach(section => section.classList.remove("active"));
            document.getElementById(sectionId).classList.add("active");

            pageTitle.textContent = button.textContent.trim();
        });
    });
}

function initRefresh() {
    document.getElementById("refreshBtn").addEventListener("click", loadAllData);
}

function initBookingForm() {
    const form = document.getElementById("bookingForm");

    if (!form) {
        console.error("Форма bookingForm не найдена");
        return;
    }

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        try {
            const payload = {
                guestName: document.getElementById("guestName")?.value.trim(),
                guestEmail: document.getElementById("guestEmail")?.value.trim(),
                hotelName: document.getElementById("hotelName")?.value.trim(),
                roomNumber: document.getElementById("roomNumber")?.value.trim(),
                guestsCount: Number(document.getElementById("guestsCount")?.value),
                checkInDate: document.getElementById("checkInDate")?.value,
                checkOutDate: document.getElementById("checkOutDate")?.value
        };

            console.log("Отправка payload JSON:");
            console.log(JSON.stringify(payload, null, 2));

            const response = await fetch(API.bookings, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payload)
            });

            const responseText = await response.text();
            console.log("POST status:", response.status);
            console.log("POST response body:", responseText);

            if (!response.ok) {
                throw new Error(responseText || "Не удалось создать бронирование");
            }

            showMessage("Бронирование успешно создано", "success");
            form.reset();

            await loadAllData();
            activateSection("bookings");
        } catch (error) {
            console.error("Ошибка при создании бронирования:", error);
            showMessage(error.message || "Ошибка при создании бронирования", "error");
        }
    });
}

async function loadAllData() {
    await Promise.all([
        loadHotels(),
        loadRooms(),
        loadBookings()
    ]);

    renderStats();
    renderHotels();
    renderRooms();
    renderBookings();
    renderRecentBookings();
}

async function loadHotels() {
    try {
        const response = await fetch(API.hotels);
        state.hotels = response.ok ? await response.json() : [];
    } catch {
        state.hotels = [];
    }
}

async function loadRooms() {
    try {
        const response = await fetch(API.rooms);
        state.rooms = response.ok ? await response.json() : [];
    } catch {
        state.rooms = [];
    }
}

async function loadBookings() {
    try {
        const response = await fetch(API.bookings);
        state.bookings = response.ok ? await response.json() : [];
    } catch {
        state.bookings = [];
    }
}

function renderStats() {
    document.getElementById("hotelsCount").textContent = state.hotels.length;
    document.getElementById("roomsCount").textContent = state.rooms.length;
    document.getElementById("bookingsCount").textContent = state.bookings.length;
}

function renderHotels() {
    const container = document.getElementById("hotelsList");
    container.innerHTML = "";

    if (!state.hotels.length) {
        container.innerHTML = `<div class="empty-state">Ничего не найдено</div>`;
        return;
    }

    state.hotels.forEach(hotel => {
        const card = document.createElement("div");
        card.className = "card";
        card.innerHTML = `
            <h3>${escapeHtml(hotel.name || "Hotel")}</h3>
            <p><strong>ID:</strong> ${hotel.id ?? "-"}</p>
            <p><strong>Address:</strong> ${escapeHtml(hotel.address || "-")}</p>
            <p><strong>City:</strong> ${escapeHtml(hotel.city || "-")}</p>
            <span class="badge">Hotel</span>
        `;
        container.appendChild(card);
    });
}

function renderRooms() {
    const container = document.getElementById("roomsList");
    container.innerHTML = "";

    if (!state.rooms.length) {
        container.innerHTML = `<div class="empty-state">Ничего не найдено</div>`;
        return;
    }

    state.rooms.forEach(room => {
        const card = document.createElement("div");
        card.className = "card";
        card.innerHTML = `
            <h3>Room #${escapeHtml(String(room.number ?? room.id ?? "-"))}</h3>
            <p><strong>ID:</strong> ${room.id ?? "-"}</p>
            <p><strong>Type:</strong> ${escapeHtml(room.type || "-")}</p>
            <p><strong>Capacity:</strong> ${room.capacity ?? "-"}</p>
            <p><strong>Status:</strong> ${escapeHtml(room.status || "-")}</p>
            <span class="badge">Room</span>
        `;
        container.appendChild(card);
    });
}

function renderBookings() {
    const container = document.getElementById("bookingsList");
    container.innerHTML = "";

    if (!state.bookings.length) {
        container.innerHTML = `<div class="empty-state">Ничего не найдено</div>`;
        return;
    }

    state.bookings.forEach(booking => {
        const card = document.createElement("div");
        card.className = "card";
        card.innerHTML = `
            <h3>${escapeHtml(booking.guestName || "Guest")}</h3>
            <p><strong>ID:</strong> ${booking.id ?? "-"}</p>
            <p><strong>Email:</strong> ${escapeHtml(booking.guestEmail || "-")}</p>
            <p><strong>Hotel:</strong> ${escapeHtml(booking.hotelName || "-")}</p>
            <p><strong>Room ID:</strong> ${booking.roomId ?? booking.room?.id ?? "-"}</p>
            <p><strong>Guests:</strong> ${booking.guestsCount ?? "-"}</p>
            <p><strong>Check-in:</strong> ${escapeHtml(booking.checkInDate || "-")}</p>
            <p><strong>Check-out:</strong> ${escapeHtml(booking.checkOutDate || "-")}</p>
            <p><strong>Status:</strong> ${escapeHtml(booking.status || "-")}</p>
            <span class="badge">Booking</span>
        `;
        container.appendChild(card);
    });
}

function renderRecentBookings() {
    const container = document.getElementById("recentBookings");
    container.innerHTML = "";

    const recent = [...state.bookings].slice(0, 4);

    if (!recent.length) {
        container.innerHTML = `<div class="empty-state">Нет бронирований</div>`;
        return;
    }

    recent.forEach(booking => {
        const card = document.createElement("div");
        card.className = "booking-card";

        const guestName = escapeHtml(booking.guestName || "Гость");
        const checkIn = escapeHtml(booking.checkInDate || "-");
        const checkOut = escapeHtml(booking.checkOutDate || "-");
        const roomId = booking.roomId ?? booking.room?.id ?? "-";
        const status = escapeHtml(booking.status || "Активно");

        card.innerHTML = `
            <div class="booking-card-header">
                <div>
                    <h3>${guestName}</h3>
                    <p>${checkIn} — ${checkOut}</p>
                </div>
                <div class="room-badge">●</div>
            </div>
            <p>ID номера: ${roomId}</p>
            <div style="margin-top: 14px;">
                <span class="status-badge">${status}</span>
            </div>
        `;

        container.appendChild(card);
    });
}

function activateSection(sectionId) {
    document.querySelectorAll(".nav-item").forEach(btn => {
        btn.classList.toggle("active", btn.dataset.section === sectionId);
    });

    document.querySelectorAll(".section").forEach(section => {
        section.classList.toggle("active", section.id === sectionId);
    });

    const activeButton = document.querySelector(`.nav-item[data-section="${sectionId}"]`);
    if (activeButton) {
        pageTitle.textContent = activeButton.textContent.trim();
    }
}

async function tryReadError(response) {
    try {
        const data = await response.json();
        return data.message || data.error || JSON.stringify(data);
    } catch {
        try {
            return await response.text();
        } catch {
            return "Unknown error";
        }
    }
}

function showMessage(text, type = "success") {
    const box = document.getElementById("messageBox");
    box.textContent = text;
    box.className = `message-box ${type}`;
    box.classList.remove("hidden");

    setTimeout(() => {
        box.classList.add("hidden");
    }, 3000);
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}