package com.yowyob.tiibntick.core.marketback.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ============================================================
// TiiBnTick Market — Domain Value Objects
// All VOs are immutable Java records.
// Author: MANFOUO Braun
// ============================================================

// -----------------------------------------------
// VitrineProfile — provider public presentation
// -----------------------------------------------
public record VitrineProfile(
        String displayName,
        String tagline,
        String description,
        String logoKey,
        String bannerKey,
        String contactEmail,
        String contactPhone,
        String websiteUrl,
        Map<String, String> socialLinks,
        List<String> certificationIds,
        Integer foundedYear
) {
    /** Returns true if all mandatory fields are filled. */
    public boolean isComplete() {
        return displayName != null && !displayName.isBlank()
                && description != null && !description.isBlank()
                && contactPhone != null && !contactPhone.isBlank();
    }

    public VitrineProfile withLogo(String key) {
        return new VitrineProfile(displayName, tagline, description, key, bannerKey,
                contactEmail, contactPhone, websiteUrl, socialLinks, certificationIds, foundedYear);
    }

    public VitrineProfile withBanner(String key) {
        return new VitrineProfile(displayName, tagline, description, logoKey, key,
                contactEmail, contactPhone, websiteUrl, socialLinks, certificationIds, foundedYear);
    }
}
