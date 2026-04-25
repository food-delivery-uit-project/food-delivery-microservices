package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/food-delivery/dispatch-service/internal/config"
	"github.com/food-delivery/dispatch-service/internal/handler"
	"github.com/food-delivery/dispatch-service/internal/matching"
	"github.com/food-delivery/dispatch-service/internal/repository"
	"github.com/food-delivery/dispatch-service/internal/service"
)

func main() {
	// Structured JSON logging
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(logger)

	// Load configuration
	cfg := config.Load()
	slog.Info("Starting dispatch-service", "port", cfg.ServerPort)

	// Initialize dependencies
	driverRepo := repository.NewRedisDriverRepository(cfg.RedisAddr)
	matcher := matching.NewMatcher()
	dispatchSvc := service.NewDispatchService(driverRepo, matcher)

	// Setup HTTP handlers
	mux := http.NewServeMux()
	handler.RegisterRoutes(mux, dispatchSvc)

	// Health check endpoints
	mux.HandleFunc("GET /health/live", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		if _, err := w.Write([]byte(`{"status":"UP"}`)); err != nil {
			slog.Error("failed to write liveness response", "error", err)
		}
	})
	mux.HandleFunc("GET /health/ready", func(w http.ResponseWriter, r *http.Request) {
		// TODO: check Redis connectivity
		w.WriteHeader(http.StatusOK)
		if _, err := w.Write([]byte(`{"status":"READY"}`)); err != nil {
			slog.Error("failed to write readiness response", "error", err)
		}
	})

	// Start server with graceful shutdown
	server := &http.Server{
		Addr:         ":" + cfg.ServerPort,
		Handler:      mux,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		slog.Info("Server listening", "addr", server.Addr)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("Server failed", "error", err)
			os.Exit(1)
		}
	}()

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	slog.Info("Shutting down server...")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		slog.Error("Server forced shutdown", "error", err)
	}
	slog.Info("Server stopped")
}
