package handler

import (
	"encoding/json"
	"net/http"

	"github.com/food-delivery/dispatch-service/internal/repository"
	"github.com/food-delivery/dispatch-service/internal/service"
)

// RegisterRoutes registers all HTTP and WebSocket handlers for the dispatch service.
func RegisterRoutes(mux *http.ServeMux, svc *service.DispatchService, driverRepo *repository.RedisDriverRepository) {
	h := &dispatchHandler{svc: svc}

	// Public REST endpoints
	mux.HandleFunc("GET /api/v1/delivery/{orderId}", h.getDeliveryStatus)
	mux.HandleFunc("PATCH /api/v1/delivery/{orderId}/pickup", h.confirmPickup)
	mux.HandleFunc("PATCH /api/v1/delivery/{orderId}/deliver", h.confirmDelivery)

	// WebSocket endpoint for driver GPS streaming
	ws := newWSHandler(driverRepo)
	mux.HandleFunc("/ws/driver/location", ws.handleDriverWS)

	// Swagger / OpenAPI endpoints
	mux.HandleFunc("GET /docs/openapi.yaml", func(w http.ResponseWriter, r *http.Request) {
		http.ServeFile(w, r, "docs/openapi.yaml")
	})
	mux.HandleFunc("GET /api-docs/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html")
		_, _ = w.Write([]byte(`
<!DOCTYPE html>
<html>
  <head>
    <title>Dispatch Service API</title>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://fonts.googleapis.com/css?family=Montserrat:300,400,700|Roboto:300,400,700" rel="stylesheet">
    <style>body { margin: 0; padding: 0; }</style>
  </head>
  <body>
    <redoc spec-url='/docs/openapi.yaml'></redoc>
    <script src="https://cdn.redoc.ly/redoc/latest/bundles/redoc.standalone.js"> </script>
  </body>
</html>`))
	})
}

type dispatchHandler struct {
	svc *service.DispatchService
}

func (h *dispatchHandler) getDeliveryStatus(w http.ResponseWriter, r *http.Request) {
	orderId := r.PathValue("orderId")
	if orderId == "" {
		writeError(w, http.StatusBadRequest, "ORDER_ID_REQUIRED", "orderId path param is required")
		return
	}

	status, err := h.svc.GetDeliveryStatus(r.Context(), orderId)
	if err != nil {
		writeError(w, http.StatusNotFound, "DELIVERY_NOT_FOUND", err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"success": true,
		"data":    status,
	})
}

func (h *dispatchHandler) confirmPickup(w http.ResponseWriter, r *http.Request) {
	orderId := r.PathValue("orderId")
	driverId := r.Header.Get("X-User-Id") // Injected by Kong

	if driverId == "" {
		writeError(w, http.StatusUnauthorized, "DRIVER_ID_REQUIRED", "X-User-Id header is required")
		return
	}

	if err := h.svc.ConfirmPickup(r.Context(), orderId, driverId); err != nil {
		writeError(w, http.StatusConflict, "PICKUP_FAILED", err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"success": true,
		"data":    map[string]string{"status": "PICKED_UP"},
	})
}

func (h *dispatchHandler) confirmDelivery(w http.ResponseWriter, r *http.Request) {
	orderId := r.PathValue("orderId")
	driverId := r.Header.Get("X-User-Id")

	if driverId == "" {
		writeError(w, http.StatusUnauthorized, "DRIVER_ID_REQUIRED", "X-User-Id header is required")
		return
	}

	if err := h.svc.ConfirmDelivery(r.Context(), orderId, driverId); err != nil {
		writeError(w, http.StatusConflict, "DELIVERY_FAILED", err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"success": true,
		"data":    map[string]string{"status": "DELIVERED"},
	})
}

// --- Helpers ---

func writeJSON(w http.ResponseWriter, statusCode int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	if err := json.NewEncoder(w).Encode(data); err != nil {
		http.Error(w, "failed to encode response", http.StatusInternalServerError)
	}
}

func writeError(w http.ResponseWriter, statusCode int, code, message string) {
	writeJSON(w, statusCode, map[string]any{
		"success": false,
		"error": map[string]string{
			"code":    code,
			"message": message,
		},
	})
}
