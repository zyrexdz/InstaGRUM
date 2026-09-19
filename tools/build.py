"""Optional Windows helper for this workspace; normal Gradle commands also work."""
import os
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
env = os.environ.copy()
if os.name == 'nt':
    candidate = Path('C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot')
    if candidate.exists():
        env['JAVA_HOME'] = str(candidate)
wrapper = str(root / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
raise SystemExit(subprocess.call([wrapper, '--console=plain', '--no-daemon', *sys.argv[1:]], cwd=root, env=env))
