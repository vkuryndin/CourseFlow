#!/usr/bin/env python3
"""Optional Linux smoke test: actual Nginx with the production header rules, no DB.
Requires Python 3 and nginx on PATH. Uses ephemeral local ports and temporary files.
"""
import http.client
import json
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Echo(BaseHTTPRequestHandler):
    def do_GET(self):
        data = json.dumps({k.lower(): self.headers.get_all(k)
                           for k in self.headers.keys()}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *_args):
        pass


def main():
    nginx = shutil.which('nginx')
    if not nginx:
        raise SystemExit('nginx is required')
    config = (Path(__file__).resolve().parents[1] / 'nginx.conf').read_text()
    with ThreadingHTTPServer(('127.0.0.1', 0), Echo) as backend:
        worker = threading.Thread(target=backend.serve_forever, daemon=True)
        worker.start()
        try:
            with tempfile.TemporaryDirectory(prefix='courseflow-proxy-') as tmp:
                with socket.socket() as probe:
                    probe.bind(('127.0.0.1', 0))
                    port = probe.getsockname()[1]
                # Change only deployment-specific listen/upstream endpoints.
                config = config.replace('listen 80;', f'listen 127.0.0.1:{port};')
                config = config.replace('http://backend:8080',
                                        f'http://127.0.0.1:{backend.server_port}')
                conf = Path(tmp) / 'nginx.conf'
                conf.write_text('worker_processes 1;\npid nginx.pid;\n'
                                'error_log stderr;\nevents {}\n'
                                'http { access_log off;\n' + config + '\n}\n')
                cmd = [nginx, '-p', tmp + '/', '-c', str(conf)]
                subprocess.run(cmd + ['-t'], check=True)
                proc = subprocess.Popen(cmd + ['-g', 'daemon off;'])
                try:
                    for _ in range(50):
                        try:
                            with socket.create_connection(('127.0.0.1', port), timeout=.2):
                                break
                        except OSError:
                            if proc.poll() is not None:
                                raise RuntimeError('Nginx exited')
                            time.sleep(.1)
                    else:
                        raise RuntimeError('Nginx did not start')
                    for path in ('/api/auth/login', '/actuator/health', '/actuator/metrics'):
                        for source in ('127.0.0.2', '127.0.0.3'):
                            conn = http.client.HTTPConnection('127.0.0.1', port,
                                                               timeout=5, source_address=(source, 0))
                            try:
                                conn.putrequest('GET', path)
                                conn.putheader('Authorization', 'Bearer smoke-token')
                                conn.putheader('X-Forwarded-For', '192.0.2.111')
                                conn.putheader('X-Forwarded-For', '192.0.2.222')
                                conn.putheader('X-Real-IP', '198.51.100.1')
                                conn.putheader('X-Forwarded-Proto', 'https')
                                conn.putheader('Forwarded', 'for=203.0.113.1')
                                for suffix in ('Host', 'Port', 'Prefix', 'By', 'Ssl'):
                                    conn.putheader('X-Forwarded-' + suffix, 'forged')
                                conn.endheaders()
                                response = conn.getresponse()
                                assert response.status == 200, response.status
                                headers = json.loads(response.read())
                                assert headers['x-forwarded-for'] == [source], headers
                                assert headers['x-real-ip'] == [source], headers
                                assert headers['x-forwarded-proto'] == ['http'], headers
                                assert headers['authorization'] == ['Bearer smoke-token'], headers
                                for name in ('forwarded', 'x-forwarded-host', 'x-forwarded-port',
                                             'x-forwarded-prefix', 'x-forwarded-by', 'x-forwarded-ssl'):
                                    assert name not in headers, headers
                            finally:
                                conn.close()
                    print('PASS: six real Nginx requests; IP overwrite, spoofed headers removed.')
                finally:
                    proc.terminate()
                    try:
                        proc.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        proc.kill()
                        proc.wait()
        finally:
            backend.shutdown()
            worker.join(timeout=5)


if __name__ == '__main__':
    main()
