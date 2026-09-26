"""End-to-end Android 16 startup checks. Dedicated emulator only; no test runtime libraries.
Exercises real SystemUI tiles, system confirmation, Settings pages, and process death.
"""
from pathlib import Path
import argparse, json, os, re, shlex, shutil, subprocess, time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--out', default='build/startup-checks')
parser.add_argument('--section', choices=['all', 'tiles', 'permissions', 'lock', 'background'], default='all')
parser.add_argument('--release', action='store_true', help='Non-debuggable APK: skip run-as process-death probes')
args = parser.parse_args()
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
adb = shutil.which('adb') or (str(Path(sdk) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')) if sdk else None)
if not adb:
    raise SystemExit('Set ANDROID_HOME or add adb to PATH.')
pkg = 'de.xianmu.arotation'
component = pkg + '/.RotationTileService'
spec = 'custom(' + component + ')'
out = Path(args.out)
out.mkdir(parents=True, exist_ok=True)
passed = []


def run(*cmd):
    return subprocess.run([adb, '-s', args.serial, *cmd], capture_output=True, check=True, timeout=40).stdout


def shell(*cmd):
    return run('shell', shlex.join(cmd)).decode('utf-8', errors='replace').strip()


def check(ok, name):
    if not ok:
        raise AssertionError(name)
    passed.append(name)
    print('PASS ' + name, flush=True)


def until(predicate, seconds=5):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if predicate():
            return True
        time.sleep(.15)
    return False


def ui():
    shell('uiautomator', 'dump', '/sdcard/arotation-startup.xml')
    return ET.fromstring(shell('cat', '/sdcard/arotation-startup.xml'))


def node_for(label, attr='text', root=None):
    return next(n for n in (ui() if root is None else root).iter('node') if n.get(attr) == label)


def tap_node(node):
    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
    shell('input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(.5)


def tap(label, attr='text'):
    tap_node(node_for(label, attr))


def texts():
    return '\n'.join(n.get('text', '') for n in ui().iter('node'))


def capture(name):
    (out / name).write_bytes(run('exec-out', 'screencap', '-p'))


def running():
    return re.search(r'ServiceRecord\{[^\n]*?/\.RotationService\b', shell('dumpsys', 'activity', 'services', pkg)) is not None


def show():
    shell('cmd', 'statusbar', 'collapse')
    shell('am', 'start', '-W', '-f', '0x14000000', '-n', pkg + '/.MainActivity')
    time.sleep(.6)


def back(count=1):
    for _ in range(count):
        shell('input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(.3)


def top():
    return '\n'.join(l for l in shell('dumpsys', 'activity', 'activities').splitlines() if 'topResumedActivity' in l)


def tile_node(open_panel=True):
    if open_panel:
        shell('cmd', 'statusbar', 'expand-settings')
        time.sleep(.6)
    for _ in range(5):
        root = ui()
        nodes = [n for n in root.iter('node') if n.get('clickable') == 'true' and n.get('content-desc', '').startswith('Arotation')]
        if nodes:
            return nodes[0]
        if not any(n.get('resource-id') == 'com.android.systemui:id/tile_label' for n in root.iter('node')):
            shell('cmd', 'statusbar', 'expand-settings')
            time.sleep(.6)
            continue
        # The supplied test AVD is a landscape tablet: QS pages occupy the left pane.
        shell('input', 'swipe', '685', '330', '95', '330', '260')
        time.sleep(.4)
    raise AssertionError('Arotation tile is not visible in Quick Settings')


def tile_state(expected):
    node = tile_node()
    end = time.monotonic() + 6
    while True:
        if expected in (node.get('content-desc', '') + node.get('text', '')):
            return True
        if time.monotonic() >= end:
            return False
        time.sleep(.3)
        node = tile_node(False)


def granted():
    shell('appops', 'set', pkg, 'SYSTEM_ALERT_WINDOW', 'allow')
    shell('appops', 'set', pkg, 'WRITE_SETTINGS', 'allow')


check(shell('getprop', 'ro.kernel.qemu') == '1', 'isolated emulator guard')
check(shell('getprop', 'ro.build.version.sdk') == '36', 'Android 16 test profile')
check('1600x1000' in shell('wm', 'size'), 'landscape tablet test profile')
old_exempt = shell('cmd', 'deviceidle', 'whitelist', '=' + pkg) == 'true'
old_auto = shell('settings', 'get', 'system', 'accelerometer_rotation')
old_rotation = shell('settings', 'get', 'system', 'user_rotation')
pin_set = False
try:
    granted()
    show()
    if running():
        tap('悬浮按钮', 'content-desc')
        assert until(lambda: not running())
    shell('settings', 'put', 'system', 'accelerometer_rotation', '1')
    shell('settings', 'put', 'system', 'user_rotation', '0')
    if spec not in shell('settings', 'get', 'secure', 'sysui_qs_tiles'):
        shell('cmd', 'statusbar', 'add-tile', component)
        time.sleep(.6)
    if args.section in ('all', 'tiles'):
        shell('cmd', 'deviceidle', 'whitelist', '-' + pkg)
        shell('cmd', 'statusbar', 'remove-tile', component)
        check(until(lambda: spec not in shell('settings', 'get', 'secure', 'sysui_qs_tiles')), 'test removes only the Arotation tile')
        tap('添加快捷开关')
        check(node_for('ADD TILE').get('package') == 'com.android.systemui', 'add entry opens the real system confirmation')
        capture('add-tile-confirmation.png')
        tap('ADD TILE')
        check(until(lambda: spec in shell('settings', 'get', 'secure', 'sysui_qs_tiles')), 'confirmation adds the registered TileService')
        tap('添加快捷开关')
        check('ADD TILE' not in texts(), 'already-added tile does not duplicate the confirmation')

        # Kill only the application's own inactive process, not force-stop the package.
        shell('input', 'keyevent', 'KEYCODE_HOME')
        if not args.release:
            old_pid = shell('pidof', pkg).split()[0]
            shell('run-as', pkg, 'kill', '-9', old_pid)
            time.sleep(12)  # Background/cold-start probe for the debuggable build only.
        check(tile_state('悬浮按钮已关闭'), 'tile shows actual inactive state')
        tap_node(tile_node(False))
        check(until(running), 'physical Quick Settings tap starts the foreground service')
        if not args.release:
            check(shell('pidof', pkg).split()[0] != old_pid, 'tile starts from a newly created application process')
        check('com.android.launcher3' in top(), 'tile start needs neither a foreground Activity nor battery exemption')
        check(shell('settings', 'get', 'system', 'accelerometer_rotation') == '0', 'tile start locks automatic rotation')
        check(tile_state('悬浮按钮已开启'), 'tile turns on only after service startup')
        capture('quick-settings-on.png')
        # START_STICKY recovery is different from the user's explicit force-stop.
        if not args.release:
            active_pid = shell('pidof', pkg).split()[0]
            shell('run-as', pkg, 'kill', '-9', active_pid)
            check(until(lambda: (lambda ids: bool(ids) and active_pid not in ids)(shell('sh', '-c', 'pidof ' + pkg + ' || true').split()), 15),
                  'system recreates the process after active-service process death')
            check(tile_state('悬浮按钮已开启'), 'sticky service restart restores the live overlay state')
        tap_node(tile_node(False))
        check(until(lambda: not running()), 'second physical tile tap stops the service')
        check(shell('settings', 'get', 'system', 'accelerometer_rotation') == '1', 'tile stop restores previous automatic mode')
        check(tile_state('悬浮按钮已关闭'), 'tile updates to off while the panel stays open')

        # A long press should open settings, not toggle or create a second launcher.
        n = tile_node(False)
        l, t, r, b = map(int, re.findall(r'\d+', n.get('bounds')))
        x, y = str((l + r) // 2), str((t + b) // 2)
        shell('input', 'swipe', x, y, x, y, '850')
        check(until(lambda: pkg + '/.MainActivity' in top()), 'tile long press opens the existing settings page')
        check(not running(), 'long press does not enable the overlay')
        tap('悬浮按钮', 'content-desc')
        assert until(running)
        check(tile_state('悬浮按钮已开启'), 'desktop start and tile state agree')
        root = ui()
        if not any(n.get('text') == '停用' for n in root.iter('node')):
            # Expand only the header containing this application's name.
            candidates = [n for n in root.iter('node') if any(c.get('text') == 'Arotation' for c in n.iter('node'))
                          and any(c.get('resource-id') == 'android:id/expand_button' for c in n.iter('node'))]
            header = min(candidates, key=lambda n: len(list(n.iter())))
            tap_node(next(n for n in header.iter('node') if n.get('resource-id') == 'android:id/expand_button'))
        tap('停用')
        check(until(lambda: not running()), 'real notification action stops the service')
        check(tile_state('悬浮按钮已关闭'), 'notification stop synchronizes the visible tile')

    if args.section in ('all', 'permissions'):
        shell('cmd', 'statusbar', 'collapse')
        shell('appops', 'set', pkg, 'SYSTEM_ALERT_WINDOW', 'deny')
        check(tile_state('需要授权'), 'tile exposes missing overlay permission without a false on-state')
        tap_node(tile_node(False))
        check(until(lambda: 'com.android.settings' in top()), 'unauthorized tile click opens the real permission screen')
        check(not running(), 'missing permission does not start a rotation service')
        back()
        check(until(lambda: pkg + '/.MainActivity' in top()), 'cancelled permission returns to settings without a redirect loop')
        granted()
        show()
        check(not running(), 'granting later does not replay a consumed tile start')
        shell('input', 'keyevent', 'KEYCODE_HOME')
        tap_node(tile_node())
        assert until(running)
        shell('appops', 'set', pkg, 'WRITE_SETTINGS', 'deny')
        check(until(lambda: not running()), 'revoking write permission stops a tile-started service')
        check(tile_state('需要授权'), 'permission revocation updates the open tile')
        granted()
        show()
        check(shell('settings', 'get', 'system', 'accelerometer_rotation') == '1', 'restored permission recovers the abandoned rotation setting')

    if args.section in ('all', 'background'):
        shell('cmd', 'deviceidle', 'whitelist', '-' + pkg)
        tap('后台运行')
        check('电池优化：未排除' in texts(), 'background guide reports actual non-exempt state')
        tap('电池优化设置')
        check('com.android.settings' in top(), 'battery entry opens Android Settings')
        tap('Arotation')
        tap('Allow background usage')
        tap('Unrestricted')
        check(shell('cmd', 'deviceidle', 'whitelist', '=' + pkg) == 'true', 'real system choice grants the battery exemption')
        back(3)
        check('电池优化：已排除' in texts(), 'returning from system settings refreshes the still-open guide')
        capture('background-guide.png')
        tap('应用后台设置')
        check('com.android.settings' in top() and 'Arotation' in texts(), 'app-background entry opens this applications details')
        back()
        shell('appops', 'set', pkg, 'RUN_ANY_IN_BACKGROUND', 'ignore')
        show()
        tap('后台运行')
        check('后台限制：有限制' in texts(), 'system background restriction is not reported as unrestricted')
        shell('appops', 'set', pkg, 'RUN_ANY_IN_BACKGROUND', 'allow')
        tap('关闭')
        shell('dumpsys', 'battery', 'unplug')
        shell('cmd', 'power', 'set-mode', '1')
        time.sleep(.5)
        tap('后台运行')
        check('省电模式已开启 · 前往设置' in texts(), 'global saver is reported only when actually enabled')
        tap('省电模式已开启 · 前往设置')
        check('com.android.settings' in top(), 'global saver action opens settings rather than changing it silently')
        back()
        tap('关闭')
    if args.section in ('all', 'lock'):
        # A secure keyguard verifies unlockAndRun, not merely screen-off hiding.
        shell('input', 'keyevent', 'KEYCODE_HOME')
        shell('locksettings', 'set-pin', '2468')
        pin_set = True
        shell('input', 'keyevent', 'KEYCODE_SLEEP')
        time.sleep(.5)
        shell('input', 'keyevent', 'KEYCODE_WAKEUP')
        time.sleep(.5)
        tap_node(tile_node())
        time.sleep(.6)
        check(not running(), 'locked tile click waits for authentication before enabling')
        check('Enter PIN' in texts(), 'tile action presents the real authentication screen')
        shell('input', 'text', '2468')
        shell('input', 'keyevent', 'KEYCODE_ENTER')
        check(until(running, 8), 'unlock completes the pending tile action once')
        # Finish the keyguard exit before changing credentials or sending more UI actions.
        time.sleep(2)
        shell('cmd', 'statusbar', 'collapse')
        time.sleep(.6)
        show()
        tap('悬浮按钮', 'content-desc')
        check(until(lambda: not running()), 'settings remains interactive after tile authentication')
        shell('locksettings', 'clear', '--old', '2468')
        pin_set = False

    print('TOTAL', len(passed), 'passed', flush=True)
except Exception:
    capture('failure.png')
    (out / 'failure-ui.xml').write_text(ET.tostring(ui(), encoding='unicode'), encoding='utf-8')
    raise
finally:
    if pin_set:
        shell('locksettings', 'clear', '--old', '2468')
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    granted()
    shell('appops', 'set', pkg, 'RUN_ANY_IN_BACKGROUND', 'allow')
    shell('cmd', 'power', 'set-mode', '0')
    shell('dumpsys', 'battery', 'reset')
    shell('cmd', 'deviceidle', 'whitelist', ('+' if old_exempt else '-') + pkg)
    show()
    if running():
        tap('悬浮按钮', 'content-desc')
        until(lambda: not running())
    shell('settings', 'put', 'system', 'accelerometer_rotation', old_auto)
    shell('settings', 'put', 'system', 'user_rotation', old_rotation)
    (out / 'checks.json').write_text(json.dumps({'sdk': 36, 'section': args.section, 'release': args.release, 'passed': passed}, ensure_ascii=False, indent=2), encoding='utf-8')
