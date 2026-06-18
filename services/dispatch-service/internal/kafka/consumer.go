package kafka

import (
	"context"
	"encoding/json"
	"log/slog"

	kafkago "github.com/segmentio/kafka-go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
)

const restaurantEventsTopic = "restaurant-events"

// DispatchHandler is the interface that the Kafka consumer calls
// when an OrderReadyForPickup event is received.
type DispatchHandler interface {
	HandleDispatchWithRetry(ctx context.Context, orderID string, restaurantLat, restaurantLng float64)
}

// Consumer listens to the restaurant-events topic for OrderReadyForPickup events.
type Consumer struct {
	reader  *kafkago.Reader
	handler DispatchHandler
}

// NewConsumer creates a new Kafka consumer for the restaurant-events topic.
func NewConsumer(brokers []string, groupID string, handler DispatchHandler) *Consumer {
	r := kafkago.NewReader(kafkago.ReaderConfig{
		Brokers:  brokers,
		Topic:    restaurantEventsTopic,
		GroupID:  groupID,
		MinBytes: 1,
		MaxBytes: 10e6, // 10MB
	})
	slog.Info("Kafka consumer initialized", "topic", restaurantEventsTopic, "group", groupID)
	return &Consumer{reader: r, handler: handler}
}

// Start begins consuming messages. This method blocks and should be run in a goroutine.
func (c *Consumer) Start(ctx context.Context) {
	slog.Info("Kafka consumer started, listening for events")
	for {
		msg, err := c.reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				slog.Info("Kafka consumer stopping (context cancelled)")
				return
			}
			slog.Error("Failed to read Kafka message", "error", err)
			continue
		}

		c.handleMessage(ctx, msg)
	}
}

// handleMessage parses the CloudEvent and dispatches to the appropriate handler.
func (c *Consumer) handleMessage(ctx context.Context, msg kafkago.Message) {
	var event CloudEvent
	if err := json.Unmarshal(msg.Value, &event); err != nil {
		slog.Error("Failed to unmarshal event", "error", err, "offset", msg.Offset)
		return
	}

	// Extract OpenTelemetry context from Kafka headers
	carrier := propagation.MapCarrier{}
	for _, h := range msg.Headers {
		carrier[h.Key] = string(h.Value)
	}
	ctx = otel.GetTextMapPropagator().Extract(ctx, carrier)

	slog.Info("Received event", "type", event.Type, "event_id", event.ID)

	switch event.Type {
	case "OrderReadyForPickup":
		c.handleOrderReadyForPickup(ctx, event)
	default:
		slog.Debug("Ignoring unhandled event type", "type", event.Type)
	}
}

// handleOrderReadyForPickup extracts the payload and triggers dispatch logic.
func (c *Consumer) handleOrderReadyForPickup(ctx context.Context, event CloudEvent) {
	dataBytes, err := json.Marshal(event.Data)
	if err != nil {
		slog.Error("Failed to marshal event data", "error", err)
		return
	}

	var data OrderReadyForPickupData
	if err := json.Unmarshal(dataBytes, &data); err != nil {
		slog.Error("Failed to unmarshal OrderReadyForPickupData", "error", err)
		return
	}

	if data.OrderID == "" {
		slog.Warn("OrderReadyForPickup event missing order_id", "event_id", event.ID)
		return
	}

	slog.Info("Processing OrderReadyForPickup",
		"order_id", data.OrderID,
		"restaurant_id", data.RestaurantID,
		"lat", data.RestaurantLat,
		"lng", data.RestaurantLng,
	)

	// Trigger dispatch in a separate goroutine since retry loop is blocking.
	go c.handler.HandleDispatchWithRetry(ctx, data.OrderID, data.RestaurantLat, data.RestaurantLng)
}

// Close closes the Kafka reader.
func (c *Consumer) Close() error {
	return c.reader.Close()
}
