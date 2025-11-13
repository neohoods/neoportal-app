import { AccessCode, PriceBreakdown, Reservation, ReservationFeedback, ReservationStatus, Space } from '../api-client';

export interface UIReservation {
    id: string;
    spaceId: string;
    userId: string;
    unitId?: string;
    startDate: string;
    endDate: string;
    status: ReservationStatus;
    totalPrice: number;
    currency: string;
    cleaningFee: number;
    deposit: number;
    platformFeeAmount?: number;
    platformFixedFeeAmount?: number;
    priceBreakdown?: PriceBreakdown;
    stripeSessionId?: string;
    stripePaymentIntentId?: string;
    accessCode?: AccessCode;
    feedback?: ReservationFeedback;
    space?: Space;
    paymentExpiresAt?: string;
    createdAt: string;
    updatedAt: string;
}

export function fromApiReservation(apiReservation: Reservation): UIReservation {
    return {
        id: apiReservation.id || '',
        spaceId: apiReservation.spaceId || '',
        userId: apiReservation.userId || '',
        unitId: apiReservation.unitId,
        startDate: apiReservation.startDate || '',
        endDate: apiReservation.endDate || '',
        status: apiReservation.status || 'PENDING_PAYMENT',
        totalPrice: apiReservation.totalPrice || 0,
        currency: apiReservation.currency || 'EUR',
        cleaningFee: apiReservation.cleaningFee || 0,
        deposit: apiReservation.deposit || 0,
        platformFeeAmount: apiReservation.platformFeeAmount,
        platformFixedFeeAmount: apiReservation.platformFixedFeeAmount,
        priceBreakdown: apiReservation.priceBreakdown,
        stripeSessionId: apiReservation.stripeSessionId,
        stripePaymentIntentId: apiReservation.stripePaymentIntentId,
        accessCode: apiReservation.accessCode,
        feedback: apiReservation.feedback,
        space: apiReservation.space,
        paymentExpiresAt: apiReservation.paymentExpiresAt,
        createdAt: apiReservation.createdAt || new Date().toISOString(),
        updatedAt: apiReservation.updatedAt || new Date().toISOString()
    };
}

export function toApiReservation(uiReservation: Partial<UIReservation>): Partial<Reservation> {
    return {
        id: uiReservation.id,
        spaceId: uiReservation.spaceId,
        userId: uiReservation.userId,
        startDate: uiReservation.startDate,
        endDate: uiReservation.endDate,
        status: uiReservation.status,
        totalPrice: uiReservation.totalPrice,
        currency: uiReservation.currency,
        cleaningFee: uiReservation.cleaningFee,
        deposit: uiReservation.deposit,
        platformFeeAmount: uiReservation.platformFeeAmount,
        platformFixedFeeAmount: uiReservation.platformFixedFeeAmount,
        stripeSessionId: uiReservation.stripeSessionId,
        stripePaymentIntentId: uiReservation.stripePaymentIntentId,
        accessCode: uiReservation.accessCode,
        feedback: uiReservation.feedback,
        space: uiReservation.space,
        paymentExpiresAt: uiReservation.paymentExpiresAt,
        createdAt: uiReservation.createdAt,
        updatedAt: uiReservation.updatedAt
    };
}

