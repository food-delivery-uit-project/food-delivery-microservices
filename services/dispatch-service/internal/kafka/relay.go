package kafka

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/redis/go-redis/v9"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
)

const (
	outboxStreamKey  = "dispatch_outbox_events"
	outboxConsumerGrp = "dispatch_outbox_group"
)

// RedisOutboxRelay reads events from Redis Stream and publishes them to Kafka.
type RedisOutboxRelay struct {
	redisClient *redis.Client
	producer    *Producer
}

// NewRedisOutboxRelay creates a new relay.
func NewRedisOutboxRelay(client *redis.Client, producer *Producer) *RedisOutboxRelay {
	return &RedisOutboxRelay{
		redisClient: client,
		producer:    producer,
	}
}

// Start begins polling the Redis stream.
func (r *RedisOutboxRelay) Start(ctx context.Context) {
	// Create consumer group if it doesn't exist
	err := r.redisClient.XGroupCreateMkStream(ctx, outboxStreamKey, outboxConsumerGrp, "0").Err()
	if err != nil && err.Error() != "BUSYGROUP Consumer Group name already exists" {
		slog.Error("Failed to create Redis consumer group", "error", err)
	} else {
		slog.Info("Redis outbox consumer group ready", "stream", outboxStreamKey, "group", outboxConsumerGrp)
	}

	consumerName := "relay-worker-1"

	for {
		select {
		case <-ctx.Done():
			slog.Info("Redis outbox relay stopping")
			return
		default:
			// Read from stream, blocking for 2 seconds
			streams, err := r.redisClient.XReadGroup(ctx, &redis.XReadGroupArgs{
				Group:    outboxConsumerGrp,
				Consumer: consumerName,
				Streams:  []string{outboxStreamKey, ">"},
				Count:    10,
				Block:    2 * time.Second,
			}).Result()

			if err == redis.Nil {
				continue // Timeout, no new messages
			} else if err != nil {
				// Don't log expected context cancellations
				if ctx.Err() == nil {
					slog.Error("Error reading from Redis outbox stream", "error", err)
					time.Sleep(1 * time.Second)
				}
				continue
			}

			for _, stream := range streams {
				for _, message := range stream.Messages {
					payloadStr, ok := message.Values["payload"].(string)
					if !ok {
						slog.Error("Invalid payload type in outbox stream", "id", message.ID)
						r.redisClient.XAck(ctx, outboxStreamKey, outboxConsumerGrp, message.ID)
						continue
					}

					var eventData DriverAssignedData
					if err := json.Unmarshal([]byte(payloadStr), &eventData); err != nil {
						slog.Error("Failed to unmarshal outbox event", "error", err)
						r.redisClient.XAck(ctx, outboxStreamKey, outboxConsumerGrp, message.ID)
						continue
					}

					// Recreate context with traceparent if available
					publishCtx := ctx
					if traceparent, ok := message.Values["traceparent"].(string); ok && traceparent != "" {
						carrier := propagation.MapCarrier{"traceparent": traceparent}
						publishCtx = otel.GetTextMapPropagator().Extract(ctx, carrier)
					}

					// Publish to Kafka
					if r.producer != nil {
						if err := r.producer.PublishDriverAssigned(publishCtx, eventData); err != nil {
							slog.Error("Failed to publish outbox event to Kafka", "error", err)
							// Do not XACK so we can retry later
							continue
						}
					}

					// Acknowledge the message
					r.redisClient.XAck(ctx, outboxStreamKey, outboxConsumerGrp, message.ID)
					slog.Info("Successfully relayed outbox event to Kafka", "order_id", eventData.OrderID, "msg_id", message.ID)
				}
			}
		}
	}
}
