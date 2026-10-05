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

def switch_state(description):
    for node in ui().iter('node'):
        if node.attrib.get('content-desc')==description and node.attrib.get('class','').endswith('Switch'):
            return node.attrib.get('checked')=='true'
    return None
def set_switch(label,wanted):
    state=switch_state(label)
    for _ in range(3):
        if state is not None:break
        shell('input','swipe','800','850','800','300','400');time.sleep(.6)
        state=switch_state(label)
    for _ in range(4):
        if state is not None:break
        shell('input','swipe','800','300','800','850','400');time.sleep(.6)
        state=switch_state(label)
    if state is None:raise RuntimeError('switch not found: '+label)
    if state!=wanted:
        tap_text(label);state=switch_state(label)
    check(state==wanted,'switch '+label+' is '+str(wanted))
def window_rect(title):
    text=shell('dumpsys','window','windows')
    m=re.search(re.escape(title)+r'[\s\S]{0,900}?mAttrs=\{\((-?\d+),(-?\d+)\)\((\d+)x(\d+)\)',text)
    return tuple(map(int,m.groups())) if m else None
def tap_point(x,y):
    shell('input','tap',str(x),str(y));time.sleep(1.0)
def top_activity():
    m=re.search(r'topResumedActivity=ActivityRecord\{\S+ \S+ ([\w\./]+)',shell('dumpsys','activity','activities'))
    return m.group(1) if m else '?'
def action_button(name):
    return window_rect('Arotation action '+name)
def open_ring():
    ball=window_rect('Arotation floating button')
    if ball is None:return None
    tap_point(ball[0]+ball[2]//2,ball[1]+ball[3]//2)
    time.sleep(.4)
    return action_button('rotate')
def tap_action(name):
    rect=action_button(name)
    if rect is None:return False
    time.sleep(.3)
    rect=action_button(name) or rect
    tap_point(rect[0]+rect[2]//2,rect[1]+rect[3]//2)
    return True

# Quick actions: configure the ring, then run each key with real taps.
show()
for _ in range(3):
    if any(n.attrib.get('text')=='最近任务' for n in ui().iter('node')):break
    shell('input','swipe','800','850','800','300','400');time.sleep(.6)
set_switch('返回',True);set_switch('桌面',True);set_switch('最近任务',True)
shell('input','swipe','800','300','800','900','400');time.sleep(.8)
set_switch('悬浮按钮',True)
# Home needs no accessibility service: the visible overlay keeps the background-start allowance.
shell('am','start','-W','-f','0x14000000','-n',pkg+'/.MainActivity');time.sleep(1.2)
check(open_ring() is not None,'quick menu opens from a real tap on the floating ball')
check(tap_action('home'),'home key is a separate small window in the ring')
time.sleep(1.2);check(top_activity().startswith('com.android.launcher3'),'home returns to the launcher without the accessibility service')
check(action_button('rotate') is None,'ring windows are removed after running an action')
shell('settings','put','secure','enabled_accessibility_services',pkg+'/.accessibility.NavigationAccessibilityService')
shell('settings','put','secure','accessibility_enabled','1');time.sleep(2)
check('Bound services:{Service' in shell('dumpsys','accessibility'),'navigation service binds outside a test session')
shell('am','start','-W','-f','0x14000000','-n',pkg+'/.MainActivity');time.sleep(1.2)
check(open_ring() is not None,'quick menu reopens with the navigation service bound')
check(tap_action('back'),'back key is present in the ring')
time.sleep(1.2);check(top_activity()!=pkg+'/.MainActivity','back quick action leaves the settings activity')
check(action_button('back') is None,'ring windows are removed after the back action')
shell('am','start','-W','-f','0x14000000','-n',pkg+'/.MainActivity');time.sleep(1.2)
check(open_ring() is not None,'quick menu reopens for recents')
check(tap_action('recents'),'recents key is present in the ring')
time.sleep(1.6)
overview=ui();markers={n.attrib.get('text') for n in overview.iter('node')}
check('Screenshot' in markers or 'Split' in markers,'recents quick action opens the overview')
shell('input','keyevent','KEYCODE_HOME');time.sleep(.6)
shell('settings','delete','secure','enabled_accessibility_services');shell('settings','put','secure','accessibility_enabled','0');time.sleep(1)
check('Bound services:{Service' not in shell('dumpsys','accessibility'),'test disables the navigation service again')
show()
for _ in range(3):
    if any(n.attrib.get('text')=='最近任务' for n in ui().iter('node')):break
    shell('input','swipe','800','850','800','300','400');time.sleep(.6)
set_switch('返回',False);set_switch('桌面',False);set_switch('最近任务',False)
shell('input','swipe','800','300','800','900','400');time.sleep(.8)
set_switch('悬浮按钮',False)
metadata['passed']=results;(out/'checks.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(metadata,ensure_ascii=False),flush=True)
