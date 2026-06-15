# Build stage
FROM golang:1.24-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /dispatch-service ./cmd/server

# Run stage (minimal image)
FROM alpine:latest
COPY --from=builder /dispatch-service /dispatch-service
COPY docs/ /docs/
EXPOSE 8080
ENTRYPOINT ["/dispatch-service"]
