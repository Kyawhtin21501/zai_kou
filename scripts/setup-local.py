#!/usr/bin/env python3
"""Generate local credentials without printing them, and provision a limited DB login."""
import os
import sys
import secrets
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
env_file = root / '.env'
if not env_file.exists():
    env_file.write_text((root / '.env.example').read_text())
text = env_file.read_text()
values = dict(line.split('=', 1) for line in text.splitlines() if '=' in line and not line.lstrip().startswith('#'))
for key in ('APP_ADMIN_PASSWORD', 'APP_DB_PASSWORD'):
    if not values.get(key):
        value = secrets.token_hex(24)
        lines = [line for line in text.splitlines() if not line.startswith(key + '=')]
        text = '\n'.join(lines) + f'\n{key}={value}\n'
        values[key] = value
if 'APP_ADMIN_USERNAME' not in values:
    text += 'APP_ADMIN_USERNAME=admin\n'
env_file.write_text(text)
os.chmod(env_file, 0o600)
if '--env-only' in sys.argv:
    print('Local credentials saved in .env. No credentials were printed.')
    sys.exit(0)
subprocess.run(['docker', 'compose', 'up', '-d', '--wait', 'db'], cwd=root, check=True)
# Quote SQL literals; credentials are sent on stdin, never in command arguments or logs.
password = values['APP_DB_PASSWORD'].replace("'", "''")
sql = """DO $$ BEGIN
IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'zaikou_app') THEN
CREATE ROLE zaikou_app LOGIN;
END IF;
END $$;
""" + f"ALTER ROLE zaikou_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '{password}';\n"
subprocess.run(['docker', 'compose', 'exec', '-T', 'db', 'sh', '-c',
                'psql -v ON_ERROR_STOP=1 --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'],
               cwd=root, input=sql, text=True, check=True, stdout=subprocess.DEVNULL)
print('Local credentials saved in .env; limited database login is ready. No credentials were printed.')
