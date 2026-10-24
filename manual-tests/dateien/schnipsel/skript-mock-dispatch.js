// Dispatch „Per Skript“: liefert den Namen der Response
context.kunde = request.query.kunde ?? 'unbekannt'
if (request.headers['x-fehler']) return 'Fehler'
return request.query.neu ? 'Angelegt' : 'Standard'
