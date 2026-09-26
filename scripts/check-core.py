"""Run deterministic core checks using only the JDK; works on Windows and Linux."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
out = root / 'build' / 'core-checks'
out.mkdir(parents=True, exist_ok=True)
source = root / 'app/src/main/java/de/xianmu/arotation'
subprocess.run([
    'javac', '-encoding', 'UTF-8', '-d', str(out),
    str(source / 'rotation/RotationPolicy.java'),
    str(source / 'overlay/Placement.java'),
    str(root / 'tests/CoreChecks.java'),
], check=True)
subprocess.run(['java', '-cp', str(out), 'CoreChecks'], check=True)
