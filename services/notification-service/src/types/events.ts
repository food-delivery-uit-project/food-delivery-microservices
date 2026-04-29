/**
 * TypeScript interfaces for all Kafka events.
 * Based on the CloudEvents specification defined in docs/api/events-schema.json.
 * This file is the single source of truth for event types in the notification service.
 */

// --- CloudEvents Envelope ---

export interface CloudEvent<T = unknown> {
  id: string;
  source: string;
  type: string;
  time: string;
  datacontenttype: string;
  data: T;
}

// --- Order Events (topic: order-events) ---

export interface OrderCreatedData {
  order_id: string;
  customer_id: string;
  restaurant_id: string;
  total_amount: number;
  currency?: string;
  items: Array<{
    item_id: string;
    name: string;
    quantity: number;
    unit_price: number;
  }>;
  delivery_address: {
    address_line: string;
    lat: number;
    lng: number;
  };
  payment_method?: 'CREDIT_CARD' | 'WALLET' | 'COD';
}

export interface OrderCancelledData {
  order_id: string;
  reason: string;
  cancelled_by: 'CUSTOMER' | 'SYSTEM' | 'RESTAURANT';
}

// --- Payment Events (topic: payment-events) ---

export interface PaymentSuccessData {
  order_id: string;
  payment_id: string;
  amount: number;
  transaction_id?: string;
}

export interface PaymentFailedData {
  order_id: string;
  payment_id: string;
  failure_reason: string;
}

export interface PaymentRefundedData {
  order_id: string;
  payment_id: string;
  refund_amount: number;
}

// --- Restaurant Events (topic: restaurant-events) ---

export interface OrderAcceptedData {
  order_id: string;
  restaurant_id: string;
  estimated_prep_time_minutes?: number;
}

export interface OrderRejectedData {
  order_id: string;
  restaurant_id: string;
  reason: string;
}

export interface OrderReadyForPickupData {
  order_id: string;
  restaurant_id: string;
  restaurant_lat: number;
  restaurant_lng: number;
}

// --- Delivery Events (topic: delivery-events) ---

export interface DriverAssignedData {
  order_id: string;
  driver_id: string;
  driver_name: string;
  driver_phone: string;
  estimated_arrival_minutes?: number;
}

export interface DriverPickedUpData {
  order_id: string;
  driver_id: string;
  picked_up_at?: string;
}

export interface OrderDeliveredData {
  order_id: string;
  driver_id: string;
  delivered_at: string;
}

export interface DispatchFailedData {
  order_id: string;
  reason: string;
  retry_count?: number;
}

// --- Notification Payload (SSE output) ---

export interface NotificationPayload {
  status: string;
  message: string;
  data?: unknown;
}

// --- Event Type Union ---

export type EventType =
  | 'OrderCreated'
  | 'OrderCancelled'
  | 'PaymentSuccess'
  | 'PaymentFailed'
  | 'PaymentRefunded'
  | 'OrderAccepted'
  | 'OrderRejected'
  | 'OrderReadyForPickup'
  | 'DriverAssigned'
  | 'DriverPickedUp'
  | 'OrderDelivered'
  | 'DispatchFailed';
