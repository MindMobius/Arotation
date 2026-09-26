from pathlib import Path
import subprocess,time,re,xml.etree.ElementTree as ET,json,argparse,os,shutil
arg=argparse.ArgumentParser();arg.add_argument('--serial',required=True);arg.add_argument('--out',default='build/device-checks');args=arg.parse_args()
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
adb=shutil.which('adb') or (str(Path(sdk)/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')) if sdk else None)
if not adb or not Path(adb).is_file():raise SystemExit('Set ANDROID_HOME or add adb to PATH before running this emulator-only test.')
pkg='de.xianmu.arotation';out=Path(args.out);out.mkdir(parents=True,exist_ok=True);results=[]
def run(*cmd):return subprocess.run([adb,'-s',args.serial,*cmd],capture_output=True,check=True,timeout=40).stdout
def shell(*cmd):return run('shell',*cmd).decode(errors='replace').strip()
def check(test,name):
 if not test:raise RuntimeError('FAIL '+name)
 results.append(name);print('PASS '+name,flush=True)
def show():shell('am','start','-W','-f','0x14000000','-n',pkg+'/.MainActivity');time.sleep(1.3)
def ui():
 shell('uiautomator','dump','/sdcard/arotation-qa.xml');return ET.fromstring(shell('cat','/sdcard/arotation-qa.xml'))
def tap_text(text):
 node=next(n for n in ui().iter('node') if n.attrib.get('text')==text)
 l,t,r,b=map(int,re.findall(r'\d+',node.attrib['bounds']));shell('input','tap',str((l+r)//2),str((t+b)//2));time.sleep(.8)
def setting(key):return shell('settings','get','system',key)
def capture(name):
 ui();time.sleep(.35);(out/name).write_bytes(run('exec-out','screencap','-p'))
def ticks(pid):
 s=shell('run-as',pkg,'cat',f'/proc/{pid}/stat');parts=s[s.rfind(')')+1:].split();return int(parts[11])+int(parts[12])
check(shell('getprop','ro.kernel.qemu')=='1','isolated emulator guard')
metadata={'sdk':shell('getprop','ro.build.version.sdk'),'release':shell('getprop','ro.build.version.release'),'serial':args.serial}
shell('settings','put','system','accelerometer_rotation','1');show();tap_text('悬浮按钮');check(setting('accelerometer_rotation')=='0','real settings button starts manual mode')
shell('am','force-stop',pkg);check(setting('accelerometer_rotation')=='0','force-stop cannot execute app recovery code')
show();check(setting('accelerometer_rotation')=='1','next launch recovers abandoned rotation session')
tap_text('悬浮按钮');shell('am','start','-a','android.settings.SETTINGS');time.sleep(.5)
shell('appops','set',pkg,'WRITE_SETTINGS','deny');time.sleep(.8)
check(re.search(r'ServiceRecord\{[^\n]*?/\.RotationService\b',shell('dumpsys','activity','services',pkg)) is None,'revoking write access stops foreground service')
check(setting('accelerometer_rotation')=='0','recovery is deferred while write access is missing')
shell('appops','set',pkg,'WRITE_SETTINGS','allow');show();check(setting('accelerometer_rotation')=='1','returning from permission settings retries recovery')
tap_text('悬浮按钮');shell('input','keyevent','KEYCODE_HOME');time.sleep(4)
pid=shell('pidof',pkg).split()[0];a=ticks(pid);time.sleep(10);b=ticks(pid)
metadata['idle_sample']={'seconds':10,'cpu_ticks':b-a};capture('overlay-over-launcher.png')
show();tap_text('悬浮按钮');check(setting('accelerometer_rotation')=='1','real stop button restores original settings')
shell('appops','set',pkg,'SYSTEM_ALERT_WINDOW','deny');shell('appops','set',pkg,'WRITE_SETTINGS','deny')
shell('am','start','-a','android.settings.SETTINGS');time.sleep(.3);show();capture('first-run-permissions.png')
tap_text('允许');time.sleep(.7)
check(any('com.android.settings' in line for line in shell('dumpsys','activity','activities').splitlines() if 'mResumedActivity' in line or 'topResumedActivity' in line),'permission action reaches Android settings')
shell('appops','set',pkg,'SYSTEM_ALERT_WINDOW','allow');shell('appops','set',pkg,'WRITE_SETTINGS','allow');show()
shell('settings','put','system','accelerometer_rotation','0');shell('settings','put','system','user_rotation','0')
metadata['passed']=results;(out/'checks.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(metadata,ensure_ascii=False),flush=True)
