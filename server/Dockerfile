FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
RUN go mod tidy
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/vibecodegram-api .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/vibecodegram-api /vibecodegram-api
EXPOSE 8080
ENTRYPOINT ["/vibecodegram-api"]
