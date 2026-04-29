export const config = {
  port: parseInt(process.env.PORT || '8080', 10),
  nodeEnv: process.env.NODE_ENV || 'development',
  kafkaBrokers: (process.env.KAFKA_BROKERS || 'localhost:9092').split(','),
  kafkaGroupId: process.env.KAFKA_GROUP_ID || 'notification-service-group',
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',
  serviceName: process.env.OTEL_SERVICE_NAME || 'notification-service',
};
