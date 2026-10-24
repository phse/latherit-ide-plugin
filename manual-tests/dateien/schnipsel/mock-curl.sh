# Requests an den Mock aus dem Demo-Projekt (Port 8099) - in einem Terminal ausführen
curl -i http://localhost:8099/shop/orders
curl -i http://localhost:8099/shop/orders/4711
curl -i -X POST -H 'Content-Type: application/json' -d '{"customer":"C-1"}' http://localhost:8099/shop/orders
curl -i http://localhost:8099/shop/gibt-es-nicht
