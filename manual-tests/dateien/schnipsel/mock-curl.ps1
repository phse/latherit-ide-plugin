# Windows (PowerShell): Requests an den Mock aus dem Demo-Projekt (Port 8099)
curl.exe -i http://localhost:8099/shop/orders
curl.exe -i http://localhost:8099/shop/orders/4711
curl.exe -i -X POST -H "Content-Type: application/json" -d "{\"customer\":\"C-1\"}" http://localhost:8099/shop/orders
