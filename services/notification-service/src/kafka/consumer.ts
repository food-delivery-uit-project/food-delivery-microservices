import { Kafka, Consumer, EachMessagePayload, logLevel } from 'kafkajs';
import { SSEManager } from '../sse/manager';
import { CloudEvent, EventType, NotificationPayload } from '../types/events';

/**
 * Kafka consumer that listens to order/payment/delivery/restaurant events
 * and pushes real-time updates to connected clients via SSE.
 */
export class KafkaConsumer {
  private kafka: Kafka;
  private consumer: Consumer;
  private sseManager: SSEManager;
  private processedEvents: Set<string> = new Set();
  private readonly MAX_PROCESSED_CACHE = 10000;

  constructor(brokers: string[], sseManager: SSEManager, groupId = 'notification-service-group') {
    this.kafka = new Kafka({
      clientId: 'notification-service',
      brokers,
      logLevel: logLevel.WARN,
    });
    this.consumer = this.kafka.consumer({ groupId });
    this.sseManager = sseManager;
  }

  async connect(): Promise<void> {
    await this.consumer.connect();

    // Subscribe to all event topics
    await this.consumer.subscribe({ topic: 'order-events', fromBeginning: false });
    await this.consumer.subscribe({ topic: 'payment-events', fromBeginning: false });
    await this.consumer.subscribe({ topic: 'delivery-events', fromBeginning: false });
    await this.consumer.subscribe({ topic: 'restaurant-events', fromBeginning: false });

    await this.consumer.run({
      eachMessage: async (payload: EachMessagePayload) => {
        await this.handleMessage(payload);
      },
    });
  }

  async disconnect(): Promise<void> {
    await this.consumer.disconnect();
  }

  private async handleMessage({ topic, message }: EachMessagePayload): Promise<void> {
    if (!message.value) return;

    try {
      const event = JSON.parse(message.value.toString()) as CloudEvent;
      const eventType = event.type as EventType;
      const eventId = event.id;
      const orderId = (event.data as Record<string, unknown>)?.order_id as string;

      if (!orderId) {
        return;
      }

      // Idempotency check: skip duplicate events
      if (eventId && this.processedEvents.has(eventId)) {
        return;
      }

      // Map event type to notification message
      const notification = mapEventToNotification(eventType, event.data as Record<string, unknown>);
      if (notification) {
        this.sseManager.send(orderId, notification);
      }

      // Track processed event ID for idempotency
      if (eventId) {
        this.processedEvents.add(eventId);
        // Evict oldest entries if cache grows too large
        if (this.processedEvents.size > this.MAX_PROCESSED_CACHE) {
          const firstKey = this.processedEvents.values().next().value;
          if (firstKey) {
            this.processedEvents.delete(firstKey);
          }
        }
      }
    } catch (_err) {
      // Log parse errors as structured JSON, but don't crash the consumer
      const errMsg = _err instanceof Error ? _err.message : String(_err);
      process.stderr.write(JSON.stringify({ level: 'error', msg: 'Failed to process Kafka message', topic, error: errMsg }) + '\n');
    }
  }
}

/**
 * Maps a Kafka event type to a notification payload for SSE delivery.
 * Returns null for unrecognized event types.
 */
export function mapEventToNotification(
  eventType: string,
  data: Record<string, unknown>,
): NotificationPayload | null {
  switch (eventType) {
    case 'OrderCreated':
      return { status: 'CREATED', message: 'Order has been created' };
    case 'PaymentSuccess':
      return { status: 'PAID', message: 'Payment completed successfully' };
    case 'PaymentFailed':
      return { status: 'PAYMENT_FAILED', message: 'Payment failed', data };
    case 'PaymentRefunded':
      return { status: 'REFUNDED', message: 'Payment has been refunded' };
    case 'OrderAccepted':
      return { status: 'ACCEPTED', message: 'Restaurant accepted the order' };
    case 'OrderRejected':
      return { status: 'REJECTED', message: 'Restaurant rejected the order', data };
    case 'OrderReadyForPickup':
      return { status: 'READY', message: 'Order is ready for pickup' };
    case 'DriverAssigned':
      return {
        status: 'DRIVER_ASSIGNED',
        message: `Driver ${String(data.driver_name ?? 'Unknown')} is on the way`,
        data,
      };
    case 'DriverPickedUp':
      return { status: 'PICKED_UP', message: 'Driver picked up the order' };
    case 'OrderDelivered':
      return { status: 'DELIVERED', message: 'Order delivered successfully' };
    case 'OrderCancelled':
      return { status: 'CANCELLED', message: 'Order has been cancelled', data };
    case 'DispatchFailed':
      return {
        status: 'DISPATCH_FAILED',
        message: 'No driver available at the moment',
        data,
      };
    default:
      return null;
  }
}
