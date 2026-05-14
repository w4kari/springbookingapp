-- Seed data for HotelBook project
-- Inserts test hotels and rooms for demo

INSERT INTO public.hotels (id, address, city, description, name, stars)
VALUES
(1, 'Test address', 'Chisinau', 'Test hotel for demo', 'Hilton', 5),
(2, 'Strada Mitropolit Varlaam 77', 'Chisinau', 'Central hotel in Chisinau', 'Radisson Blu', 5),
(3, 'Strada Puskin 32', 'Chisinau', 'Business hotel in city center', 'Bristol Hotel', 4),
(4, 'Eugen Doga 2A', 'Chisinau', 'Hotel near city park', 'City Park Hotel', 4)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.rooms (id, capacity, number, price_per_night, status, type, hotel_id)
VALUES
(1, 1, '3', 100.00, 'AVAILABLE', 'SINGLE', 1),
(2, 1, '101', 90.00, 'AVAILABLE', 'SINGLE', 2),
(3, 2, '102', 130.00, 'AVAILABLE', 'DOUBLE', 2),
(4, 2, '201', 110.00, 'AVAILABLE', 'DOUBLE', 3),
(5, 3, '202', 180.00, 'AVAILABLE', 'SUITE', 3),
(6, 1, '301', 80.00, 'AVAILABLE', 'SINGLE', 4),
(7, 2, '302', 120.00, 'AVAILABLE', 'DOUBLE', 4)
ON CONFLICT (id) DO NOTHING;

SELECT pg_catalog.setval('public.hotels_id_seq', 4, true);
SELECT pg_catalog.setval('public.rooms_id_seq', 7, true);