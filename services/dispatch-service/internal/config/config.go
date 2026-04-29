package config

import (
	"os"
	"strconv"
	"strings"
	"time"
)

// Config holds all configuration values for the dispatch service.
type Config struct {
	ServerPort         string
	RedisAddr          string
	KafkaBrokers       []string
	KafkaGroupID       string
	OrderServiceURL    string
	MatchRetryInterval time.Duration
	MatchMaxRetries    int
	MatchRadiusKm      float64
}

// Load reads configuration from environment variables with sensible defaults.
func Load() *Config {
	retrySeconds := parseIntOrDefault(getEnv("MATCH_RETRY_INTERVAL_SEC", "30"), 30)
	maxRetries := parseIntOrDefault(getEnv("MATCH_MAX_RETRIES", "5"), 5)
	radiusKm := parseFloatOrDefault(getEnv("MATCH_RADIUS_KM", "5.0"), 5.0)

	brokersStr := getEnv("KAFKA_BROKERS", "localhost:9092")
	rawBrokers := strings.Split(brokersStr, ",")
	brokers := make([]string, 0, len(rawBrokers))
	for _, b := range rawBrokers {
		if trimmed := strings.TrimSpace(b); trimmed != "" {
			brokers = append(brokers, trimmed)
		}
	}

	return &Config{
		ServerPort:         getEnv("SERVER_PORT", "8080"),
		RedisAddr:          getEnv("REDIS_ADDR", "localhost:6379"),
		KafkaBrokers:       brokers,
		KafkaGroupID:       getEnv("KAFKA_GROUP_ID", "dispatch-service-group"),
		OrderServiceURL:    getEnv("ORDER_SERVICE_URL", "http://localhost:8083"),
		MatchRetryInterval: time.Duration(retrySeconds) * time.Second,
		MatchMaxRetries:    maxRetries,
		MatchRadiusKm:      radiusKm,
	}
}

func getEnv(key, defaultValue string) string {
	if value, exists := os.LookupEnv(key); exists {
		return value
	}
	return defaultValue
}

func parseIntOrDefault(s string, defaultVal int) int {
	v, err := strconv.Atoi(s)
	if err != nil {
		return defaultVal
	}
	return v
}

func parseFloatOrDefault(s string, defaultVal float64) float64 {
	v, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return defaultVal
	}
	return v
}
