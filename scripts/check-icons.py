from pathlib import Path
import argparse,os,shutil,subprocess,time,re,json,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--out',default='build/icon-checks');args=p.parse_args()
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
adb=shutil.which('adb') or (str(Path(sdk)/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')) if sdk else None)
if not adb:raise SystemExit('Set ANDROID_HOME or put adb on PATH.')
pkg='de.xianmu.arotation';out=Path(args.out);out.mkdir(parents=True,exist_ok=True);passed=[]
def run(*cmd):return subprocess.run([adb,'-s',args.serial,*cmd],capture_output=True,check=True,timeout=40).stdout
def shell(*cmd):return run('shell',*cmd).decode('utf-8',errors='replace').strip()
def check(ok,name):
    if not ok:raise AssertionError(name)
    passed.append(name);print('PASS '+name,flush=True)
def ui():
    shell('uiautomator','dump','/sdcard/arotation-icons.xml');return ET.fromstring(shell('cat','/sdcard/arotation-icons.xml'))
def tap(label):
    node=next(n for n in ui().iter('node') if n.attrib.get('content-desc')==label)
    l,t,r,b=map(int,re.findall(r'\d+',node.attrib['bounds']));shell('input','tap',str((l+r)//2),str((t+b)//2));time.sleep(.8)
def show(alias):return shell('am','start','-W','-f','0x14000000','-n',pkg+'/.'+alias)
check(shell('getprop','ro.kernel.qemu')=='1','isolated emulator guard')
show('IconClassic');time.sleep(.6);tap('悬浮按钮')
check(shell('settings','get','system','accelerometer_rotation')=='0','launcher entry starts working manual rotation')
aliases=['IconCycle','IconAngle','IconDots','IconSwap','IconTablet','IconClassic'];names=['双环','转向','点线','双箭头','平板','圆弧']
for alias,name in zip(aliases,names):
    pid=shell('pidof',pkg);tap('图标：'+name)
    check(shell('pidof',pkg)==pid,'switching to '+alias+' does not restart the process')
    check('Status: ok' in show(alias),'selected '+alias+' launches the real settings activity')
    shell('am','force-stop',pkg);check('Status: ok' in show(alias),'selected '+alias+' survives a process restart')
    node=next(n for n in ui().iter('node') if n.attrib.get('content-desc')=='图标：'+name)
    check(node.attrib.get('selected')=='true','selected '+alias+' is restored in the UI')
    # Force-stop deliberately stops the service; re-enable before checking the next icon switch.
    if alias!=aliases[-1]:tap('悬浮按钮')
shell('settings','put','system','accelerometer_rotation','0');shell('settings','put','system','user_rotation','0')
(out/'checks.json').write_text(json.dumps({'sdk':shell('getprop','ro.build.version.sdk'),'passed':passed},ensure_ascii=False,indent=2),encoding='utf-8')
print('TOTAL',len(passed),'passed',flush=True)
