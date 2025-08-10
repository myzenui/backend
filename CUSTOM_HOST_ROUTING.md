# 🐳 Custom Host Routing Feature

## Overview

The CloudflareService now supports custom host routing, allowing you to route traffic to services running on different hosts (Docker containers, microservices, remote servers) instead of just `localhost`.

## 🚀 What's New

### Before
```json
{
  "hostname": "app.armikom.com",
  "port": 5000,
  "protocol": "http"
}
// Always routed to: http://localhost:5000
```

### After
```json
{
  "hostname": "app.armikom.com", 
  "port": 5000,
  "protocol": "http",
  "host": "menu2"
}
// Now routes to: http://menu2:5000
```

## 📋 Supported Endpoints

All CloudflareService endpoints now accept an optional `host` parameter:

- ✅ `POST /api/cloudflare/create-dns-only`
- ✅ `POST /api/cloudflare/create-tunnel-dns`
- ✅ `POST /api/cloudflare/tunnel-route`
- ✅ `POST /api/cloudflare/complete-route`

## 🔧 Usage Examples

### Docker Container Routing
```bash
# Route to a Docker container named "menu2"
curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -H "Content-Type: application/json" \
  -d '{
    "hostname": "menu.armikom.com",
    "port": 5000,
    "protocol": "http",
    "host": "menu2"
  }'
```

### Microservice Routing
```bash
# Route to a microservice named "api-service"
curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -H "Content-Type: application/json" \
  -d '{
    "hostname": "api.armikom.com", 
    "port": 3000,
    "protocol": "http",
    "host": "api-service"
  }'
```

### Remote Server Routing
```bash
# Route to a remote server by IP
curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -H "Content-Type: application/json" \
  -d '{
    "hostname": "remote.armikom.com",
    "port": 8080,
    "protocol": "https",
    "host": "192.168.1.100"
  }'
```

### Default Localhost (Backward Compatible)
```bash
# Omit "host" parameter for localhost (backward compatible)
curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -H "Content-Type: application/json" \
  -d '{
    "hostname": "local.armikom.com",
    "port": 8080,
    "protocol": "http"
  }'
# Routes to: http://localhost:8080
```

## 🐳 Docker Compose Example

Perfect for Docker Compose setups:

```yaml
# docker-compose.yml
services:
  menu2:
    image: menu-app:latest
    ports:
      - "5000:5000"
    
  api-service:
    image: api-app:latest
    ports:
      - "3000:3000"
      
  zen-backend:
    image: zen-backend:latest
    ports:
      - "8080:8080"
    depends_on:
      - menu2
      - api-service
```

Then route traffic:
```bash
# Create routes for each service
curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -d '{"hostname": "menu.armikom.com", "port": 5000, "protocol": "http", "host": "menu2"}'

curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -d '{"hostname": "api.armikom.com", "port": 3000, "protocol": "http", "host": "api-service"}'
```

## 🔄 Idempotent Behavior

The custom host routing is fully idempotent:

```bash
# First call - creates route
curl -X POST .../complete-route -d '{"hostname": "app.com", "port": 5000, "host": "menu2"}'
# Response: "Complete route created successfully"

# Same call again - detects existing
curl -X POST .../complete-route -d '{"hostname": "app.com", "port": 5000, "host": "menu2"}'  
# Response: "Complete route already exists with exact configuration"

# Different host - updates route
curl -X POST .../complete-route -d '{"hostname": "app.com", "port": 5000, "host": "menu3"}'
# Response: "Tunnel configuration updated successfully" (menu2 → menu3)
```

## 📋 Host Parameter Details

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `host` | String | No | `"localhost"` | Target host to route traffic to |

### Valid Host Examples:
- ✅ `"localhost"` - Local machine
- ✅ `"menu2"` - Docker container name
- ✅ `"api-service"` - Kubernetes service name  
- ✅ `"192.168.1.100"` - IP address
- ✅ `"internal.company.com"` - Internal hostname

## 🔍 Manual Configuration Format

When using `create-dns-only`, the manual configuration instructions now include the custom host:

```yaml
# ~/.cloudflared/config.yml
ingress:
  - hostname: menu.armikom.com
    service: http://menu2:5000  # ← Custom host included
  - service: http_status:404
```

## 🚀 Use Cases

### 1. **Docker Container Routing**
Route different subdomains to different containers:
- `menu.armikom.com` → `menu2:5000`
- `api.armikom.com` → `api-service:3000`
- `auth.armikom.com` → `auth-service:8080`

### 2. **Microservice Architecture**
Expose multiple microservices through one tunnel:
- `users.armikom.com` → `user-service:3000`
- `orders.armikom.com` → `order-service:3001`
- `payments.armikom.com` → `payment-service:3002`

### 3. **Development Environment**
Route to development services:
- `dev-app.armikom.com` → `localhost:3000`
- `dev-api.armikom.com` → `docker-api:8080`

### 4. **Load Balancer Integration**
Route to internal load balancers:
- `app.armikom.com` → `internal-lb:80`
- `api.armikom.com` → `api-lb:443`

## ⚡ Performance Notes

- No performance impact - host parameter is only used during tunnel configuration
- Cloudflare tunnel handles the actual routing efficiently
- DNS resolution happens at the tunnel server level

## 🔧 Troubleshooting

### Issue: "Connection refused"
**Cause**: Target host is not accessible from tunnel server
**Solution**: Ensure the target host/container is running and accessible

### Issue: "Name resolution failed"
**Cause**: Host name cannot be resolved
**Solution**: Use IP address or ensure hostname is resolvable

### Issue: "Service unreachable"
**Cause**: Port is not open on target host
**Solution**: Verify the service is listening on the specified port

## 🎯 Migration Guide

### From Hardcoded Localhost
If you were previously hardcoding localhost routes, you can now make them configurable:

**Before:**
```bash
curl -X POST .../complete-route -d '{"hostname": "app.com", "port": 5000, "protocol": "http"}'
# Always routed to localhost:5000
```

**After (backward compatible):**
```bash
# Still works - defaults to localhost
curl -X POST .../complete-route -d '{"hostname": "app.com", "port": 5000, "protocol": "http"}'

# Now configurable for containers
curl -X POST .../complete-route -d '{"hostname": "app.com", "port": 5000, "protocol": "http", "host": "menu2"}'
```

### Updating Existing Routes
Simply call the same endpoint with the new `host` parameter - the route will be updated idempotently:

```bash
# Existing route: app.com → localhost:5000
# Update to container: app.com → menu2:5000
curl -X POST .../complete-route -d '{"hostname": "app.com", "port": 5000, "host": "menu2"}'
```

## 🎉 Summary

The custom host routing feature provides:

- ✅ **Flexibility**: Route to any host/container/service
- ✅ **Backward Compatibility**: Existing code works unchanged  
- ✅ **Idempotency**: Safe to run multiple times
- ✅ **Docker Ready**: Perfect for containerized environments
- ✅ **Microservice Friendly**: Route different domains to different services
- ✅ **Easy Migration**: Update existing routes with one API call

Your CloudflareService is now ready for modern containerized and microservice architectures! 🚀