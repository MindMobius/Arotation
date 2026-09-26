"""Build and verify a signed release without copying signing secrets into the checkout."""
from pathlib import Path
import argparse
import json
import os
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--signing-config', type=Path, required=True)
parser.add_argument('--offline', action='store_true')
parser.add_argument('--tests', action='store_true', help='Also build release-signed instrumentation')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
config_path = args.signing_config.resolve()
if config_path.is_relative_to(root):
    raise SystemExit('Keep the signing configuration outside the source checkout.')
config = json.loads(config_path.read_text(encoding='utf-8-sig'))
keystore = Path(config['storeFile']).expanduser()
if not keystore.is_absolute():
    keystore = config_path.parent / keystore
if not keystore.is_file():
    raise SystemExit('Signing keystore was not found.')
variables = {
    'AROTATION_KEYSTORE': str(keystore.resolve()),
    'AROTATION_STORE_PASSWORD': config['storePassword'],
    'AROTATION_KEY_ALIAS': config['keyAlias'],
    'AROTATION_KEY_PASSWORD': config.get('keyPassword', config['storePassword']),
}
if not all(isinstance(v, str) and v for v in variables.values()):
    raise SystemExit('Incomplete signing configuration.')
env = os.environ.copy()
env.update(variables)
wrapper = root / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
command = [str(wrapper), '--no-daemon']
if args.offline:
    command.append('--offline')
command += [':app:assembleRelease', ':app:lintRelease']
if args.tests:
    command += ['-PtestBuildType=release', ':app:assembleReleaseAndroidTest']
subprocess.run(command, cwd=root, env=env, check=True)
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    raise SystemExit('Set ANDROID_HOME to verify the signed artifact.')
signer = Path(sdk) / 'build-tools/36.0.0' / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
apk = root / 'app/build/outputs/apk/release/app-release.apk'
subprocess.run([str(signer), 'verify', '--verbose', '--print-certs', str(apk)], check=True)
print('Signed release:', apk)
