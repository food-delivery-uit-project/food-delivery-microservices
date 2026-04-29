import Fastify from 'fastify';
import { KafkaConsumer } from './kafka/consumer';
import { SSEManager } from './sse/manager';
import { config } from './config';
import { registerSSERoutes } from './sse/handler';

const isProduction = config.nodeEnv === 'production';

const app = Fastify({
  logger: isProduction
    ? true // JSON output in production
    : {
        transport: {
          target: 'pino-pretty',
        },
      },
});

const sseManager = new SSEManager();

// Health check endpoints
app.get('/health/live', async () => ({ status: 'UP' }));
app.get('/health/ready', async () => ({ status: 'READY' }));

// SSE routes
registerSSERoutes(app, sseManager);

// Start Kafka consumer
const kafkaConsumer = new KafkaConsumer(config.kafkaBrokers, sseManager, config.kafkaGroupId);

const start = async () => {
  try {
    // Attempt to connect Kafka consumer (best-effort; service runs without it)
    try {
      await kafkaConsumer.connect();
      app.log.info('Kafka consumer connected');
    } catch (kafkaErr) {
      app.log.warn({ err: kafkaErr }, 'Kafka connection failed, service will run without event consumption');
    }

    await app.listen({ port: config.port, host: '0.0.0.0' });
    app.log.info(`Notification service listening on port ${config.port}`);
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
};

// Graceful shutdown
const shutdown = async () => {
  app.log.info('Shutting down...');
  try {
    await kafkaConsumer.disconnect();
  } catch (_err) {
    // Ignore disconnect errors during shutdown
  }
  await app.close();
  process.exit(0);
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

start();
