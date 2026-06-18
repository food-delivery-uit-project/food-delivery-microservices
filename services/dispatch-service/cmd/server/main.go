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
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"github.com/food-delivery/dispatch-service/internal/kafka"
	"github.com/food-delivery/dispatch-service/internal/matching"
	"github.com/food-delivery/dispatch-service/internal/repository"
	"github.com/food-delivery/dispatch-service/internal/service"
	"github.com/food-delivery/dispatch-service/internal/telemetry"
)

func main() {
	// Structured JSON logging
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(logger)

	// Load configuration
	cfg := config.Load()
	slog.Info("Starting dispatch-service",
		"port", cfg.ServerPort,
		"redis", cfg.RedisAddr,
		"kafka_brokers", cfg.KafkaBrokers,
	)

	// Initialize OpenTelemetry
	ctx := context.Background()
	shutdownOTel, err := telemetry.InitProvider(ctx, cfg.OTelServiceName)
	if err != nil {
		slog.Error("Failed to initialize OpenTelemetry", "error", err)
	} else {
		defer func() {
			if err := shutdownOTel(ctx); err != nil {
				slog.Error("Failed to shutdown OpenTelemetry", "error", err)
			}
		}()
	}

	// Initialize dependencies
	driverRepo := repository.NewRedisDriverRepository(cfg.RedisAddr)
	matcher := matching.NewMatcher()
	kafkaProducer := kafka.NewProducer(cfg.KafkaBrokers)

	dispatchSvc := service.NewDispatchService(
		driverRepo,
		matcher,
		kafkaProducer,
		cfg.MatchRadiusKm,
		cfg.MatchRetryInterval,
		cfg.MatchMaxRetries,
	)

	// Start Kafka consumer in background
	kafkaConsumer := kafka.NewConsumer(cfg.KafkaBrokers, cfg.KafkaGroupID, dispatchSvc)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go kafkaConsumer.Start(ctx)

	// Start Redis Outbox Relay worker
	outboxRelay := kafka.NewRedisOutboxRelay(driverRepo.GetClient(), kafkaProducer)
	go outboxRelay.Start(ctx)

	// Setup HTTP handlers
	mux := http.NewServeMux()
	handler.RegisterRoutes(mux, dispatchSvc, driverRepo)

	// Health check endpoints
	mux.HandleFunc("GET /health/live", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		if _, err := w.Write([]byte(`{"status":"UP"}`)); err != nil {
			slog.Error("failed to write liveness response", "error", err)
		}
	})
	mux.HandleFunc("GET /health/ready", func(w http.ResponseWriter, r *http.Request) {
		if err := driverRepo.Ping(r.Context()); err != nil {
			w.WriteHeader(http.StatusServiceUnavailable)
			if _, wErr := w.Write([]byte(`{"status":"NOT_READY","reason":"redis_unavailable"}`)); wErr != nil {
				slog.Error("failed to write readiness response", "error", wErr)
			}
			return
		}
		w.WriteHeader(http.StatusOK)
		if _, err := w.Write([]byte(`{"status":"READY"}`)); err != nil {
			slog.Error("failed to write readiness response", "error", err)
		}
	})

	// Wrap with logging middleware
	loggedMux := handler.LoggingMiddleware(mux)

	// Wrap with OpenTelemetry HTTP middleware
	otelMux := otelhttp.NewHandler(loggedMux, "dispatch-service-http")

	rootHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/ws/driver/location" {
			loggedMux.ServeHTTP(w, r)
		} else {
			otelMux.ServeHTTP(w, r)
		}
	})

	// Start server with graceful shutdown
	server := &http.Server{
		Addr:         ":" + cfg.ServerPort,
		Handler:      rootHandler,
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
	cancel() // Stop Kafka consumer

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		slog.Error("Server forced shutdown", "error", err)
	}
	if err := kafkaConsumer.Close(); err != nil {
		slog.Error("Failed to close Kafka consumer", "error", err)
	}
	if err := kafkaProducer.Close(); err != nil {
		slog.Error("Failed to close Kafka producer", "error", err)
	}
	if err := driverRepo.Close(); err != nil {
		slog.Error("Failed to close Redis", "error", err)
	}
	slog.Info("Server stopped")
}
