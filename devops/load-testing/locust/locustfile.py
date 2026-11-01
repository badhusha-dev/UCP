import os
import uuid
import random
from locust import HttpUser, task, between, events

class BaseEcommerceUser(HttpUser):
    wait_time = between(0.5, 1.5)
    abstract = True

    def generate_traceparent(self):
        trace_id = uuid.uuid4().hex
        span_id = uuid.uuid4().hex[:16]
        return f"00-{trace_id}-{span_id}-01"

    def login(self, email, password):
        payload = {"email": email, "password": password}
        headers = {
            "Content-Type": "application/json",
            "traceparent": self.generate_traceparent()
        }
        with self.client.post("/api/users/login", json=payload, headers=headers, catch_response=True) as response:
            if response.status_code == 200:
                data = response.json()
                self.token = data.get("token")
                response.success()
            else:
                response.failure(f"Login failed with status {response.status_code}")


class FlashSaleShopper(BaseEcommerceUser):
    weight = 4

    def on_start(self):
        self.token = None
        self.user_id = 4
        self.login("shopper@ecommerce.com", "password123")

    @task(6)
    def browse_catalog(self):
        headers = {
            "traceparent": self.generate_traceparent(),
            "Accept": "application/json"
        }
        with self.client.get("/api/products", headers=headers, catch_response=True) as res:
            if res.status_code == 200 and len(res.json()) > 0:
                res.success()
            else:
                res.failure("Failed to fetch product catalog")

    @task(3)
    def inspect_product_spec(self):
        product_id = random.choice([1, 2, 3])
        headers = {
            "traceparent": self.generate_traceparent(),
            "Accept": "application/json"
        }
        self.client.get(f"/api/products", headers=headers, name="/api/products/inspect")

    @task(2)
    def execute_flash_checkout(self):
        if not self.token:
            return

        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.token}",
            "traceparent": self.generate_traceparent()
        }
        payload = {
            "userId": self.user_id,
            "items": [
                {
                    "productId": random.choice([1, 2]),
                    "name": "High-Performance Electronic",
                    "price": 99.99,
                    "quantity": 1
                }
            ]
        }
        with self.client.post("/api/orders/checkout", json=payload, headers=headers, catch_response=True) as res:
            if res.status_code == 200:
                res.success()
            else:
                res.failure(f"Checkout failed: {res.status_code} - {res.text}")


class PlatformAdminUser(BaseEcommerceUser):
    weight = 1

    def on_start(self):
        self.token = None
        self.login("admin@ecommerce.com", "password123")

    @task(3)
    def view_all_orders(self):
        if not self.token:
            return
        headers = {
            "Authorization": f"Bearer {self.token}",
            "traceparent": self.generate_traceparent()
        }
        self.client.get("/api/orders", headers=headers)

    @task(1)
    def check_system_telemetry(self):
        self.client.get("/actuator/health", name="/actuator/health")
