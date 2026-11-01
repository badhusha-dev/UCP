import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const catalogResponseTime = new Trend('catalog_response_time_ms');

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 100, // 100 requests per second
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<250', 'p(99)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

export default function () {
  const headers = {
    'Accept': 'application/json',
    'User-Agent': 'k6-catalog-benchmarker',
  };

  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/products`, { headers });
  catalogResponseTime.add(Date.now() - start);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has products': (r) => {
      try {
        const json = r.json();
        return Array.isArray(json) && json.length >= 1;
      } catch (_) {
        return false;
      }
    },
  });

  sleep(0.1);
}
