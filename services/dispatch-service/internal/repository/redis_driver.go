package repository

import (
	"context"
	"fmt"
	"log/slog"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/food-delivery/dispatch-service/internal/domain"
)

const (
	activeDriversKey   = "active_drivers"
	driverKeyPrefix    = "driver:"
	deliveryKeyPrefix  = "delivery:"
	dispatchKeyPrefix  = "dispatch:order:"
	OutboxStreamKey    = "dispatch_outbox_events"
	dispatchAssignTTL  = 1 * time.Hour
)

// RedisDriverRepository manages driver locations and delivery state in Redis.
type RedisDriverRepository struct {
	client *redis.Client
}

// NewRedisDriverRepository creates a new Redis-backed driver repository.
func NewRedisDriverRepository(addr string) *RedisDriverRepository {
	client := redis.NewClient(&redis.Options{
		Addr:         addr,
		DialTimeout:  5 * time.Second,
		ReadTimeout:  3 * time.Second,
		WriteTimeout: 3 * time.Second,
		PoolSize:     10,
	})
	slog.Info("Connecting to Redis", "addr", addr)
	return &RedisDriverRepository{client: client}
}

// NewRedisDriverRepositoryWithClient creates a repository with a pre-configured client (for testing).
func NewRedisDriverRepositoryWithClient(client *redis.Client) *RedisDriverRepository {
	return &RedisDriverRepository{client: client}
}

// GetClient returns the underlying Redis client for use by other components like the Outbox Relay.
func (r *RedisDriverRepository) GetClient() *redis.Client {
	return r.client
}

// Ping verifies the Redis connection is alive (used by readiness probe).
func (r *RedisDriverRepository) Ping(ctx context.Context) error {
	return r.client.Ping(ctx).Err()
}

// UpdateDriverLocation stores or updates a driver's GPS coordinates using GEOADD.
func (r *RedisDriverRepository) UpdateDriverLocation(ctx context.Context, driverID string, loc domain.Location) error {
	return r.client.GeoAdd(ctx, activeDriversKey, &redis.GeoLocation{
		Name:      driverID,
		Longitude: loc.Lng,
		Latitude:  loc.Lat,
	}).Err()
}

// GetNearbyDrivers returns drivers within radiusKm of the center point.
// Uses Redis GEORADIUS sorted by distance ASC.
func (r *RedisDriverRepository) GetNearbyDrivers(ctx context.Context, center domain.Location, radiusKm float64) ([]domain.Driver, error) {
	//nolint:staticcheck // miniredis does not support GEOSEARCH yet (SA1019)
	results, err := r.client.GeoRadius(ctx, activeDriversKey, center.Lng, center.Lat, &redis.GeoRadiusQuery{
		Radius:    radiusKm,
		Unit:      "km",
		Sort:      "ASC",
		Count:     10,
		WithCoord: true,
	}).Result()
	if err != nil {
		return nil, fmt.Errorf("GEORADIUS failed: %w", err)
	}

	drivers := make([]domain.Driver, 0, len(results))
	for _, loc := range results {
		// Get driver metadata from hash
		driver, err := r.getDriverInfo(ctx, loc.Name)
		if err != nil {
			slog.Warn("Failed to get driver info, skipping", "driver_id", loc.Name, "error", err)
			continue
		}

		driver.Location = domain.Location{
			Lat: loc.Latitude,
			Lng: loc.Longitude,
		}

		drivers = append(drivers, *driver)
	}

	return drivers, nil
}

// SetDriverStatus updates the driver status and last_seen timestamp in a Redis hash.
func (r *RedisDriverRepository) SetDriverStatus(ctx context.Context, driverID string, status domain.DriverStatus) error {
	key := driverKeyPrefix + driverID
	return r.client.HSet(ctx, key, map[string]interface{}{
		"status":    string(status),
		"last_seen": time.Now().UTC().Format(time.RFC3339),
	}).Err()
}

// SetDriverInfo stores driver metadata (name, phone, vehicle_type) in a Redis hash.
func (r *RedisDriverRepository) SetDriverInfo(ctx context.Context, driver domain.Driver) error {
	key := driverKeyPrefix + driver.ID
	return r.client.HSet(ctx, key, map[string]interface{}{
		"name":         driver.Name,
		"phone":        driver.Phone,
		"vehicle_type": driver.VehicleType,
		"status":       string(driver.Status),
		"last_seen":    time.Now().UTC().Format(time.RFC3339),
	}).Err()
}

// getDriverInfo reads driver metadata from the driver:{id} hash.
func (r *RedisDriverRepository) getDriverInfo(ctx context.Context, driverID string) (*domain.Driver, error) {
	key := driverKeyPrefix + driverID
	data, err := r.client.HGetAll(ctx, key).Result()
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		// Driver has geo position but no metadata hash — return with just ID.
		return &domain.Driver{
			ID:     driverID,
			Status: domain.DriverAvailable,
		}, nil
	}

	return &domain.Driver{
		ID:          driverID,
		Name:        data["name"],
		Phone:       data["phone"],
		VehicleType: data["vehicle_type"],
		Status:      domain.DriverStatus(data["status"]),
	}, nil
}

// SaveDeliveryStatus persists the delivery state for an order in a Redis hash.
func (r *RedisDriverRepository) SaveDeliveryStatus(ctx context.Context, status *domain.DeliveryStatus) error {
	key := deliveryKeyPrefix + status.OrderID
	fields := map[string]interface{}{
		"order_id":   status.OrderID,
		"status":     status.Status,
		"updated_at": status.UpdatedAt.Format(time.RFC3339),
	}
	if status.DriverID != "" {
		fields["driver_id"] = status.DriverID
		fields["driver_name"] = status.DriverName
		fields["driver_phone"] = status.DriverPhone
	}
	if status.DriverLocation != nil {
		fields["driver_lat"] = strconv.FormatFloat(status.DriverLocation.Lat, 'f', 6, 64)
		fields["driver_lng"] = strconv.FormatFloat(status.DriverLocation.Lng, 'f', 6, 64)
	}
	if status.EstimatedDeliveryTime != nil {
		fields["estimated_delivery_time"] = status.EstimatedDeliveryTime.Format(time.RFC3339)
	}
	return r.client.HSet(ctx, key, fields).Err()
}

// GetDeliveryStatus returns the delivery state for an order from Redis.
func (r *RedisDriverRepository) GetDeliveryStatus(ctx context.Context, orderID string) (*domain.DeliveryStatus, error) {
	key := deliveryKeyPrefix + orderID
	data, err := r.client.HGetAll(ctx, key).Result()
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		return nil, fmt.Errorf("delivery not found for order %s", orderID)
	}

	updatedAt, _ := time.Parse(time.RFC3339, data["updated_at"])

	status := &domain.DeliveryStatus{
		OrderID:   data["order_id"],
		Status:    data["status"],
		DriverID:  data["driver_id"],
		DriverName: data["driver_name"],
		DriverPhone: data["driver_phone"],
		UpdatedAt: updatedAt,
	}

	if lat, ok := data["driver_lat"]; ok {
		latF, _ := strconv.ParseFloat(lat, 64)
		lngF, _ := strconv.ParseFloat(data["driver_lng"], 64)
		status.DriverLocation = &domain.Location{Lat: latF, Lng: lngF}
	}

	if edt, ok := data["estimated_delivery_time"]; ok {
		t, _ := time.Parse(time.RFC3339, edt)
		status.EstimatedDeliveryTime = &t
	}

	return status, nil
}

// SetDispatchAssignment records the driver assigned to an order with a TTL.
func (r *RedisDriverRepository) SetDispatchAssignment(ctx context.Context, orderID, driverID string) error {
	key := dispatchKeyPrefix + orderID
	return r.client.Set(ctx, key, driverID, dispatchAssignTTL).Err()
}

	// SetDispatchAssignmentWithOutbox records the dispatch assignment and appends an event to the Redis Stream atomically.
func (r *RedisDriverRepository) SetDispatchAssignmentWithOutbox(ctx context.Context, orderID, driverID, eventPayload, traceparent string) error {
	script := `
		-- Set the dispatch assignment with TTL
		redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
		-- Add the outbox event to the stream
		redis.call('XADD', KEYS[2], '*', 'payload', ARGV[3], 'traceparent', ARGV[4])
		return 1
	`
	key := dispatchKeyPrefix + orderID
	ttlSeconds := int(dispatchAssignTTL.Seconds())

	_, err := r.client.Eval(ctx, script, []string{key, OutboxStreamKey}, driverID, ttlSeconds, eventPayload, traceparent).Result()
	return err
}

// GetDispatchAssignment returns the driver ID assigned to an order.
func (r *RedisDriverRepository) GetDispatchAssignment(ctx context.Context, orderID string) (string, error) {
	key := dispatchKeyPrefix + orderID
	result, err := r.client.Get(ctx, key).Result()
	if err == redis.Nil {
		return "", fmt.Errorf("no dispatch assignment for order %s", orderID)
	}
	return result, err
}

// RemoveDriverFromGeo removes a driver from the active_drivers geo set (when going offline).
func (r *RedisDriverRepository) RemoveDriverFromGeo(ctx context.Context, driverID string) error {
	return r.client.ZRem(ctx, activeDriversKey, driverID).Err()
}

// Close closes the Redis client connection.
func (r *RedisDriverRepository) Close() error {
	return r.client.Close()
}
