# Cloudflare Service Setup Guide

## Overview

The CloudflareService enables your application to dynamically create DNS records AND configure tunnel routes that point to your existing Cloudflare tunnel. This allows you to expose different local applications/ports through subdomains like `zen-app1.armikom.com`, `zen-app2.armikom.com`, etc., with complete automation of both DNS and tunnel routing configuration.

## Prerequisites

1. **Existing Cloudflare Tunnel**: You need a running Cloudflare tunnel with ID `7a1dbacd-f34f-4662-81bc-6ee718f898e7` (as specified in your request)
2. **Cloudflare API Token**: Create an API token with Zone:Edit, DNS:Edit, and Cloudflare Tunnel:Edit permissions
3. **Zone ID**: Your Cloudflare zone ID for the domain (e.g., armikom.com)
4. **Account ID**: Your Cloudflare account ID (found in dashboard sidebar)

## Configuration

### 1. Get Cloudflare API Token

1. Go to [Cloudflare API Tokens](https://dash.cloudflare.com/profile/api-tokens)
2. Click "Create Token"
3. Use "Custom token" template
4. Set permissions:
   - Zone:Edit for your zone
   - DNS:Edit for your zone
   - Cloudflare Tunnel:Edit for your account
5. Copy the generated token

### 2. Get Zone ID and Account ID

1. Go to your domain in Cloudflare dashboard
2. Scroll down to the right sidebar
3. Copy the "Zone ID"
4. Copy the "Account ID" (also in right sidebar)

### 3. Configure Application Properties

Add these to your `application.properties` or set as environment variables:

```properties
cloudflare.api.token=your-actual-api-token-here
cloudflare.zone.id=your-actual-zone-id-here
cloudflare.account.id=your-actual-account-id-here
cloudflare.tunnel.id=7a1dbacd-f34f-4662-81bc-6ee718f898e7
```

Or use environment variables:
```bash
export CLOUDFLARE_API_TOKEN="your-actual-api-token-here"
export CLOUDFLARE_ZONE_ID="your-actual-zone-id-here"
export CLOUDFLARE_ACCOUNT_ID="your-actual-account-id-here"
export CLOUDFLARE_TUNNEL_ID="7a1dbacd-f34f-4662-81bc-6ee718f898e7"
```

## API Endpoints

### 🚀 **RECOMMENDED: Complete Route Management**

### Create Complete Route (DNS + Tunnel)
`POST /api/cloudflare/complete-route`

**⭐ Best option** - Creates both DNS record and tunnel route in one call.

**Request:**
```json
{
  "hostname": "zen-app1.armikom.com",
  "port": 45001,
  "protocol": "http",
  "path": "/api"  // optional
}
```

**Response:**
```json
{
  "success": true,
  "message": "Complete route created successfully. zen-app1.armikom.com is now accessible and routed to http://localhost:45001",
  "dnsName": "zen-app1.armikom.com",
  "recordId": "abc123def456",
  "targetUrl": "http://localhost:45001"
}
```

### 🔧 **DNS Record Management**

### Create Tunnel DNS Record
`POST /api/cloudflare/create-tunnel-dns`

Creates only a CNAME record pointing to your tunnel (requires separate tunnel configuration).

### List DNS Records
`GET /api/cloudflare/dns-records`

Returns all DNS records for your zone.

### Delete DNS Record
`DELETE /api/cloudflare/dns-record/{recordId}`

Deletes a specific DNS record by ID.

### 🛠️ **Tunnel Route Configuration**

### Get Tunnel Configuration
`GET /api/cloudflare/tunnel-configuration`

Returns current tunnel ingress rules.

### Add Tunnel Route
`POST /api/cloudflare/tunnel-route`

Adds a route to the tunnel configuration.

**Request:**
```json
{
  "hostname": "zen-app1.armikom.com",
  "port": 45001,
  "protocol": "http",
  "path": "/api"  // optional
}
```

### Remove Tunnel Route
`DELETE /api/cloudflare/tunnel-route/{hostname}`

Removes a route from the tunnel configuration.

### ℹ️ **Information**

### Get Tunnel Info
`GET /api/cloudflare/tunnel-info`

Returns tunnel configuration information.

## Tunnel Configuration

🎉 **NEW**: Tunnel configuration is now **fully automated** via the API! You no longer need to manually edit config files or use CLI commands.

### ✅ Automatic Configuration (Recommended)

Use the **complete route API** which handles everything:

```bash
curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -H "Content-Type: application/json" \
  -d '{"hostname": "zen-app1.armikom.com", "port": 45001, "protocol": "http"}'
```

This automatically:
1. Creates the DNS CNAME record
2. Configures the tunnel route
3. Your app is immediately accessible!

### 🔧 Manual Configuration (Legacy)

If you prefer manual configuration or need custom setups:

#### Option 1: Using Tunnel Configuration File

Create or update your tunnel configuration file (usually `~/.cloudflared/config.yml`):

```yaml
tunnel: 7a1dbacd-f34f-4662-81bc-6ee718f898e7
credentials-file: /path/to/credentials.json

ingress:
  - hostname: zen-app1.armikom.com
    service: http://localhost:45001
  - hostname: zen-app2.armikom.com  
    service: http://localhost:45002
  - service: http_status:404
```

#### Option 2: Using CLI Commands

```bash
# Route a specific hostname to a local port
cloudflared tunnel route dns 7a1dbacd-f34f-4662-81bc-6ee718f898e7 zen-app1.armikom.com

# Configure ingress rules
cloudflared tunnel ingress add zen-app1.armikom.com http://localhost:45001
```

## Workflow Examples

### 🚀 **Simple Workflow (Recommended)**

1. **Start your local application** on port 45001
2. **Create complete route** (DNS + Tunnel in one call):
   ```bash
   curl -X POST http://localhost:8080/api/cloudflare/complete-route \
     -H "Content-Type: application/json" \
     -d '{"hostname": "zen-app1.armikom.com", "port": 45001, "protocol": "http"}'
   ```
3. **Access your app** immediately at `https://zen-app1.armikom.com`

That's it! No manual configuration needed.

### 🔧 **Advanced Workflow (Step by Step)**

1. **Start your local application** on port 45001
2. **Create DNS record**:
   ```bash
   curl -X POST http://localhost:8080/api/cloudflare/create-tunnel-dns \
     -H "Content-Type: application/json" \
     -d '{"dnsName": "zen-app1.armikom.com", "port": 45001, "protocol": "http"}'
   ```
3. **Add tunnel route**:
   ```bash
   curl -X POST http://localhost:8080/api/cloudflare/tunnel-route \
     -H "Content-Type: application/json" \
     -d '{"hostname": "zen-app1.armikom.com", "port": 45001, "protocol": "http"}'
   ```
4. **Access your app** at `https://zen-app1.armikom.com`

### 📱 **Multiple Apps Example**

Expose multiple apps with different subdomains:

```bash
# App 1 - Main application
curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -H "Content-Type: application/json" \
  -d '{"hostname": "app.armikom.com", "port": 3000, "protocol": "http"}'

# App 2 - API service  
curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -H "Content-Type: application/json" \
  -d '{"hostname": "api.armikom.com", "port": 8080, "protocol": "http"}'

# App 3 - Admin panel
curl -X POST http://localhost:8080/api/cloudflare/complete-route \
  -H "Content-Type: application/json" \
  -d '{"hostname": "admin.armikom.com", "port": 9000, "protocol": "https"}'
```

## Security Notes

- Keep your API token secure and never commit it to version control
- Use environment variables for production deployments
- The tunnel provides automatic HTTPS termination
- Consider implementing authentication/authorization for the API endpoints

## Troubleshooting

- **DNS record creation fails**: Check API token permissions and zone ID
- **Domain not accessible**: Verify tunnel is running and configured correctly
- **SSL issues**: Cloudflare automatically handles SSL for proxied records