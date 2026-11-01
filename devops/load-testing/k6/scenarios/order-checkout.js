import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const orderSuccess = new Rate('order_success_rate');
const orderDuration = new Trend('order_duration_ms');

export const options = {
  vus: 30,
  duration: '20s',
  thresholds: {
    http_req_duration: ['p(95)<400'],
    order_success_rate: ['rate>0.95'],
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

export function setup() {
  // Pre-authenticate a master session token
  const loginRes = http.post(
    `${BASE_URL}/api/users/login`,
    JSON.stringify({ email: 'admin@ecommerce.com', password: 'password123' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const data = loginRes.json();
  return { token: data.token };
}

export default function (data) {
  const traceId = '4bf92f3577b34da6' + Math.random().toString(16).substring(2, 18);
  const spanId = Math.random().toString(16).substring(2, 18);
  const traceparent = `00-${traceId}-${spanId}-01`;

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
    'traceparent': traceparent,
  };

  const payload = JSON.stringify({
    userId: 4,
    items: [
      {
        productId: 2,
        name: 'Mechanical Keyboard',
        price: 79.99,
        quantity: 1,
      },
    ],
  });

  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/orders/checkout`, payload, { headers });
  orderDuration.add(Date.now() - start);

  const passed = check(res, {
    'checkout responded 200': (r) => r.status === 200,
    'order created with ID': (r) => {
      try {
        const body = r.json();
        return body && body.id !== undefined;
      } catch (_) {
        return false;
      }
    },
  });

  orderSuccess.add(passed);
  sleep(0.5);
}
