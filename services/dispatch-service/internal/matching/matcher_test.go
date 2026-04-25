package matching

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/food-delivery/dispatch-service/internal/domain"
)

func TestFindNearestAvailable_ReturnsClosestDriver(t *testing.T) {
	matcher := NewMatcher()

	center := domain.Location{Lat: 10.7731, Lng: 106.7030} // Q1, HCM

	drivers := []domain.Driver{
		{ID: "d1", Location: domain.Location{Lat: 10.7750, Lng: 106.7050}, Status: domain.DriverAvailable}, // ~0.3km
		{ID: "d2", Location: domain.Location{Lat: 10.7800, Lng: 106.7100}, Status: domain.DriverAvailable}, // ~1.1km
		{ID: "d3", Location: domain.Location{Lat: 10.7900, Lng: 106.7200}, Status: domain.DriverAvailable}, // ~2.5km
	}

	result := matcher.FindNearestAvailable(center, drivers, 3.0)

	assert.NotNil(t, result)
	assert.Equal(t, "d1", result.ID)
}

func TestFindNearestAvailable_SkipsBusyDrivers(t *testing.T) {
	matcher := NewMatcher()

	center := domain.Location{Lat: 10.7731, Lng: 106.7030}

	drivers := []domain.Driver{
		{ID: "d1", Location: domain.Location{Lat: 10.7750, Lng: 106.7050}, Status: domain.DriverBusy},      // Closest but busy
		{ID: "d2", Location: domain.Location{Lat: 10.7800, Lng: 106.7100}, Status: domain.DriverAvailable}, // Next closest, available
	}

	result := matcher.FindNearestAvailable(center, drivers, 3.0)

	assert.NotNil(t, result)
	assert.Equal(t, "d2", result.ID)
}

func TestFindNearestAvailable_ReturnsNilWhenNoDriverInRadius(t *testing.T) {
	matcher := NewMatcher()

	center := domain.Location{Lat: 10.7731, Lng: 106.7030}

	drivers := []domain.Driver{
		{ID: "d1", Location: domain.Location{Lat: 10.8500, Lng: 106.8000}, Status: domain.DriverAvailable}, // ~12km away
	}

	result := matcher.FindNearestAvailable(center, drivers, 3.0) // 3km radius

	assert.Nil(t, result)
}

func TestFindNearestAvailable_ReturnsNilForEmptyList(t *testing.T) {
	matcher := NewMatcher()

	center := domain.Location{Lat: 10.7731, Lng: 106.7030}

	result := matcher.FindNearestAvailable(center, []domain.Driver{}, 3.0)

	assert.Nil(t, result)
}
