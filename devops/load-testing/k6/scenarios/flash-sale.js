import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom Enterprise Metrics
const checkoutSuccessRate = new Rate('checkout_success_rate');
const checkoutLatency = new Trend('checkout_latency_ms');
const traceHeaderCount = new Counter('w3c_trace_headers_injected');

export const options = {
  scenarios: {
    flash_sale_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 50 },   // Warm-up ramp
        { duration: '20s', target: 200 },  // Flash sale initial rush
        { duration: '30s', target: 250 },  // Sustained peak load
        { duration: '10s', target: 400 },  // Traffic spike / surge
        { duration: '10s', target: 0 },    // Graceful ramp-down
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<400', 'p(99)<800'], // 95% < 400ms, 99% < 800ms
    http_req_failed: ['rate<0.02'],                // Max 2% error rate under flash sale
    checkout_success_rate: ['rate>0.98'],          // 98%+ checkout transactions succeed
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

// Helper to generate 32-char hex trace ID and 16-char span ID (W3C TraceContext)
function generateW3CTraceparent() {
  const hex = '0123456789abcdef';
  let traceId = '';
  for (let i = 0; i < 32; i++) traceId += hex[Math.floor(Math.random() * 16)];
  let spanId = '';
  for (let i = 0; i < 16; i++) spanId += hex[Math.floor(Math.random() * 16)];
  return `00-${traceId}-${spanId}-01`;
}

export default function () {
  const traceparent = generateW3CTraceparent();
  traceHeaderCount.add(1);

  const baseHeaders = {
    'Content-Type': 'application/json',
    'traceparent': traceparent,
    'X-Client-Platform': 'k6-load-agent',
  };

  let token = null;

  // Step 1: User Authentication & Token Acquisition
  group('01_User_Authentication', () => {
    const loginPayload = JSON.stringify({
      email: 'admin@ecommerce.com',
      password: 'password123',
    });

    const loginRes = http.post(`${BASE_URL}/api/users/login`, loginPayload, {
      headers: baseHeaders,
      tags: { name: 'POST_Login' },
    });

    check(loginRes, {
      'login status is 200': (r) => r.status === 200,
      'token returned': (r) => {
        try {
          const json = r.json();
          if (json && json.token) {
            token = json.token;
            return true;
          }
        } catch (_) {}
        return false;
      },
    });
  });

  sleep(0.5);

  // Step 2: Product Catalog Browsing & Search
  group('02_Browse_Product_Catalog', () => {
    const catalogRes = http.get(`${BASE_URL}/api/products`, {
      headers: baseHeaders,
      tags: { name: 'GET_Products' },
    });

    check(catalogRes, {
      'catalog status is 200': (r) => r.status === 200,
      'products list not empty': (r) => {
        try {
          const arr = r.json();
          return Array.isArray(arr) && arr.length > 0;
        } catch (_) {
          return false;
        }
      },
    });

    // Sub-item query simulating quick-view inspection
    const productId = Math.floor(Math.random() * 3) + 1;
    const itemRes = http.get(`${BASE_URL}/api/products`, {
      headers: baseHeaders,
      tags: { name: 'GET_Product_Detail' },
    });

    check(itemRes, {
      'item inspection is 200': (r) => r.status === 200,
    });
  });

  sleep(0.8);

  // Step 3: Checkout Processing with W3C Distributed Trace Propagation
  if (token) {
    group('03_High_Concurrency_Checkout', () => {
      const authHeaders = {
        ...baseHeaders,
        Authorization: `Bearer ${token}`,
      };

      const checkoutPayload = JSON.stringify({
        userId: 4,
        items: [
          {
            productId: 1,
            name: 'Wireless Noise-Canceling Headphones',
            price: 99.99,
            quantity: 1,
          },
        ],
      });

      const startTime = Date.now();
      const checkoutRes = http.post(`${BASE_URL}/api/orders/checkout`, checkoutPayload, {
        headers: authHeaders,
        tags: { name: 'POST_Checkout' },
      });
      const duration = Date.now() - startTime;
      checkoutLatency.add(duration);

      const success = check(checkoutRes, {
        'checkout status is 200': (r) => r.status === 200,
        'order status is PLACED': (r) => {
          try {
            const body = r.json();
            return body && (body.status === 'PLACED' || body.status === 'PENDING_INVENTORY');
          } catch (_) {
            return false;
          }
        },
      });

      checkoutSuccessRate.add(success);
    });
  }

  sleep(1.0);
}
