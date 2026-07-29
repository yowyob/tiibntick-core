-- Liquibase formatted sql
-- changeset TiiBnTickTeam:060-create-visuals-and-docs

CREATE TABLE gofreelancer_vehicles (
    id UUID PRIMARY KEY,
    freelancer_id UUID,
    core_vehicle_id UUID,
    front_photo_url VARCHAR(255),
    back_photo_url VARCHAR(255),
    color_hex VARCHAR(50),
    trunk_length DOUBLE PRECISION,
    trunk_width DOUBLE PRECISION,
    trunk_height DOUBLE PRECISION,
    trunk_dimension_unit VARCHAR(10)
);

CREATE TABLE gofreelancer_onboarding_docs (
    id UUID PRIMARY KEY,
    freelancer_id UUID,
    cni_recto_url VARCHAR(255),
    cni_verso_url VARCHAR(255),
    photo_card_url VARCHAR(255),
    commercial_register_url VARCHAR(255),
    nui_photo_url VARCHAR(255)
);

CREATE TABLE gofreelancer_packet_proofs (
    id UUID PRIMARY KEY,
    core_packet_id UUID,
    cover_image_url VARCHAR(255)
);

CREATE TABLE gofreelancer_relay_point_visuals (
    id UUID PRIMARY KEY,
    core_relay_point_id UUID,
    storefront_photo_url VARCHAR(255),
    shop_photo_url VARCHAR(255),
    absence_message VARCHAR(500)
);

CREATE TABLE gofreelancer_user_bookmarks (
    id UUID PRIMARY KEY,
    user_id UUID,
    core_announcement_id UUID,
    ui_display_status VARCHAR(100),
    is_bookmarked BOOLEAN
);

CREATE TABLE gofreelancer_visual_profiles (
    id UUID PRIMARY KEY,
    core_actor_id UUID,
    avatar_url VARCHAR(255),
    bio VARCHAR(1000),
    theme VARCHAR(50),
    favorite_color_badge VARCHAR(50),
    last_login_ip VARCHAR(50)
);
