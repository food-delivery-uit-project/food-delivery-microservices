package kafka

import (
	"context"
	"encoding/json"
	"log/slog"

	kafkago "github.com/segmentio/kafka-go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
)

const deliveryEventsTopic = "delivery-events"

// Producer publishes CloudEvents to the delivery-events Kafka topic.
type Producer struct {
	writer *kafkago.Writer
}

// NewProducer creates a new Kafka producer for the delivery-events topic.
func NewProducer(brokers []string) *Producer {
	w := &kafkago.Writer{
		Addr:         kafkago.TCP(brokers...),
		Topic:        deliveryEventsTopic,
		Balancer:     &kafkago.LeastBytes{},
		RequiredAcks: kafkago.RequireOne,
	}
	slog.Info("Kafka producer initialized", "topic", deliveryEventsTopic, "brokers", brokers)
	return &Producer{writer: w}
}

// PublishDriverAssigned publishes a DriverAssigned event.
func (p *Producer) PublishDriverAssigned(ctx context.Context, data DriverAssignedData) error {
	return p.publish(ctx, data.OrderID, "DriverAssigned", data)
}

// PublishDriverPickedUp publishes a DriverPickedUp event.
func (p *Producer) PublishDriverPickedUp(ctx context.Context, data DriverPickedUpData) error {
	return p.publish(ctx, data.OrderID, "DriverPickedUp", data)
}

// PublishOrderDelivered publishes an OrderDelivered event.
func (p *Producer) PublishOrderDelivered(ctx context.Context, data OrderDeliveredData) error {
	return p.publish(ctx, data.OrderID, "OrderDelivered", data)
}

// PublishDispatchFailed publishes a DispatchFailed event.
func (p *Producer) PublishDispatchFailed(ctx context.Context, data DispatchFailedData) error {
	return p.publish(ctx, data.OrderID, "DispatchFailed", data)
}

// publish wraps the data in a CloudEvent envelope and writes to Kafka.
func (p *Producer) publish(ctx context.Context, key, eventType string, data interface{}) error {
	event := NewCloudEvent(eventType, data)
	payload, err := json.Marshal(event)
	if err != nil {
		return err
	}

	msg := kafkago.Message{
		Key:   []byte(key),
		Value: payload,
	}

	// Inject OpenTelemetry context into Kafka headers
	carrier := propagation.MapCarrier{}
	otel.GetTextMapPropagator().Inject(ctx, carrier)
	for k, v := range carrier {
		msg.Headers = append(msg.Headers, kafkago.Header{Key: k, Value: []byte(v)})
	}

	if err := p.writer.WriteMessages(ctx, msg); err != nil {
		slog.Error("Failed to publish event", "type", eventType, "key", key, "error", err)
		return err
	}

	slog.Info("Event published", "type", eventType, "key", key, "event_id", event.ID)
	return nil
}

// Close closes the Kafka writer.
func (p *Producer) Close() error {
	return p.writer.Close()
}
