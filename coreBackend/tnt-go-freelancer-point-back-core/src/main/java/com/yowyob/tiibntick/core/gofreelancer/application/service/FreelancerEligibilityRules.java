package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerEligibilityDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase.Parcel;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;

/**
 * The matching rules applied to a stored eligibility. Pure functions, no I/O.
 *
 * <p>Three hard constraints, each skipped when the freelancer left it unset:
 * <ul>
 *   <li><b>accepted packages</b> — a fragile parcel needs FRAGILE, a perishable one PERISHABLE, a plain
 *       one STANDARD or DOCUMENT;</li>
 *   <li><b>weight range</b> — only applied when the parcel has a known (&gt; 0) weight;</li>
 *   <li><b>working days</b> — only applied when the need has a pickup deadline, and only at day level:
 *       the deadline is a latest-pickup instant, not a start time, so the hours window is shown to the
 *       client but not enforced (it would wrongly exclude a freelancer who can pick up earlier that day).</li>
 * </ul>
 */
public final class FreelancerEligibilityRules {

    private FreelancerEligibilityRules() { }

    public static boolean accepts(FreelancerEligibilityDTO e, Parcel p) {
        return acceptsPackage(e.getAcceptedPackages(), p)
                && acceptsWeight(e, p.weightKg())
                && worksOn(e.getAvailability(), p.day());
    }

    static boolean acceptsPackage(List<String> accepted, Parcel p) {
        if (accepted == null || accepted.isEmpty()) return true;
        List<String> codes = accepted.stream().map(c -> c.toUpperCase(Locale.ROOT)).toList();
        if (p.fragile() && !codes.contains("FRAGILE")) return false;
        if (p.perishable() && !codes.contains("PERISHABLE")) return false;
        if (!p.fragile() && !p.perishable()) {
            return codes.contains("STANDARD") || codes.contains("DOCUMENT");
        }
        return true;
    }

    static boolean acceptsWeight(FreelancerEligibilityDTO e, double weightKg) {
        if (weightKg <= 0) return true;
        if (e.getMinWeightKg() != null && weightKg < e.getMinWeightKg()) return false;
        return e.getMaxWeightKg() == null || weightKg <= e.getMaxWeightKg();
    }

    static boolean worksOn(List<FreelancerEligibilityDTO.Slot> slots, DayOfWeek day) {
        if (slots == null || slots.isEmpty() || day == null) return true;
        return slots.stream().anyMatch(s -> s.isActive() && day.name().equals(s.getDay()));
    }
}
