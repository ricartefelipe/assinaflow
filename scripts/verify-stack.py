#!/usr/bin/env python3
"""Run built demo images in an isolated Compose project and verify the browser journey."""
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def main():
    project = 'assinaflow-stack-proof-' + uuid.uuid4().hex[:12]
    api_port, web_port = free_port(), free_port()
    with tempfile.TemporaryDirectory(prefix=project) as directory:
        override = Path(directory) / 'compose.yml'
        override.write_text(f'''services:
  postgres:
    ports: !reset []
  redis:
    ports: !reset []
  rabbitmq:
    ports: !reset []
  app:
    image: assinaflow-validation-stripe-backend:local
    ports: !override ["127.0.0.1:{api_port}:8080"]
    environment:
      APP_BILLING_MODE: demo
      APP_PAYMENTS_GATEWAY: simulated
      APP_NOTIFICATIONS_SENDER: logging
      JWT_SECRET: synthetic-stack-proof-only-secret-at-least32
      APP_CORS_ORIGINS: http://127.0.0.1:{web_port}
  web:
    image: assinaflow-validation-stripe-frontend:local
    ports: !override ["127.0.0.1:{web_port}:8080"]
''')
        compose = ['docker', 'compose', '-p', project, '-f', str(ROOT / 'docker-compose.yml'), '-f', str(override)]
        try:
            subprocess.run(compose + ['up', '-d', '--no-build'], check=True, cwd=ROOT)
            deadline = time.monotonic() + 120
            while True:
                try:
                    with urllib.request.urlopen(f'http://127.0.0.1:{api_port}/actuator/health', timeout=2) as response:
                        if response.status == 200:
                            break
                except OSError:
                    pass
                if time.monotonic() >= deadline:
                    raise RuntimeError('Isolated stack did not become healthy')
                time.sleep(1)
            env = dict(os.environ, E2E_EXTERNAL_WEB='true', E2E_BASE_URL=f'http://127.0.0.1:{web_port}', E2E_API_URL=f'http://127.0.0.1:{api_port}')
            subprocess.run(['npx', 'playwright', 'test', '--retries=0'], check=True, cwd=ROOT / 'frontend', env=env)
            print('PASS: isolated PostgreSQL, Redis, RabbitMQ, API and Nginx; browser journey without retries')
        finally:
            subprocess.run(compose + ['down', '-v', '--remove-orphans'], check=True, cwd=ROOT)


if __name__ == '__main__':
    main()
