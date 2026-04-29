package handler

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/gorilla/websocket"

	"github.com/food-delivery/dispatch-service/internal/domain"
	"github.com/food-delivery/dispatch-service/internal/repository"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin:     func(r *http.Request) bool { return true }, // Allow all origins in dev
}

// GPSMessage represents the incoming GPS data from driver apps.
type GPSMessage struct {
	Lat       float64 `json:"lat"`
	Lng       float64 `json:"lng"`
	Timestamp string  `json:"timestamp"`
}

// wsHandler handles WebSocket connections for driver GPS streaming.
type wsHandler struct {
	driverRepo *repository.RedisDriverRepository
}

// newWSHandler creates a new WebSocket handler.
func newWSHandler(repo *repository.RedisDriverRepository) *wsHandler {
	return &wsHandler{driverRepo: repo}
}

// handleDriverWS upgrades to WebSocket and processes GPS updates from drivers.
// Authentication: expects X-User-Id header (injected by Kong after JWT validation).
// In a real deployment, the query param ?token=xxx would be validated by Kong.
func (h *wsHandler) handleDriverWS(w http.ResponseWriter, r *http.Request) {
	driverID := r.Header.Get("X-User-Id")
	if driverID == "" {
		// Fallback: check query param for direct WebSocket connections
		driverID = r.URL.Query().Get("driver_id")
	}
	if driverID == "" {
		http.Error(w, "driver identification required", http.StatusUnauthorized)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		slog.Error("WebSocket upgrade failed", "error", err, "driver_id", driverID)
		return
	}
	defer conn.Close()

	slog.Info("Driver WebSocket connected", "driver_id", driverID)

	// Mark driver as AVAILABLE when connected
	ctx := r.Context()
	if err := h.driverRepo.SetDriverStatus(ctx, driverID, domain.DriverAvailable); err != nil {
		slog.Error("Failed to set driver status", "error", err)
	}

	// Set read deadline and pong handler for keepalive
	if err := conn.SetReadDeadline(time.Now().Add(60 * time.Second)); err != nil {
		slog.Error("Failed to set read deadline", "error", err)
	}
	conn.SetPongHandler(func(string) error {
		return conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	})

	// Start ping ticker for keepalive
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	done := make(chan struct{})
	defer close(done)

	go func() {
		for {
			select {
			case <-done:
				return
			case <-ticker.C:
				if err := conn.WriteMessage(websocket.PingMessage, nil); err != nil {
					return
				}
			}
		}
	}()

	// Read GPS messages from driver
	for {
		_, message, err := conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseNormalClosure) {
				slog.Error("WebSocket read error", "error", err, "driver_id", driverID)
			}
			break
		}

		var gps GPSMessage
		if err := json.Unmarshal(message, &gps); err != nil {
			slog.Warn("Invalid GPS message", "error", err, "driver_id", driverID)
			continue
		}

		// Update driver location in Redis via GEOADD
		loc := domain.Location{Lat: gps.Lat, Lng: gps.Lng}
		if err := h.driverRepo.UpdateDriverLocation(ctx, driverID, loc); err != nil {
			slog.Error("Failed to update driver location", "error", err, "driver_id", driverID)
		}

		slog.Debug("GPS update", "driver_id", driverID, "lat", gps.Lat, "lng", gps.Lng)
	}

	// Driver disconnected — mark as OFFLINE and remove from geo set
	slog.Info("Driver WebSocket disconnected", "driver_id", driverID)
	if err := h.driverRepo.SetDriverStatus(ctx, driverID, domain.DriverOffline); err != nil {
		slog.Error("Failed to set driver offline", "error", err)
	}
	if err := h.driverRepo.RemoveDriverFromGeo(ctx, driverID); err != nil {
		slog.Error("Failed to remove driver from geo set", "error", err)
	}
}
