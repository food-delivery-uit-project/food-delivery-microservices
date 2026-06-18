import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { SSEManager } from './manager';

/**
 * Register SSE routes for real-time order tracking.
 *
 * Client connects to: GET /api/v1/notifications/orders/{orderId}/stream
 * Server sends events as the order progresses through its lifecycle.
 */
export function registerSSERoutes(app: FastifyInstance, sseManager: SSEManager): void {
  app.get<{ Params: { orderId: string } }>(
    '/api/v1/notifications/orders/:orderId/stream',
    {
      schema: {
        description: 'Subscribe to Server-Sent Events for order updates',
        tags: ['Notifications'],
        params: {
          type: 'object',
          properties: {
            orderId: { type: 'string', description: 'UUID of the order' }
          }
        },
        response: {
          200: {
            description: 'SSE stream connection established',
            type: 'string'
          }
        }
      }
    },
    async (request: FastifyRequest<{ Params: { orderId: string } }>, reply: FastifyReply) => {
      const { orderId } = request.params;

      // Set SSE headers
      reply.raw.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no', // Disable nginx buffering
      });

      // Send initial connection event
      reply.raw.write(`data: ${JSON.stringify({ status: 'CONNECTED', message: 'Listening for updates' })}\n\n`);

      // Register SSE send function
      const sendFn = (data: string) => {
        reply.raw.write(`data: ${data}\n\n`);
      };

      sseManager.addConnection(orderId, sendFn);

      // Keep-alive ping every 30 seconds
      const keepAlive = setInterval(() => {
        reply.raw.write(': keep-alive\n\n');
      }, 30000);

      // Cleanup on disconnect
      request.raw.on('close', () => {
        clearInterval(keepAlive);
        sseManager.removeConnection(orderId, sendFn);
        request.log.info({ orderId }, 'SSE client disconnected');
      });
    },
  );

  // Monitoring endpoint: active SSE connections count
  app.get(
    '/api/v1/notifications/stats',
    {
      schema: {
        description: 'Get current SSE connection stats',
        tags: ['System'],
        response: {
          200: {
            type: 'object',
            properties: {
              success: { type: 'boolean' },
              data: {
                type: 'object',
                properties: {
                  active_connections: { type: 'number' }
                }
              }
            }
          }
        }
      }
    },
    async () => ({
      success: true,
      data: {
        active_connections: sseManager.getActiveCount(),
      },
    })
  );
}
