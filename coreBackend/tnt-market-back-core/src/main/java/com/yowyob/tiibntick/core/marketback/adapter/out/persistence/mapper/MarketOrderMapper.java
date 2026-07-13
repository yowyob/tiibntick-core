package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MarketOrderEntity;
import com.yowyob.tiibntick.core.marketback.domain.model.Address;
import com.yowyob.tiibntick.core.marketback.domain.model.DeliveryRequest;
import com.yowyob.tiibntick.core.marketback.domain.model.DeliveryUrgency;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrder;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrderId;
import com.yowyob.tiibntick.core.marketback.domain.model.Money;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderPricing;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.ParcelSpec;
import com.yowyob.tiibntick.core.marketback.domain.model.PaymentInfo;
import com.yowyob.tiibntick.core.marketback.domain.model.PaymentMethod;
import com.yowyob.tiibntick.core.marketback.domain.model.QuoteRequestId;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceOfferId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity &lt;-&gt; domain mapping for {@link MarketOrder}, mirroring the original
 * standalone app's {@code MarketOrderMapper} but flattening the full
 * {@link Address} value object (district/postalCode/landmark included).
 *
 * @author MANFOUO Braun
 */
@Component
public class MarketOrderMapper {

    public MarketOrderEntity toEntity(MarketOrder o) {
        OrderPricing pricing = o.getPricing();
        PaymentInfo payment = o.getPaymentInfo();
        DeliveryRequest dr = o.getDeliveryRequest();

        MarketOrderEntity.MarketOrderEntityBuilder b = MarketOrderEntity.builder()
                .id(o.getId().value())
                .tenantId(o.getTenantId())
                .clientId(o.getClientId())
                .providerId(o.getProviderId())
                .listingId(o.getListingId() != null ? o.getListingId().value() : null)
                .offerId(o.getOfferId() != null ? o.getOfferId().value() : null)
                .quoteRequestId(o.getQuoteRequestId() != null ? o.getQuoteRequestId().value() : null)
                .status(o.getStatus().name())
                .missionId(o.getMissionId())
                .invoiceId(o.getInvoiceId())
                .cancellationReason(o.getCancellationReason())
                .cancelledAt(o.getCancelledAt())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt());

        if (pricing != null) {
            b.baseAmount(amountOf(pricing.baseAmount()))
                    .distanceFee(amountOf(pricing.distanceFee()))
                    .weightFee(amountOf(pricing.weightFee()))
                    .insuranceFee(amountOf(pricing.insuranceFee()))
                    .expressFee(amountOf(pricing.expressFee()))
                    .discountAmount(amountOf(pricing.discount()))
                    .totalAmount(amountOf(pricing.total()))
                    .currency(pricing.currency());
        }

        if (payment != null) {
            b.paymentMethod(payment.paymentMethod() != null ? payment.paymentMethod().name() : null)
                    .transactionRef(payment.transactionRef())
                    .paidAt(payment.paidAt())
                    .paidAmount(amountOf(payment.paidAmount()))
                    .mobileMoneyPhone(payment.mobileMoneyPhone());
        }

        if (dr != null) {
            b.urgency(dr.urgency() != null ? dr.urgency().name() : null)
                    .desiredPickupAt(dr.desiredPickupAt())
                    .desiredDeliveryAt(dr.desiredDeliveryAt())
                    .specialInstructions(dr.specialInstructions());

            Address pickup = dr.pickupAddress();
            if (pickup != null) {
                b.pickupStreet(pickup.street())
                        .pickupDistrict(pickup.district())
                        .pickupCity(pickup.city())
                        .pickupCountry(pickup.country())
                        .pickupPostalCode(pickup.postalCode())
                        .pickupLat(pickup.lat())
                        .pickupLng(pickup.lng())
                        .pickupLandmark(pickup.landmark());
            }

            Address delivery = dr.deliveryAddress();
            if (delivery != null) {
                b.deliveryStreet(delivery.street())
                        .deliveryDistrict(delivery.district())
                        .deliveryCity(delivery.city())
                        .deliveryCountry(delivery.country())
                        .deliveryPostalCode(delivery.postalCode())
                        .deliveryLat(delivery.lat())
                        .deliveryLng(delivery.lng())
                        .deliveryLandmark(delivery.landmark());
            }

            ParcelSpec parcel = dr.parcelSpec();
            if (parcel != null) {
                b.parcelDescription(parcel.description())
                        .weightKg(parcel.weightKg())
                        .lengthCm(parcel.lengthCm())
                        .widthCm(parcel.widthCm())
                        .heightCm(parcel.heightCm())
                        .valueXaf(parcel.valueXaf())
                        .fragile(parcel.fragile())
                        .perishable(parcel.perishable())
                        .requiresInsurance(parcel.requiresInsurance())
                        .quantity(parcel.quantity());
            }
        }

        return b.build();
    }

    public MarketOrder toDomain(MarketOrderEntity e) {
        String currency = e.getCurrency() != null ? e.getCurrency() : "XAF";
        Money base = moneyOf(e.getBaseAmount(), currency);
        Money distanceFee = moneyOf(e.getDistanceFee(), currency);
        Money weightFee = moneyOf(e.getWeightFee(), currency);
        Money insuranceFee = moneyOf(e.getInsuranceFee(), currency);
        Money expressFee = moneyOf(e.getExpressFee(), currency);
        Money discount = moneyOf(e.getDiscountAmount(), currency);
        Money total = moneyOf(e.getTotalAmount(), currency);

        Map<String, Money> breakdown = new HashMap<>();
        breakdown.put("base", base);
        breakdown.put("distance", distanceFee);
        breakdown.put("weight", weightFee);
        breakdown.put("insurance", insuranceFee);
        breakdown.put("express", expressFee);
        OrderPricing pricing = new OrderPricing(base, distanceFee, weightFee, insuranceFee, expressFee,
                discount, total, currency, breakdown);

        PaymentInfo payment = null;
        if (e.getTransactionRef() != null) {
            Money paidAmount = e.getPaidAmount() != null ? new Money(e.getPaidAmount(), currency) : null;
            payment = new PaymentInfo(
                    e.getPaymentMethod() != null ? PaymentMethod.valueOf(e.getPaymentMethod()) : null,
                    e.getTransactionRef(), e.getPaidAt(), paidAmount, e.getMobileMoneyPhone());
        }

        Address pickup = new Address(e.getPickupStreet(), e.getPickupDistrict(), e.getPickupCity(),
                e.getPickupCountry(), e.getPickupPostalCode(), e.getPickupLat(), e.getPickupLng(),
                e.getPickupLandmark());
        Address delivery = new Address(e.getDeliveryStreet(), e.getDeliveryDistrict(), e.getDeliveryCity(),
                e.getDeliveryCountry(), e.getDeliveryPostalCode(), e.getDeliveryLat(), e.getDeliveryLng(),
                e.getDeliveryLandmark());
        ParcelSpec parcel = new ParcelSpec(e.getParcelDescription(),
                e.getWeightKg() != null ? e.getWeightKg() : 0,
                e.getLengthCm() != null ? e.getLengthCm() : 0,
                e.getWidthCm() != null ? e.getWidthCm() : 0,
                e.getHeightCm() != null ? e.getHeightCm() : 0,
                e.getValueXaf() != null ? e.getValueXaf() : 0,
                e.isFragile(), e.isPerishable(), e.isRequiresInsurance(), e.getQuantity());
        DeliveryRequest dr = new DeliveryRequest(pickup, delivery, parcel,
                e.getDesiredPickupAt(), e.getDesiredDeliveryAt(),
                e.getUrgency() != null ? DeliveryUrgency.valueOf(e.getUrgency()) : null,
                e.getSpecialInstructions());

        return MarketOrder.reconstitute(
                MarketOrderId.of(e.getId()), e.getTenantId(), e.getClientId(), e.getProviderId(),
                e.getListingId() != null ? MarketListingId.of(e.getListingId()) : null,
                e.getOfferId() != null ? ServiceOfferId.of(e.getOfferId()) : null,
                e.getQuoteRequestId() != null ? QuoteRequestId.of(e.getQuoteRequestId()) : null,
                OrderStatus.valueOf(e.getStatus()), dr, pricing, payment,
                e.getMissionId() != null ? e.getMissionId().toString() : null,
                e.getInvoiceId(),
                e.getCancellationReason(), e.getCancelledAt(),
                e.getCreatedAt(), e.getUpdatedAt(), null);
    }

    private static Long amountOf(Money money) {
        return money != null ? money.amount() : null;
    }

    private static Money moneyOf(Long amount, String currency) {
        return amount != null ? new Money(amount, currency) : Money.zero(currency);
    }
}
