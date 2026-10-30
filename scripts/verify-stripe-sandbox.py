#!/usr/bin/env python3
"""Acceptance against an explicitly configured Stripe sandbox and local synthetic account."""
import argparse


class Inconclusive(Exception):
    """Sem prova positiva nem negativa (janela de aceitação ou busca eventual): não reenviar."""

import base64
import json
import os
import sys
import subprocess
import re
import uuid
import time
import urllib.error
import urllib.parse
import urllib.request


def call(url, data=None, token=None, stripe=False):
    headers = {}
    if stripe:
        key = os.environ.get('STRIPE_SECRET_KEY', '')
        if not key.startswith('sk_test_'):
            raise RuntimeError('Only a Stripe test secret key is allowed')
        headers['Authorization'] = 'Basic ' + base64.b64encode((key + ':').encode()).decode()
        headers['Stripe-Version'] = '2025-02-24.acacia'
    elif token:
        headers['Authorization'] = 'Bearer ' + token
    body = None
    if data is not None:
        headers['Content-Type'] = 'application/json'
        body = json.dumps(data).encode()
    request = urllib.request.Request(url, data=body, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            content = response.read()
            return response.status, json.loads(content) if content else None
    except urllib.error.HTTPError as error:
        # Never print provider bodies, client secrets, headers or credentials.
        return error.code, None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default=os.environ.get('SANDBOX_API_URL', 'http://127.0.0.1:8080'))
    parser.add_argument('--phase', choices=['approve', 'decline', 'action', 'prepare-renewal', 'renewal', 'replay', 'cancel'], required=True)
    parser.add_argument('--wait-seconds', type=int, default=180)
    args = parser.parse_args()
    url = urllib.parse.urlsplit(args.base)
    if url.hostname not in ['127.0.0.1', 'localhost'] or url.scheme != 'http' or url.username or url.password:
        raise RuntimeError('Use only the isolated local application')
    required = ['STRIPE_SECRET_KEY', 'SANDBOX_EMAIL', 'SANDBOX_PASSWORD']
    if any(not os.environ.get(key) for key in required):
        raise RuntimeError('Required environment variable names: ' + ', '.join(required))
    if not os.environ['STRIPE_SECRET_KEY'].startswith('sk_test_'):
        raise RuntimeError('Only a Stripe test secret key is allowed')
    if not os.environ['SANDBOX_EMAIL'].endswith('@example.test'):
        raise RuntimeError('Use an isolated synthetic account ending in @example.test')
    status, config = call(args.base + '/api/v1/billing/config')
    assert status == 200 and config['gateway'] == 'stripe' and config['mode'] == 'sandbox'
    status, login = call(args.base + '/api/v1/auth/login', {'email': os.environ['SANDBOX_EMAIL'], 'senha': os.environ['SANDBOX_PASSWORD']})
    assert status == 200
    token, user = login['accessToken'], login['user']['id']
    path = args.base + '/api/v1/users/' + user
    assert call(path + '/billing/status', token=token)[1]['ready'], 'Complete hosted card setup in the portal first'
    if args.phase in ['approve', 'decline', 'action']:
        status, _ = call(path + '/subscriptions', {'plano': 'BASICO'}, token)
        assert status == {'approve': 201, 'decline': 400, 'action': 503}[args.phase]
        if args.phase == 'decline':
            assert call(path + '/subscriptions/active', token=token)[0] == 204
            print('PASS: sandbox decline; no active subscription')
            return
        if args.phase == 'action':
            print('Complete additional authentication inside the provider component in the local account portal.')
    if args.phase == 'prepare-renewal':
        container = os.environ.get('SANDBOX_DB_CONTAINER', '')
        assert container.startswith('assinaflow-stripe-proof-')
        info = json.loads(subprocess.check_output(['docker', 'inspect', container], text=True))[0]
        assert info['Config']['Labels'].get('assinaflow.proof') == 'stripe-sandbox'
        assert info['Mounts'] and all(m['Type'] == 'volume' and re.fullmatch(r'[0-9a-f]{64}', m['Name']) for m in info['Mounts'])
        rows = call(path + '/payments', token=token)[1]
        assert rows and all(p['status'] == 'APPROVED' and p['settled'] for p in rows)
        user = str(uuid.UUID(user))
        sql = ("UPDATE subscriptions SET start_date=(current_date-interval '1 month')::date, expiration_date=current_date, "
               "next_renewal_attempt_at=now(), renewal_in_flight_until=NULL, renewal_failures=0, version=version+1 "
               "WHERE user_id='" + user + "' AND status='ATIVA' AND auto_renew=true")
        output = subprocess.check_output(['docker', 'exec', container, 'psql', '-U', 'proof', '-d', 'stripeproof', '-v', 'ON_ERROR_STOP=1', '-c', sql], text=True)
        assert 'UPDATE 1' in output
        print('PASS: due-cycle fixture prepared only in the labeled disposable sandbox database')
        return
    if args.phase == 'renewal':
        status, subscription = call(path + '/subscriptions/active', token=token)
        assert status == 200 and subscription and subscription['autoRenew'], 'Use the approved synthetic subscription and prepare-renewal fixture first'

    deadline = time.monotonic() + args.wait_seconds
    payment = None
    while time.monotonic() < deadline:
        status, rows = call(path + '/payments', token=token)
        assert status == 200
        if rows and rows[0]['status'] == 'APPROVED' and rows[0]['settled'] and (args.phase != 'renewal' or rows[0]['subscription_id'] is not None):
            payment = rows[0]
            if args.phase != 'renewal' or len(rows) >= 2:
                break
        time.sleep(2)
    if not (payment and payment['status'] == 'APPROVED' and (args.phase != 'renewal' or len(rows) >= 2)):
        raise Inconclusive('No confirmed approval within the acceptance window')
    query = "metadata['obligation_id']:'" + payment['id'] + "'"
    status, result = call('https://api.stripe.com/v1/payment_intents/search?' + urllib.parse.urlencode({'query': query, 'limit': 2}), stripe=True)
    if not (status == 200 and not result['has_more'] and len(result['data']) == 1):
        raise Inconclusive('Search is eventually consistent; absence is not proof of no debit')
    intent = result['data'][0]
    assert intent['livemode'] is False and intent['status'] == 'succeeded'
    assert intent['metadata']['user_id'] == user and intent['currency'] == 'brl' and intent['amount'] == payment['amount_cents']
    customer = intent['customer']
    def intent_ids():
        code, data = call('https://api.stripe.com/v1/payment_intents?' + urllib.parse.urlencode({'customer': customer, 'limit': 100}), stripe=True)
        assert code == 200 and not data['has_more']
        assert all(i['livemode'] is False for i in data['data'])
        return {i['id'] for i in data['data']}
    before = intent_ids()
    for _ in range(10):
        assert call(path + '/payments/' + payment['id'] + '/stripe/reconcile', {}, token)[0] == 200
    assert intent_ids() == before, 'Unexpected additional PaymentIntent after replay'
    if args.phase == 'cancel':
        code, canceled = call(path + '/subscriptions/cancel', {}, token)
        assert code == 200 and canceled['autoRenew'] is False and canceled['status'] == 'CANCELAMENTO_AGENDADO'
        assert intent_ids() == before
    print('PASS: Stripe sandbox ' + args.phase + '; provider correlation, server approval and ten replays without another PaymentIntent')


if __name__ == '__main__':
    # Códigos de saída: 0 aprovado; 1 falha; 2 configuração (credencial ausente ou não de teste);
    # 3 inconclusivo (sem prova em nenhum sentido: consultar de novo, nunca reenviar).
    try:
        main()
    except RuntimeError as error:
        print('CONFIG: ' + str(error), file=sys.stderr)
        sys.exit(2)
    except Inconclusive as error:
        print('INCONCLUSIVE: ' + str(error) + '; do not resend, query again later', file=sys.stderr)
        sys.exit(3)
    except Exception:
        print('FAIL: sandbox acceptance not completed; check configuration and the selected phase without logging secrets', file=sys.stderr)
        sys.exit(1)
