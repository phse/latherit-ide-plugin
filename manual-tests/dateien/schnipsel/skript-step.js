// Skript-Step nach dem Step „Lesen“ (REST GET /api/orders/4711)
const order = steps['Lesen'].json()
log.info('Status', order.status, 'Summe', order.total)
assert(steps['Lesen'].status === 200, 'Lesen fehlgeschlagen')
testCase.setPropertyValue('kunde', order.customer)
return order.id
