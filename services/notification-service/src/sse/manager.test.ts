import { SSEManager } from './manager';

describe('SSEManager', () => {
  let manager: SSEManager;

  beforeEach(() => {
    manager = new SSEManager();
  });

  describe('addConnection / removeConnection', () => {
    it('should register a new connection for an order', () => {
      const sendFn = jest.fn();
      manager.addConnection('order-1', sendFn);
      expect(manager.getActiveCount()).toBe(1);
    });

    it('should allow multiple connections for the same order', () => {
      const sendFn1 = jest.fn();
      const sendFn2 = jest.fn();
      manager.addConnection('order-1', sendFn1);
      manager.addConnection('order-1', sendFn2);
      expect(manager.getActiveCount()).toBe(2);
    });

    it('should remove a specific connection', () => {
      const sendFn1 = jest.fn();
      const sendFn2 = jest.fn();
      manager.addConnection('order-1', sendFn1);
      manager.addConnection('order-1', sendFn2);

      manager.removeConnection('order-1', sendFn1);
      expect(manager.getActiveCount()).toBe(1);
    });

    it('should clean up order entry when last connection removed', () => {
      const sendFn = jest.fn();
      manager.addConnection('order-1', sendFn);
      manager.removeConnection('order-1', sendFn);
      expect(manager.getActiveCount()).toBe(0);
    });

    it('should handle removing from non-existent order gracefully', () => {
      const sendFn = jest.fn();
      // Should not throw
      manager.removeConnection('nonexistent', sendFn);
      expect(manager.getActiveCount()).toBe(0);
    });
  });

  describe('send', () => {
    it('should send event to all connections watching an order', () => {
      const sendFn1 = jest.fn();
      const sendFn2 = jest.fn();
      manager.addConnection('order-1', sendFn1);
      manager.addConnection('order-1', sendFn2);

      const event = { status: 'PAID', message: 'Payment completed' };
      manager.send('order-1', event);

      const expectedPayload = JSON.stringify(event);
      expect(sendFn1).toHaveBeenCalledWith(expectedPayload);
      expect(sendFn2).toHaveBeenCalledWith(expectedPayload);
    });

    it('should not send to connections watching different orders', () => {
      const sendFn1 = jest.fn();
      const sendFn2 = jest.fn();
      manager.addConnection('order-1', sendFn1);
      manager.addConnection('order-2', sendFn2);

      manager.send('order-1', { status: 'PAID', message: 'Payment completed' });

      expect(sendFn1).toHaveBeenCalled();
      expect(sendFn2).not.toHaveBeenCalled();
    });

    it('should handle sending to non-existent order gracefully', () => {
      // Should not throw
      manager.send('nonexistent', { status: 'TEST', message: 'test' });
    });
  });

  describe('getActiveCount', () => {
    it('should return 0 when no connections exist', () => {
      expect(manager.getActiveCount()).toBe(0);
    });

    it('should count connections across multiple orders', () => {
      manager.addConnection('order-1', jest.fn());
      manager.addConnection('order-1', jest.fn());
      manager.addConnection('order-2', jest.fn());
      expect(manager.getActiveCount()).toBe(3);
    });

    it('should update after removals', () => {
      const fn1 = jest.fn();
      const fn2 = jest.fn();
      manager.addConnection('order-1', fn1);
      manager.addConnection('order-2', fn2);

      manager.removeConnection('order-1', fn1);
      expect(manager.getActiveCount()).toBe(1);
    });
  });
});
