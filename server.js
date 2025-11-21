const http = require("http");
const fs = require("fs");
const path = require("path");
const os = require("os");

const PORT = 8000;
// Serve files from the current directory (server.js is inside `pwa-app`)
const APP_DIR = path.resolve(__dirname);

// Function to get local IP address
function getLocalIPAddress() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      // Skip internal and non-IPv4 addresses
      if (iface.family === "IPv4" && !iface.internal) {
        return iface.address;
      }
    }
  }
  return "localhost";
}

// MIME types
const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript",
  ".css": "text/css",
  ".json": "application/json",
  ".webmanifest": "application/manifest+json",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
};

// Create server
const server = http.createServer((req, res) => {
  // Remove query string
  let filePath = req.url.split("?")[0];

  // Default to index.html for root request
  if (filePath === "/") {
    filePath = "/index.html";
  }

  // Construct full file path
  const fullPath = path.join(APP_DIR, filePath);

  // Prevent directory traversal attacks
  if (!fullPath.startsWith(APP_DIR)) {
    res.writeHead(403);
    res.end("Forbidden");
    return;
  }

  // Check if file exists
  fs.stat(fullPath, (err, stats) => {
    if (err) {
      if (err.code === "ENOENT") {
        res.writeHead(404, { "Content-Type": "text/html" });
        res.end("<h1>404 - File Not Found</h1>");
      } else {
        res.writeHead(500);
        res.end("Server Error");
      }
      return;
    }

    // Check if it's a directory
    if (stats.isDirectory()) {
      const indexPath = path.join(fullPath, "index.html");
      fs.stat(indexPath, (err) => {
        if (!err) {
          serveFile(indexPath, res);
        } else {
          res.writeHead(403);
          res.end("Forbidden");
        }
      });
      return;
    }

    // Serve the file
    serveFile(fullPath, res);
  });
});

function serveFile(filePath, res) {
  const ext = path.extname(filePath).toLowerCase();
  const contentType = mimeTypes[ext] || "application/octet-stream";

  // Add CORS headers
  res.setHeader("Access-Control-Allow-Origin", "*");

  // Add Service Worker headers
  if (filePath.endsWith("service-worker.js")) {
    res.setHeader("Service-Worker-Allowed", "/");
  }

  fs.readFile(filePath, (err, content) => {
    if (err) {
      res.writeHead(500);
      res.end("Server Error");
      return;
    }

    res.writeHead(200, { "Content-Type": contentType });
    res.end(content);
  });
}

// Get IP address
const localIP = getLocalIPAddress();

// Start server
server.listen(PORT, "0.0.0.0", () => {
  console.log(
    "\n╔════════════════════════════════════════════════════════════╗"
  );
  console.log("║          🚀 SCADA Mobile PWA - Development Server          ║");
  console.log("╠════════════════════════════════════════════════════════════╣");
  console.log(
    `║  🌐 Localhost: http://localhost:${PORT}/                       ║`
  );
  console.log(
    `║  📱 Network:   http://${localIP}:${PORT}/                         ║`
  );
  console.log("║                                                            ║");
  console.log(
    "║  ✅ Откройте URL выше в браузере                            ║"
  );
  console.log("║  📲 Для просмотра с телефона:                              ║");
  console.log(
    `║     1. Введите адрес: http://${localIP}:${PORT}/             ║`
  );
  console.log("║     2. Оба устройства должны быть в одной сети             ║");
  console.log("║                                                            ║");
  console.log("║  🔴 Для остановки: Ctrl+C                                  ║");
  console.log(
    "╚════════════════════════════════════════════════════════════╝\n"
  );
});

// Handle graceful shutdown
process.on("SIGTERM", () => {
  console.log("\n🛑 Сервер остановлен");
  process.exit(0);
});

process.on("SIGINT", () => {
  console.log("\n🛑 Сервер остановлен");
  process.exit(0);
});
