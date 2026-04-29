import { mapEventToNotification } from './consumer';

describe('mapEventToNotification', () => {
  it('should map OrderCreated to CREATED status', () => {
    const result = mapEventToNotification('OrderCreated', { order_id: 'ord-1' });
    expect(result).toEqual({ status: 'CREATED', message: 'Order has been created' });
  });

  it('should map PaymentSuccess to PAID status', () => {
    const result = mapEventToNotification('PaymentSuccess', { order_id: 'ord-1' });
    expect(result).toEqual({ status: 'PAID', message: 'Payment completed successfully' });
  });

  it('should map PaymentFailed with data payload', () => {
    const data = { order_id: 'ord-1', failure_reason: 'Insufficient funds' };
    const result = mapEventToNotification('PaymentFailed', data);
    expect(result).toEqual({
      status: 'PAYMENT_FAILED',
      message: 'Payment failed',
      data,
    });
  });

  it('should map PaymentRefunded to REFUNDED status', () => {
    const result = mapEventToNotification('PaymentRefunded', { order_id: 'ord-1' });
    expect(result).toEqual({ status: 'REFUNDED', message: 'Payment has been refunded' });
  });

  it('should map OrderAccepted to ACCEPTED status', () => {
    const result = mapEventToNotification('OrderAccepted', { order_id: 'ord-1' });
    expect(result).toEqual({ status: 'ACCEPTED', message: 'Restaurant accepted the order' });
  });

  it('should map OrderRejected with reason data', () => {
    const data = { order_id: 'ord-1', reason: 'Out of stock' };
    const result = mapEventToNotification('OrderRejected', data);
    expect(result).toEqual({
      status: 'REJECTED',
      message: 'Restaurant rejected the order',
      data,
    });
  });

  it('should map OrderReadyForPickup to READY status', () => {
    const result = mapEventToNotification('OrderReadyForPickup', { order_id: 'ord-1' });
    expect(result).toEqual({ status: 'READY', message: 'Order is ready for pickup' });
  });

  it('should map DriverAssigned with driver name in message', () => {
    const data = { order_id: 'ord-1', driver_id: 'd-1', driver_name: 'Nguyen Van A' };
    const result = mapEventToNotification('DriverAssigned', data);
    expect(result).toEqual({
      status: 'DRIVER_ASSIGNED',
      message: 'Driver Nguyen Van A is on the way',
      data,
    });
  });

  it('should map DriverPickedUp to PICKED_UP status', () => {
    const result = mapEventToNotification('DriverPickedUp', { order_id: 'ord-1' });
    expect(result).toEqual({ status: 'PICKED_UP', message: 'Driver picked up the order' });
  });

  it('should map OrderDelivered to DELIVERED status', () => {
    const result = mapEventToNotification('OrderDelivered', { order_id: 'ord-1' });
    expect(result).toEqual({ status: 'DELIVERED', message: 'Order delivered successfully' });
  });

  it('should map OrderCancelled with cancellation data', () => {
    const data = { order_id: 'ord-1', reason: 'Customer request', cancelled_by: 'CUSTOMER' };
    const result = mapEventToNotification('OrderCancelled', data);
    expect(result).toEqual({
      status: 'CANCELLED',
      message: 'Order has been cancelled',
      data,
    });
  });

  it('should map DispatchFailed to DISPATCH_FAILED status', () => {
    const data = { order_id: 'ord-1', reason: 'No driver available', retry_count: 5 };
    const result = mapEventToNotification('DispatchFailed', data);
    expect(result).toEqual({
      status: 'DISPATCH_FAILED',
      message: 'No driver available at the moment',
      data,
    });
  });

  it('should return null for unknown event types', () => {
    const result = mapEventToNotification('UnknownEvent', { order_id: 'ord-1' });
    expect(result).toBeNull();
  });

  it('should return null for empty string event type', () => {
    const result = mapEventToNotification('', {});
    expect(result).toBeNull();
  });
});
