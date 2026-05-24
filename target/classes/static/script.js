const API = {
    hotels: "/hotels",
    rooms: "/rooms",
    guests: "/guests",
    bookings: "/bookings"
};

const state = {
    hotels: [],
    rooms: [],
    guests: [],
    bookings: []
};

document.addEventListener("DOMContentLoaded", () => {
    initNavigation();
    initRefresh();
    initHotelControls();
    initRoomControls();
    initGuestControls();
    initBookingForm();
    initBookingTools();
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

function initHotelControls() {
    const filterForm = document.getElementById("hotelFilterForm");
    const resetButton = document.getElementById("resetHotelFilterBtn");
    const createForm = document.getElementById("hotelForm");

    filterForm?.addEventListener("submit", async event => {
        event.preventDefault();
        await loadHotels(getValue("hotelCityFilter"));
        renderStats();
        renderHotels();
    });

    resetButton?.addEventListener("click", async () => {
        document.getElementById("hotelCityFilter").value = "";
        await loadHotels();
        renderStats();
        renderHotels();
    });

    createForm?.addEventListener("submit", async event => {
        event.preventDefault();

        try {
            await apiRequest(API.hotels, {
                method: "POST",
                body: hotelPayload()
            });
            showMessage("Hotel created", "success");
            createForm.reset();
            await loadAllData();
        } catch (error) {
            showMessage(error.message, "error");
        }
    });
}

function initRoomControls() {
    const filterForm = document.getElementById("roomFilterForm");
    const resetButton = document.getElementById("resetRoomFilterBtn");
    const createForm = document.getElementById("roomForm");

    filterForm?.addEventListener("submit", async event => {
        event.preventDefault();
        await loadRooms({
            minPrice: getValue("roomMinPrice"),
            maxPrice: getValue("roomMaxPrice")
        });
        renderStats();
        renderRooms();
    });

    resetButton?.addEventListener("click", async () => {
        document.getElementById("roomMinPrice").value = "";
        document.getElementById("roomMaxPrice").value = "";
        await loadRooms();
        renderStats();
        renderRooms();
    });

    createForm?.addEventListener("submit", async event => {
        event.preventDefault();

        try {
            await apiRequest(API.rooms, {
                method: "POST",
                body: roomPayload()
            });
            showMessage("Room created", "success");
            createForm.reset();
            await loadAllData();
        } catch (error) {
            showMessage(error.message, "error");
        }
    });
}

function initGuestControls() {
    const form = document.getElementById("guestForm");

    form?.addEventListener("submit", async event => {
        event.preventDefault();

        try {
            await apiRequest(API.guests, {
                method: "POST",
                body: guestPayload()
            });
            showMessage("Guest created", "success");
            form.reset();
            await loadGuests();
            renderGuests();
        } catch (error) {
            showMessage(error.message, "error");
        }
    });
}

function initBookingForm() {
    const form = document.getElementById("bookingForm");

    if (!form) {
        return;
    }

    form.addEventListener("submit", async event => {
        event.preventDefault();

        try {
            await apiRequest(API.bookings, {
                method: "POST",
                body: bookingPayload()
            });
            showMessage("Booking created", "success");
            form.reset();
            await loadAllData();
            activateSection("bookings");
        } catch (error) {
            showMessage(error.message, "error");
        }
    });
}

function initBookingTools() {
    const searchForm = document.getElementById("bookingSearchForm");
    const resetButton = document.getElementById("resetBookingSearchBtn");
    const updateForm = document.getElementById("bookingUpdateForm");
    const bookingsList = document.getElementById("bookingsList");

    searchForm?.addEventListener("submit", async event => {
        event.preventDefault();

        try {
            const bookingId = getValue("bookingSearchId");
            const guestId = getValue("bookingSearchGuestId");
            const email = getValue("bookingSearchEmail");

            if (bookingId) {
                state.bookings = [await apiRequest(`${API.bookings}/${bookingId}`)];
            } else if (guestId) {
                state.bookings = await apiRequest(`${API.bookings}/by-guest-id${toQuery({ guestId })}`);
            } else if (email) {
                state.bookings = await apiRequest(`${API.bookings}/by-guest-email${toQuery({ email })}`);
            } else {
                await loadBookings();
            }

            renderStats();
            renderBookings();
            renderRecentBookings();
        } catch (error) {
            showMessage(error.message, "error");
        }
    });

    resetButton?.addEventListener("click", async () => {
        searchForm.reset();
        await loadBookings();
        renderStats();
        renderBookings();
        renderRecentBookings();
    });

    updateForm?.addEventListener("submit", async event => {
        event.preventDefault();

        try {
            const id = getValue("updateBookingId");
            await apiRequest(`${API.bookings}/${id}`, {
                method: "PUT",
                body: updateBookingPayload()
            });
            showMessage("Booking updated", "success");
            updateForm.reset();
            await loadBookings();
            renderStats();
            renderBookings();
            renderRecentBookings();
        } catch (error) {
            showMessage(error.message, "error");
        }
    });

    bookingsList?.addEventListener("click", async event => {
        const button = event.target.closest("[data-booking-action]");
        if (!button) {
            return;
        }

        const id = Number(button.dataset.bookingId);
        const action = button.dataset.bookingAction;

        if (action === "edit") {
            fillBookingUpdateForm(id);
            return;
        }

        if (action === "cancel") {
            try {
                await apiRequest(`${API.bookings}/${id}`, { method: "DELETE" });
                showMessage("Booking cancelled", "success");
                await loadBookings();
                renderStats();
                renderBookings();
                renderRecentBookings();
            } catch (error) {
                showMessage(error.message, "error");
            }
        }
    });
}

async function loadAllData() {
    await Promise.all([
        loadHotels(),
        loadRooms(),
        loadGuests(),
        loadBookings()
    ]);

    renderStats();
    renderHotels();
    renderRooms();
    renderGuests();
    renderBookings();
    renderRecentBookings();
}

async function loadHotels(city = "") {
    try {
        state.hotels = await apiRequest(`${API.hotels}${toQuery({ city })}`);
    } catch {
        state.hotels = [];
    }
}

async function loadRooms(filters = {}) {
    try {
        state.rooms = await apiRequest(`${API.rooms}${toQuery(filters)}`);
    } catch {
        state.rooms = [];
    }
}

async function loadGuests() {
    try {
        state.guests = await apiRequest(API.guests);
    } catch {
        state.guests = [];
    }
}

async function loadBookings() {
    try {
        state.bookings = await apiRequest(API.bookings);
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
        container.innerHTML = `<div class="empty-state">No hotels found</div>`;
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
            <p><strong>Stars:</strong> ${hotel.stars ?? "-"}</p>
            <span class="badge">Hotel</span>
        `;
        container.appendChild(card);
    });
}

function renderRooms() {
    const container = document.getElementById("roomsList");
    container.innerHTML = "";

    if (!state.rooms.length) {
        container.innerHTML = `<div class="empty-state">No rooms found</div>`;
        return;
    }

    const groups = groupRoomsByHotel(state.rooms);

    groups.forEach(group => {
        const section = document.createElement("section");
        section.className = "room-hotel-group";

        const roomsGrid = document.createElement("div");
        roomsGrid.className = "cards-grid";

        group.rooms.forEach(room => {
            const hotelName = getRoomHotel(room).name;
            const card = document.createElement("div");
            card.className = "card";
            card.innerHTML = `
                <h3>Room #${escapeHtml(String(room.number ?? room.id ?? "-"))}</h3>
                <p><strong>Hotel:</strong> ${escapeHtml(hotelName)}</p>
                <p><strong>ID:</strong> ${room.id ?? "-"}</p>
                <p><strong>Type:</strong> ${escapeHtml(room.type || "-")}</p>
                <p><strong>Capacity:</strong> ${room.capacity ?? "-"}</p>
                <p><strong>Price:</strong> ${room.pricePerNight ?? "-"}</p>
                <p><strong>Status:</strong> ${escapeHtml(room.status || "-")}</p>
                <span class="badge">Room</span>
            `;
            roomsGrid.appendChild(card);
        });

        section.innerHTML = `
            <div class="room-hotel-header">
                <h3>${escapeHtml(group.hotel.name)}</h3>
                <span>${group.rooms.length} room${group.rooms.length === 1 ? "" : "s"}</span>
            </div>
        `;
        section.appendChild(roomsGrid);
        container.appendChild(section);
    });
}

function renderGuests() {
    const container = document.getElementById("guestsList");
    container.innerHTML = "";

    if (!state.guests.length) {
        container.innerHTML = `<div class="empty-state">No guests found</div>`;
        return;
    }

    state.guests.forEach(guest => {
        const card = document.createElement("div");
        card.className = "card";
        card.innerHTML = `
            <h3>${escapeHtml(`${guest.firstName || ""} ${guest.lastName || ""}`.trim() || "Guest")}</h3>
            <p><strong>ID:</strong> ${guest.id ?? "-"}</p>
            <p><strong>Email:</strong> ${escapeHtml(guest.email || "-")}</p>
            <p><strong>Phone:</strong> ${escapeHtml(guest.phone || "-")}</p>
            <span class="badge">Guest</span>
        `;
        container.appendChild(card);
    });
}

function renderBookings() {
    const container = document.getElementById("bookingsList");
    container.innerHTML = "";

    if (!state.bookings.length) {
        container.innerHTML = `<div class="empty-state">No bookings found</div>`;
        return;
    }

    state.bookings.forEach(booking => {
        const card = document.createElement("div");
        card.className = "card";
        card.innerHTML = `
            <h3>${escapeHtml(booking.guestName || "Guest")}</h3>
            <p><strong>ID:</strong> ${booking.id ?? "-"}</p>
            <p><strong>Guest ID:</strong> ${booking.guestId ?? "-"}</p>
            <p><strong>Email:</strong> ${escapeHtml(booking.guestEmail || "-")}</p>
            <p><strong>Hotel:</strong> ${escapeHtml(booking.hotelName || "-")}</p>
            <p><strong>Room:</strong> ${escapeHtml(booking.roomNumber || String(booking.roomId ?? booking.room?.id ?? "-"))}</p>
            <p><strong>Guests:</strong> ${booking.guestsCount ?? "-"}</p>
            <p><strong>Check-in:</strong> ${escapeHtml(booking.checkInDate || "-")}</p>
            <p><strong>Check-out:</strong> ${escapeHtml(booking.checkOutDate || "-")}</p>
            <p><strong>Total:</strong> ${escapeHtml(booking.totalPrice || "-")}</p>
            <p><strong>Status:</strong> ${escapeHtml(booking.status || "-")}</p>
            <div class="card-actions">
                <button type="button" class="secondary-btn" data-booking-action="edit" data-booking-id="${booking.id}">Edit</button>
                <button type="button" class="danger-btn" data-booking-action="cancel" data-booking-id="${booking.id}">Cancel</button>
            </div>
        `;
        container.appendChild(card);
    });
}

function renderRecentBookings() {
    const container = document.getElementById("recentBookings");
    container.innerHTML = "";

    const recent = [...state.bookings].slice(-4).reverse();

    if (!recent.length) {
        container.innerHTML = `<div class="empty-state">No bookings yet</div>`;
        return;
    }

    recent.forEach(booking => {
        const card = document.createElement("div");
        card.className = "booking-card";

        const guestName = escapeHtml(booking.guestName || "Guest");
        const guestEmail = escapeHtml(booking.guestEmail || "-");
        const checkIn = escapeHtml(booking.checkInDate || "-");
        const checkOut = escapeHtml(booking.checkOutDate || "-");
        const roomLabel = escapeHtml(booking.roomNumber || String(booking.roomId ?? booking.room?.id ?? "-"));
        const status = escapeHtml(booking.status || "ACTIVE");

        card.innerHTML = `
            <div class="booking-card-header">
                <div>
                    <h3>${guestName}</h3>
                    <p>${checkIn} - ${checkOut}</p>
                </div>
                <div class="room-badge">Room</div>
            </div>
            <p>Email: ${guestEmail}</p>
            <p>Room: ${roomLabel}</p>
            <div style="margin-top: 14px;">
                <span class="status-badge">${status}</span>
            </div>
        `;

        container.appendChild(card);
    });
}

function fillBookingUpdateForm(id) {
    const booking = state.bookings.find(item => item.id === id);
    if (!booking) {
        showMessage("Booking not found in current list", "error");
        return;
    }

    document.getElementById("updateBookingId").value = booking.id ?? "";
    document.getElementById("updateGuestName").value = booking.guestName ?? "";
    document.getElementById("updateGuestEmail").value = booking.guestEmail ?? "";
    document.getElementById("updateHotelName").value = booking.hotelName ?? "";
    document.getElementById("updateRoomNumber").value = booking.roomNumber ?? "";
    document.getElementById("updateGuestsCount").value = booking.guestsCount ?? "";
    document.getElementById("updateCheckInDate").value = booking.checkInDate ?? "";
    document.getElementById("updateCheckOutDate").value = booking.checkOutDate ?? "";
}

function groupRoomsByHotel(rooms) {
    const groups = new Map();

    rooms.forEach(room => {
        const hotel = getRoomHotel(room);
        const key = hotel.id ?? hotel.name;

        if (!groups.has(key)) {
            groups.set(key, {
                hotel,
                rooms: []
            });
        }

        groups.get(key).rooms.push(room);
    });

    return Array.from(groups.values());
}

function getRoomHotel(room) {
    const hotelFromRoom = room.hotel || {};
    const hotelFromState = state.hotels.find(hotel => hotel.id === hotelFromRoom.id) || {};

    return {
        id: hotelFromRoom.id ?? hotelFromState.id ?? "unknown",
        name: hotelFromRoom.name || hotelFromState.name || "Unknown hotel"
    };
}

function hotelPayload() {
    return compactObject({
        name: getValue("hotelCreateName"),
        city: getValue("hotelCreateCity"),
        address: getValue("hotelCreateAddress"),
        stars: optionalNumber("hotelCreateStars"),
        description: getValue("hotelCreateDescription")
    });
}

function roomPayload() {
    return {
        hotel: {
            id: Number(getValue("roomCreateHotelId"))
        },
        number: getValue("roomCreateNumber"),
        type: getValue("roomCreateType"),
        capacity: Number(getValue("roomCreateCapacity")),
        pricePerNight: Number(getValue("roomCreatePrice")),
        status: getValue("roomCreateStatus")
    };
}

function guestPayload() {
    return compactObject({
        firstName: getValue("guestCreateFirstName"),
        lastName: getValue("guestCreateLastName"),
        email: getValue("guestCreateEmail"),
        phone: getValue("guestCreatePhone")
    });
}

function bookingPayload() {
    return {
        guestName: getValue("guestName"),
        guestEmail: getValue("guestEmail"),
        hotelName: getValue("hotelName"),
        roomNumber: getValue("roomNumber"),
        guestsCount: Number(getValue("guestsCount")),
        checkInDate: getValue("checkInDate"),
        checkOutDate: getValue("checkOutDate")
    };
}

function updateBookingPayload() {
    return {
        guestName: getValue("updateGuestName"),
        guestEmail: getValue("updateGuestEmail"),
        hotelName: getValue("updateHotelName"),
        roomNumber: getValue("updateRoomNumber"),
        guestsCount: Number(getValue("updateGuestsCount")),
        checkInDate: getValue("updateCheckInDate"),
        checkOutDate: getValue("updateCheckOutDate")
    };
}

async function apiRequest(url, options = {}) {
    const requestOptions = {
        ...options,
        headers: {
            ...(options.headers || {})
        }
    };

    if (options.body !== undefined) {
        requestOptions.headers["Content-Type"] = "application/json";
        requestOptions.body = JSON.stringify(options.body);
    }

    const response = await fetch(url, requestOptions);
    const responseText = await response.text();
    const data = parseResponse(responseText);

    if (!response.ok) {
        throw new Error(readApiError(data, response.status));
    }

    return data;
}

function parseResponse(text) {
    if (!text) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

function readApiError(data, status) {
    if (!data) {
        return `Request failed with status ${status}`;
    }
    if (typeof data === "string") {
        return data;
    }
    return data.message || data.error || JSON.stringify(data);
}

function toQuery(params) {
    const query = new URLSearchParams();

    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            query.set(key, value);
        }
    });

    const queryString = query.toString();
    return queryString ? `?${queryString}` : "";
}

function compactObject(object) {
    return Object.entries(object).reduce((result, [key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            result[key] = value;
        }
        return result;
    }, {});
}

function optionalNumber(id) {
    const value = getValue(id);
    return value ? Number(value) : undefined;
}

function getValue(id) {
    return document.getElementById(id)?.value.trim() || "";
}

function activateSection(sectionId) {
    document.querySelectorAll(".nav-item").forEach(btn => {
        btn.classList.toggle("active", btn.dataset.section === sectionId);
    });

    document.querySelectorAll(".section").forEach(section => {
        section.classList.toggle("active", section.id === sectionId);
    });

    const activeButton = document.querySelector(`.nav-item[data-section="${sectionId}"]`);
    const pageTitle = document.getElementById("pageTitle");
    if (activeButton && pageTitle) {
        pageTitle.textContent = activeButton.textContent.trim();
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
