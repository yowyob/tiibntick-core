package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.ParcelDetails;

/** Test access to the package-private mapping of {@link ParcelPortAdapter}. */
public final class ParcelPortAdapterAccess {

    private ParcelPortAdapterAccess() {
    }

    public static PackageSpecification spec(ParcelDetails details) {
        return ParcelPortAdapter.toSpecification(details);
    }
}
