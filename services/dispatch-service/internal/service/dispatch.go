package service

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/food-delivery/dispatch-service/internal/domain"
	"github.com/food-delivery/dispatch-service/internal/kafka"
	"github.com/food-delivery/dispatch-service/internal/matching"
	"github.com/food-delivery/dispatch-service/internal/repository"
)

// DispatchService orchestrates driver matching and delivery lifecycle.
type DispatchService struct {
	driverRepo    *repository.RedisDriverRepository
	matcher       *matching.Matcher
	producer      *kafka.Producer
	radiusKm      float64
	retryInterval time.Duration
	maxRetries    int
}

// NewDispatchService creates a new DispatchService with all dependencies.
func NewDispatchService(
	repo *repository.RedisDriverRepository,
	matcher *matching.Matcher,
	producer *kafka.Producer,
	radiusKm float64,
	retryInterval time.Duration,
	maxRetries int,
) *DispatchService {
	return &DispatchService{
		driverRepo:    repo,
		matcher:       matcher,
		producer:      producer,
		radiusKm:      radiusKm,
		retryInterval: retryInterval,
		maxRetries:    maxRetries,
	}
}

// GetDeliveryStatus returns the current delivery status for an order.
func (s *DispatchService) GetDeliveryStatus(ctx context.Context, orderID string) (*domain.DeliveryStatus, error) {
	status, err := s.driverRepo.GetDeliveryStatus(ctx, orderID)
	if err != nil {
		return nil, fmt.Errorf("delivery not found for order %s: %w", orderID, err)
	}
	return status, nil
}

// ConfirmPickup marks the order as picked up by the assigned driver.
func (s *DispatchService) ConfirmPickup(ctx context.Context, orderID, driverID string) error {
	slog.Info("Driver confirming pickup", "order_id", orderID, "driver_id", driverID)

	// Validate driver is assigned to this order
	assignedDriver, err := s.driverRepo.GetDispatchAssignment(ctx, orderID)
	if err != nil {
		return fmt.Errorf("no active dispatch for order %s", orderID)
	}
	if assignedDriver != driverID {
		return fmt.Errorf("driver %s is not assigned to order %s", driverID, orderID)
	}

	// Validate current status allows pickup
	delivery, err := s.driverRepo.GetDeliveryStatus(ctx, orderID)
	if err != nil {
		return fmt.Errorf("delivery not found for order %s", orderID)
	}
	if delivery.Status != "DRIVER_ASSIGNED" {
		return fmt.Errorf("order %s is not in DRIVER_ASSIGNED status (current: %s)", orderID, delivery.Status)
	}

	// Update delivery status to PICKED_UP
	now := time.Now().UTC()
	delivery.Status = "PICKED_UP"
	delivery.UpdatedAt = now
	if err := s.driverRepo.SaveDeliveryStatus(ctx, delivery); err != nil {
		return fmt.Errorf("failed to update delivery status: %w", err)
	}

	// Publish DriverPickedUp event
	if s.producer != nil {
		if err := s.producer.PublishDriverPickedUp(ctx, kafka.DriverPickedUpData{
			OrderID:    orderID,
			DriverID:   driverID,
			PickedUpAt: now.Format(time.RFC3339),
		}); err != nil {
			slog.Error("Failed to publish DriverPickedUp event", "error", err)
		}
	}

	return nil
}

// ConfirmDelivery marks the order as delivered and frees up the driver.
func (s *DispatchService) ConfirmDelivery(ctx context.Context, orderID, driverID string) error {
	slog.Info("Driver confirming delivery", "order_id", orderID, "driver_id", driverID)

	// Validate driver is assigned to this order
	assignedDriver, err := s.driverRepo.GetDispatchAssignment(ctx, orderID)
	if err != nil {
		return fmt.Errorf("no active dispatch for order %s", orderID)
	}
	if assignedDriver != driverID {
		return fmt.Errorf("driver %s is not assigned to order %s", driverID, orderID)
	}

	// Validate current status allows delivery confirmation
	now := time.Now().UTC()
	delivery, err := s.driverRepo.GetDeliveryStatus(ctx, orderID)
	if err != nil {
		return fmt.Errorf("delivery not found for order %s", orderID)
	}
	if delivery.Status != "PICKED_UP" && delivery.Status != "DRIVER_ASSIGNED" {
		return fmt.Errorf("cannot confirm delivery for order %s from status %s", orderID, delivery.Status)
	}
	delivery.Status = "DELIVERED"
	delivery.UpdatedAt = now
	if err := s.driverRepo.SaveDeliveryStatus(ctx, delivery); err != nil {
		return fmt.Errorf("failed to update delivery status: %w", err)
	}

	// Mark driver as AVAILABLE again
	if err := s.driverRepo.SetDriverStatus(ctx, driverID, domain.DriverAvailable); err != nil {
		slog.Error("Failed to reset driver status", "driver_id", driverID, "error", err)
	}

	// Publish OrderDelivered event
	if s.producer != nil {
		if err := s.producer.PublishOrderDelivered(ctx, kafka.OrderDeliveredData{
			OrderID:     orderID,
			DriverID:    driverID,
			DeliveredAt: now.Format(time.RFC3339),
		}); err != nil {
			slog.Error("Failed to publish OrderDelivered event", "error", err)
		}
	}

	return nil
}

// FindAndAssignDriver searches for the nearest available driver and assigns them to the order.
func (s *DispatchService) FindAndAssignDriver(ctx context.Context, orderID string, restaurantLoc domain.Location) (*domain.DispatchResult, error) {
	slog.Info("Searching for driver", "order_id", orderID, "restaurant", restaurantLoc)

	// Save initial delivery status as SEARCHING_DRIVER
	now := time.Now().UTC()
	if err := s.driverRepo.SaveDeliveryStatus(ctx, &domain.DeliveryStatus{
		OrderID:   orderID,
		Status:    "SEARCHING_DRIVER",
		UpdatedAt: now,
	}); err != nil {
		slog.Error("Failed to save initial delivery status", "error", err)
	}

	drivers, err := s.driverRepo.GetNearbyDrivers(ctx, restaurantLoc, s.radiusKm)
	if err != nil {
		return &domain.DispatchResult{OrderID: orderID, Success: false, ErrorMsg: err.Error()}, err
	}

	nearest := s.matcher.FindNearestAvailable(restaurantLoc, drivers, s.radiusKm)
	if nearest == nil {
		return &domain.DispatchResult{OrderID: orderID, Success: false, ErrorMsg: "no driver available"}, nil
	}

	// Set driver status to ASSIGNED
	if err := s.driverRepo.SetDriverStatus(ctx, nearest.ID, domain.DriverAssigned); err != nil {
		slog.Error("Failed to set driver status to ASSIGNED", "driver_id", nearest.ID, "error", err)
	}

	// Save dispatch assignment (order → driver mapping) — this is a required write.
	if err := s.driverRepo.SetDispatchAssignment(ctx, orderID, nearest.ID); err != nil {
		slog.Error("Failed to save dispatch assignment", "order_id", orderID, "driver_id", nearest.ID, "error", err)
		return &domain.DispatchResult{OrderID: orderID, Success: false, ErrorMsg: err.Error()}, err
	}

	// Update delivery status to DRIVER_ASSIGNED
	if err := s.driverRepo.SaveDeliveryStatus(ctx, &domain.DeliveryStatus{
		OrderID:        orderID,
		Status:         "DRIVER_ASSIGNED",
		DriverID:       nearest.ID,
		DriverName:     nearest.Name,
		DriverPhone:    nearest.Phone,
		DriverLocation: &nearest.Location,
		UpdatedAt:      time.Now().UTC(),
	}); err != nil {
		slog.Error("Failed to update delivery status", "error", err)
	}

	// Publish DriverAssigned event
	if s.producer != nil {
		if err := s.producer.PublishDriverAssigned(ctx, kafka.DriverAssignedData{
			OrderID:                 orderID,
			DriverID:                nearest.ID,
			DriverName:              nearest.Name,
			DriverPhone:             nearest.Phone,
			EstimatedArrivalMinutes: 10, // Simplified estimate
		}); err != nil {
			slog.Error("Failed to publish DriverAssigned event", "error", err)
		}
	}

	slog.Info("Driver assigned", "order_id", orderID, "driver_id", nearest.ID)
	return &domain.DispatchResult{OrderID: orderID, Driver: nearest, Success: true}, nil
}

// HandleDispatchWithRetry attempts to find a driver with retries.
// If no driver is found after maxRetries, a DispatchFailed event is published.
// This method is called by the Kafka consumer when OrderReadyForPickup is received.
func (s *DispatchService) HandleDispatchWithRetry(ctx context.Context, orderID string, restaurantLat, restaurantLng float64) {
	restaurantLoc := domain.Location{Lat: restaurantLat, Lng: restaurantLng}

	for attempt := 1; attempt <= s.maxRetries; attempt++ {
		slog.Info("Dispatch attempt", "order_id", orderID, "attempt", attempt, "max", s.maxRetries)

		result, err := s.FindAndAssignDriver(ctx, orderID, restaurantLoc)
		if err != nil {
			slog.Error("Dispatch error", "order_id", orderID, "attempt", attempt, "error", err)
		}
		if result != nil && result.Success {
			slog.Info("Dispatch successful", "order_id", orderID, "driver_id", result.Driver.ID)
			return
		}

		// Not the last attempt — wait before retrying
		if attempt < s.maxRetries {
			slog.Info("No driver found, retrying", "order_id", orderID, "wait", s.retryInterval)
			select {
			case <-time.After(s.retryInterval):
				// continue to next attempt
			case <-ctx.Done():
				slog.Info("Dispatch cancelled", "order_id", orderID)
				return
			}
		}
	}

	// All retries exhausted — publish DispatchFailed
	slog.Warn("All dispatch attempts exhausted", "order_id", orderID, "retries", s.maxRetries)

	// Update delivery status to FAILED
	if err := s.driverRepo.SaveDeliveryStatus(ctx, &domain.DeliveryStatus{
		OrderID:   orderID,
		Status:    "FAILED",
		UpdatedAt: time.Now().UTC(),
	}); err != nil {
		slog.Error("Failed to update delivery status to FAILED", "error", err)
	}

	if s.producer != nil {
		if err := s.producer.PublishDispatchFailed(ctx, kafka.DispatchFailedData{
			OrderID:    orderID,
			Reason:     "no driver available after maximum retries",
			RetryCount: s.maxRetries,
		}); err != nil {
			slog.Error("Failed to publish DispatchFailed event", "error", err)
		}
	}
}
