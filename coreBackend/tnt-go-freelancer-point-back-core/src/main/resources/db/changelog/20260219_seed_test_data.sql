-- Insert test data for delivery persons and clients (idempotent)
-- Only inserts if delivery_persons table is empty (excludes admin seeded data)

DO $$
DECLARE
    v_person1_id UUID := '11111111-1111-1111-1111-111111111101';
    v_person2_id UUID := '11111111-1111-1111-1111-111111111102';
    v_person3_id UUID := '11111111-1111-1111-1111-111111111103';
    v_person4_id UUID := '11111111-1111-1111-1111-111111111104';
    v_person5_id UUID := '11111111-1111-1111-1111-111111111105';
    v_client_person1_id UUID := '22222222-2222-2222-2222-222222222201';
    v_client_person2_id UUID := '22222222-2222-2222-2222-222222222202';
    v_client_person3_id UUID := '22222222-2222-2222-2222-222222222203';
    v_dp1_id UUID := '33333333-3333-3333-3333-333333333301';
    v_dp2_id UUID := '33333333-3333-3333-3333-333333333302';
    v_dp3_id UUID := '33333333-3333-3333-3333-333333333303';
    v_dp4_id UUID := '33333333-3333-3333-3333-333333333304';
    v_dp5_id UUID := '33333333-3333-3333-3333-333333333305';
    v_log1_id UUID := '44444444-4444-4444-4444-444444444401';
    v_log2_id UUID := '44444444-4444-4444-4444-444444444402';
    v_log3_id UUID := '44444444-4444-4444-4444-444444444403';
    v_log4_id UUID := '44444444-4444-4444-4444-444444444404';
    v_log5_id UUID := '44444444-4444-4444-4444-444444444405';
    v_addr1_id UUID := '55555555-5555-5555-5555-555555555501';
    v_addr2_id UUID := '55555555-5555-5555-5555-555555555502';
    v_addr3_id UUID := '55555555-5555-5555-5555-555555555503';
    v_addr4_id UUID := '55555555-5555-5555-5555-555555555504';
    v_addr5_id UUID := '55555555-5555-5555-5555-555555555505';
    v_addr_c1_id UUID := '55555555-5555-5555-5555-555555555506';
    v_addr_c2_id UUID := '55555555-5555-5555-5555-555555555507';
    v_addr_c3_id UUID := '55555555-5555-5555-5555-555555555508';
    v_client1_id UUID := '66666666-6666-6666-6666-666666666601';
    v_client2_id UUID := '66666666-6666-6666-6666-666666666602';
    v_client3_id UUID := '66666666-6666-6666-6666-666666666603';
BEGIN
    -- Only seed if no delivery persons exist yet
    IF NOT EXISTS (SELECT 1 FROM delivery_persons WHERE id = v_dp1_id) THEN

        -- ============ DELIVERY PERSON 1: PENDING, MOTORBIKE ============
        INSERT INTO persons (id, last_name, first_name, phone, email, password, national_id, role, is_active)
        VALUES (v_person1_id, 'Nguemo', 'Jean-Pierre', '+237690112233', 'jean.nguemo@email.cm',
                '$2a$10$dummyhash1234567890123456789012345678901234567890', 'CNI-001-YDE', 'DELIVERY', false);

        INSERT INTO delivery_persons (id, person_id, commercial_register, commercial_name, taxpayer_number, status, is_active, created_at, updated_at)
        VALUES (v_dp1_id, v_person1_id, 'RC/YDE/2025/B/001', 'Express Nguemo', 'M019200045678A', 'PENDING', false, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

        INSERT INTO logistics (id, delivery_person_id, plate_number, logistics_type, logistics_class, brand, model, color, created_at)
        VALUES (v_log1_id, v_dp1_id, 'LT-1234-AB', 'MOTORBIKE', 'STANDARD', 'Honda', 'CG 125', 'Rouge', NOW());

        INSERT INTO addresses (id, street, city, district, type, country, description)
        VALUES (v_addr1_id, 'Rue de la Joie', 'Yaoundé', 'Bastos', 'DOMICILE', 'Cameroun', 'Près du carrefour Bastos');

        INSERT INTO person_addresses (id, person_id, address_id) VALUES (gen_random_uuid(), v_person1_id, v_addr1_id);

        -- ============ DELIVERY PERSON 2: PENDING, CAR ============
        INSERT INTO persons (id, last_name, first_name, phone, email, password, national_id, role, is_active)
        VALUES (v_person2_id, 'Tchatchoua', 'Marie', '+237677889900', 'marie.tchatchoua@email.cm',
                '$2a$10$dummyhash1234567890123456789012345678901234567890', 'CNI-002-DLA', 'DELIVERY', false);

        INSERT INTO delivery_persons (id, person_id, commercial_register, commercial_name, taxpayer_number, status, is_active, created_at, updated_at)
        VALUES (v_dp2_id, v_person2_id, 'RC/DLA/2025/B/002', 'Trans Marie', 'M019200067890B', 'PENDING', false, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days');

        INSERT INTO logistics (id, delivery_person_id, plate_number, logistics_type, logistics_class, brand, model, color, created_at)
        VALUES (v_log2_id, v_dp2_id, 'CE-5678-CD', 'CAR', 'PREMIUM', 'Toyota', 'Corolla', 'Blanc', NOW());

        INSERT INTO addresses (id, street, city, district, type, country, description)
        VALUES (v_addr2_id, 'Boulevard de la Liberté', 'Douala', 'Akwa', 'DOMICILE', 'Cameroun', 'Face au marché Akwa');

        INSERT INTO person_addresses (id, person_id, address_id) VALUES (gen_random_uuid(), v_person2_id, v_addr2_id);

        -- ============ DELIVERY PERSON 3: PENDING, BIKE ============
        INSERT INTO persons (id, last_name, first_name, phone, email, password, national_id, role, is_active)
        VALUES (v_person3_id, 'Fokou', 'Samuel', '+237655667788', 'samuel.fokou@email.cm',
                '$2a$10$dummyhash1234567890123456789012345678901234567890', 'CNI-003-YDE', 'DELIVERY', false);

        INSERT INTO delivery_persons (id, person_id, commercial_register, commercial_name, taxpayer_number, status, is_active, created_at, updated_at)
        VALUES (v_dp3_id, v_person3_id, 'RC/YDE/2025/B/003', 'Livraison Rapide Fokou', 'M019200089012C', 'PENDING', false, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

        INSERT INTO logistics (id, delivery_person_id, plate_number, logistics_type, logistics_class, brand, model, color, created_at)
        VALUES (v_log3_id, v_dp3_id, 'VELO-001', 'BIKE', 'STANDARD', 'Giant', 'Escape 3', 'Noir', NOW());

        INSERT INTO addresses (id, street, city, district, type, country, description)
        VALUES (v_addr3_id, 'Avenue Kennedy', 'Yaoundé', 'Mfandena', 'DOMICILE', 'Cameroun', 'Quartier Mfandena');

        INSERT INTO person_addresses (id, person_id, address_id) VALUES (gen_random_uuid(), v_person3_id, v_addr3_id);

        -- ============ DELIVERY PERSON 4: APPROVED, TRUCK ============
        INSERT INTO persons (id, last_name, first_name, phone, email, password, national_id, role, is_active)
        VALUES (v_person4_id, 'Mbarga', 'Paul', '+237699001122', 'paul.mbarga@email.cm',
                '$2a$10$dummyhash1234567890123456789012345678901234567890', 'CNI-004-DLA', 'DELIVERY', true);

        INSERT INTO delivery_persons (id, person_id, commercial_register, commercial_name, taxpayer_number, status, is_active, created_at, updated_at)
        VALUES (v_dp4_id, v_person4_id, 'RC/DLA/2025/B/004', 'Mbarga Logistics', 'M019200011111D', 'APPROVED', true, NOW() - INTERVAL '30 days', NOW() - INTERVAL '10 days');

        INSERT INTO logistics (id, delivery_person_id, plate_number, logistics_type, logistics_class, brand, model, color, created_at)
        VALUES (v_log4_id, v_dp4_id, 'LT-9012-EF', 'TRUCK', 'PREMIUM', 'Mercedes', 'Atego', 'Bleu', NOW());

        INSERT INTO addresses (id, street, city, district, type, country, description)
        VALUES (v_addr4_id, 'Rue du Port', 'Douala', 'Bassa', 'DOMICILE', 'Cameroun', 'Zone industrielle de Bassa');

        INSERT INTO person_addresses (id, person_id, address_id) VALUES (gen_random_uuid(), v_person4_id, v_addr4_id);

        -- ============ DELIVERY PERSON 5: SUSPENDED, VAN ============
        INSERT INTO persons (id, last_name, first_name, phone, email, password, national_id, role, is_active)
        VALUES (v_person5_id, 'Atangana', 'Éric', '+237688334455', 'eric.atangana@email.cm',
                '$2a$10$dummyhash1234567890123456789012345678901234567890', 'CNI-005-YDE', 'DELIVERY', false);

        INSERT INTO delivery_persons (id, person_id, commercial_register, commercial_name, taxpayer_number, status, is_active, created_at, updated_at)
        VALUES (v_dp5_id, v_person5_id, 'RC/YDE/2025/B/005', 'Atangana Express', 'M019200022222E', 'SUSPENDED', false, NOW() - INTERVAL '60 days', NOW() - INTERVAL '5 days');

        INSERT INTO logistics (id, delivery_person_id, plate_number, logistics_type, logistics_class, brand, model, color, created_at)
        VALUES (v_log5_id, v_dp5_id, 'CE-3456-GH', 'VAN', 'STANDARD', 'Renault', 'Kangoo', 'Gris', NOW());

        INSERT INTO addresses (id, street, city, district, type, country, description)
        VALUES (v_addr5_id, 'Rue du Marché', 'Yaoundé', 'Mokolo', 'DOMICILE', 'Cameroun', 'Derrière le marché Mokolo');

        INSERT INTO person_addresses (id, person_id, address_id) VALUES (gen_random_uuid(), v_person5_id, v_addr5_id);

        -- ============ CLIENT 1 ============
        INSERT INTO persons (id, last_name, first_name, phone, email, password, national_id, role, is_active)
        VALUES (v_client_person1_id, 'Nkoulou', 'Diane', '+237677112233', 'diane.nkoulou@email.cm',
                '$2a$10$dummyhash1234567890123456789012345678901234567890', 'CNI-C01-YDE', 'CLIENT', true);

        INSERT INTO clients (id, person_id, loyalty_status, status)
        VALUES (v_client1_id, v_client_person1_id, 'GOLD', 'ACTIVE');

        INSERT INTO addresses (id, street, city, district, type, country, description)
        VALUES (v_addr_c1_id, 'Avenue Foch', 'Yaoundé', 'Centre-Ville', 'DOMICILE', 'Cameroun', 'Immeuble Rose, 3e étage');

        INSERT INTO person_addresses (id, person_id, address_id) VALUES (gen_random_uuid(), v_client_person1_id, v_addr_c1_id);

        -- ============ CLIENT 2 ============
        INSERT INTO persons (id, last_name, first_name, phone, email, password, national_id, role, is_active)
        VALUES (v_client_person2_id, 'Tamo', 'François', '+237699556677', 'francois.tamo@email.cm',
                '$2a$10$dummyhash1234567890123456789012345678901234567890', 'CNI-C02-DLA', 'CLIENT', true);

        INSERT INTO clients (id, person_id, loyalty_status, status)
        VALUES (v_client2_id, v_client_person2_id, 'SILVER', 'ACTIVE');

        INSERT INTO addresses (id, street, city, district, type, country, description)
        VALUES (v_addr_c2_id, 'Rue de la Paix', 'Douala', 'Bonapriso', 'DOMICILE', 'Cameroun', 'Villa Tamo');

        INSERT INTO person_addresses (id, person_id, address_id) VALUES (gen_random_uuid(), v_client_person2_id, v_addr_c2_id);

        -- ============ CLIENT 3 ============
        INSERT INTO persons (id, last_name, first_name, phone, email, password, national_id, role, is_active)
        VALUES (v_client_person3_id, 'Etoundi', 'Sylvie', '+237688998877', 'sylvie.etoundi@email.cm',
                '$2a$10$dummyhash1234567890123456789012345678901234567890', 'CNI-C03-YDE', 'CLIENT', true);

        INSERT INTO clients (id, person_id, loyalty_status, status)
        VALUES (v_client3_id, v_client_person3_id, 'BRONZE', 'ACTIVE');

        INSERT INTO addresses (id, street, city, district, type, country, description)
        VALUES (v_addr_c3_id, 'Boulevard du 20 Mai', 'Yaoundé', 'Nlongkak', 'DOMICILE', 'Cameroun', 'Résidence Nlongkak');

        INSERT INTO person_addresses (id, person_id, address_id) VALUES (gen_random_uuid(), v_client_person3_id, v_addr_c3_id);

        RAISE NOTICE 'Test data seeded: 5 delivery persons, 3 clients';
    ELSE
        RAISE NOTICE 'Test data already exists, skipping seed';
    END IF;
END $$;
