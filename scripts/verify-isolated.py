#!/usr/bin/env python3
"""Synthetic API journey and restore proof; never connects to an existing database."""
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]

def backend_jar():
    """JAR executável do backend, independente da versão no POM (ignora o *.original do Boot)."""
    candidates = sorted(
        path for path in (ROOT / 'backend/target').glob('assinaflow-*.jar')
        if not path.name.endswith('.original')
    )
    if not candidates:
        raise RuntimeError('backend jar not found; run mvn package first')
    return candidates[-1]

NAME = 'assinaflow-proof-' + uuid.uuid4().hex[:12]
# Credenciais sinteticas geradas por execucao: nunca fixas no codigo, nunca reutilizaveis.
DB_PASSWORD = secrets.token_urlsafe(24)
JWT_SECRET = secrets.token_urlsafe(48)
TOKEN = None


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def request(path, payload=None, expected=200, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=json.dumps(payload).encode() if payload is not None else None,
                                 headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            code, body = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, body = error.code, error.read()
    assert code == expected, f'{path}: expected {expected}, received {code}'
    return json.loads(body) if body else None


def start(database, log, recovery=False):
    env = dict(os.environ, DB_URL=f'jdbc:postgresql://127.0.0.1:{DB_PORT}/{database}',
               DB_USERNAME='proof', DB_PASSWORD=DB_PASSWORD, SERVER_PORT=str(PORT),
               APP_SCHEDULER_ENABLED='false', APP_PAYMENTS_ASYNC_ENABLED='false',
               APP_SECURITY_ENABLED='true', APP_PAYMENTS_GATEWAY='simulated', APP_BILLING_MODE='demo',
               JWT_SECRET=JWT_SECRET)
    # Override inherited Spring configuration that could redirect a test process.
    args = [f'--spring.datasource.url={env["DB_URL"]}', '--spring.datasource.username=proof',
            f'--spring.datasource.password={DB_PASSWORD}', '--spring.profiles.active=',
            '--spring.cache.type=simple', '--spring.config.location=classpath:/application.yml',
            '--management.health.redis.enabled=false', '--management.health.rabbit.enabled=false',
            '--spring.rabbitmq.dynamic=false', '--app.notifications.sender=logging', '--app.scheduler.enabled=false',
            '--app.payments.async.enabled=false', '--app.security.enabled=true',
            '--app.payments.gateway=simulated', '--app.billing.mode=demo', f'--server.port={PORT}',
            f'--app.payments.recovery.enabled={str(recovery).lower()}', '--app.payments.recovery.delay-ms=250']
    process = subprocess.Popen(['java', '-jar', str(backend_jar()), *args],
                               env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError('API exited; inspect isolated log')
            try:
                request('/actuator/health')
                return process
            except (OSError, AssertionError):
                time.sleep(1)
        raise RuntimeError('Isolated API startup timeout')
    except BaseException:
        stop(process)
        raise


def stop(process):
    if process and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


if __name__ == '__main__':
    PORT = free_port()
    BASE = f'http://127.0.0.1:{PORT}'
    process = None
    created = False
    with tempfile.TemporaryDirectory(prefix='assinaflow-proof-') as temp:
        try:
            run('docker', 'run', '--rm', '-d', '--name', NAME, '-p', '127.0.0.1::5432',
                '-e', 'POSTGRES_DB=proof', '-e', 'POSTGRES_USER=proof',
                '-e', f'POSTGRES_PASSWORD={DB_PASSWORD}', os.environ.get('TEST_POSTGRES_IMAGE', 'postgres:16-alpine'))
            created = True
            DB_PORT = run('docker', 'port', NAME, '5432/tcp').rsplit(':', 1)[1]
            for _ in range(60):
                if subprocess.run(['docker', 'exec', NAME, 'pg_isready', '-U', 'proof'],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                    break
                time.sleep(1)
            with open(Path(temp) / 'api.log', 'w') as log:
                process = start('proof', log)
                email = f'{NAME}@example.test'
                registration = {'nome': 'Synthetic proof', 'email': email, 'senha': 'synthetic-password'}
                request('/api/v1/auth/register', registration, 201)
                login = request('/api/v1/auth/login', {'email': email, 'senha': registration['senha']})
                TOKEN = login['accessToken']
                user = login['user']['id']
                path = f'/api/v1/users/{user}/subscriptions'
                subscription = request(path, {'plano': 'BASICO'}, 201, TOKEN)
                request(path + '/active', token=TOKEN)
                request(f'/api/v1/users/{uuid.uuid4()}/subscriptions/active', expected=403, token=TOKEN)
                request('/api/v1/admin/users', expected=403, token=TOKEN)
                request(path + '/active', expected=401)
                print('PASS: HTTP register/login, own subscription, cross-user 403, admin 403, anonymous 401')
                if '--frontend' in os.sys.argv:
                    subprocess.run(['npm', 'run', 'test:e2e', '--', '--retries=0'], cwd=ROOT / 'frontend',
                                   env=dict(os.environ, E2E_API_URL=BASE, CI='true'), check=True)
                stop(process)
                process = None
                # Controlled persisted fixture: remote approval, response lost, process restarted.
                # This only touches the fresh container created above, never an existing installation.
                cycle = run('docker', 'exec', NAME, 'psql', '-U', 'proof', '-d', 'proof', '-At', '-c',
                            "SELECT ((now() AT TIME ZONE 'UTC')::date)::text")
                def sql(statement, database='proof'):
                    return run('docker', 'exec', NAME, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'proof', '-d', database, '-At', '-c', statement)
                sub_id = subscription['id']
                sql(f"UPDATE subscriptions SET start_date=date '{cycle}'-interval '1 month', expiration_date=date '{cycle}', version=version+1 WHERE id='{sub_id}'")
                version = int(sql(f"SELECT version FROM subscriptions WHERE id='{sub_id}'"))
                obligation = str(uuid.uuid4())
                key = 'obligation-' + obligation
                context = json.dumps(dict(kind='RENEWAL', subscriptionId=sub_id, plan='BASICO', start=cycle,
                                          cycle=cycle, version=version, creditCents=0, priceCents=1990))
                sql(f"""INSERT INTO payment_obligations(id,business_key,user_id,subscription_id,amount_cents,currency,
                        idempotency_key,context,kind,cycle_date,status,retention_until,execution_count)
                    VALUES ('{obligation}','renewal|{sub_id}|{cycle}','{user}','{sub_id}',1990,'BRL','{key}',
                        '{context}'::jsonb,'RENEWAL','{cycle}','PREPARED',now()+interval '48 hours',0)""")
                sql(f"""INSERT INTO simulated_provider_payments(idempotency_key,provider_reference,obligation_id,user_id,
                        amount_cents,currency,status,effects) VALUES ('{key}','sim-{obligation}','{obligation}','{user}',1990,'BRL','APPROVED',1)""")
                sql(f"""UPDATE payment_obligations SET status='UNKNOWN',execution_count=1 WHERE id='{obligation}';
                    INSERT INTO payment_executions(id,obligation_id,sequence,operation,owner,started_at)
                        VALUES ('{uuid.uuid4()}','{obligation}',1,'CHARGE','{uuid.uuid4()}',now())""")
                process = start('proof', log, recovery=True)
                deadline = time.monotonic() + 30
                while True:
                    payment = request(f'/api/v1/users/{user}/payments/{obligation}', token=TOKEN)
                    if payment['status'] == 'APPROVED' and payment['settled']:
                        break
                    assert time.monotonic() < deadline, 'Durable recovery did not settle the obligation'
                    time.sleep(0.25)
                subscription = request(path + '/active', token=TOKEN)
                expected_expiration = sql(f"SELECT (date '{cycle}' + interval '1 month')::date")
                assert subscription['dataExpiracao'] == expected_expiration
                assert sql(f"SELECT effects FROM simulated_provider_payments WHERE obligation_id='{obligation}'") == '1'
                assert sql(f"SELECT count(*) FROM payment_executions WHERE obligation_id='{obligation}' AND operation='CHARGE'") == '1'
                assert int(sql(f"SELECT count(*) FROM payment_executions WHERE obligation_id='{obligation}' AND operation='QUERY'")) >= 1
                print('PASS: persisted intent, lost approval response, JVM restart, provider lookup, renewal and one financial effect')
                stop(process)
                process = None
                run('docker', 'exec', NAME, 'pg_dump', '-U', 'proof', '-d', 'proof', '-Fc', '-f', '/var/lib/postgresql/proof.dump')
                run('docker', 'exec', NAME, 'createdb', '-U', 'proof', 'restored')
                run('docker', 'exec', NAME, 'pg_restore', '--exit-on-error', '-U', 'proof', '-d', 'restored', '/var/lib/postgresql/proof.dump')
                process = start('restored', log)
                restored = request(path + '/active', token=TOKEN)
                assert restored['id'] == subscription['id']
                assert restored['dataExpiracao'] == subscription['dataExpiracao']
                request(f'/api/v1/users/{user}/payments/{obligation}', token=TOKEN)
                assert sql(f"SELECT effects FROM simulated_provider_payments WHERE obligation_id='{obligation}'", 'restored') == '1'
                request(path + '/cancel', {}, token=TOKEN)
                print('PASS: dump restored to empty database, application restarted, authenticated API read and cancel')
        except Exception:
            log_path = Path(temp) / 'api.log'
            if log_path.exists():
                # Full logs stay local; do not expose request bodies or tokens.
                print('Isolated API failed; inspect configuration and repeat the gate.')
            raise
        finally:
            stop(process)
            if created:
                subprocess.run(['docker', 'stop', NAME], stdout=subprocess.DEVNULL, check=False)
