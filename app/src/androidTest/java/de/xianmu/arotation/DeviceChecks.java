package de.xianmu.arotation;

import de.xianmu.arotation.data.Prefs;
import de.xianmu.arotation.ui.IconCatalog;

import android.app.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.graphics.Rect;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.function.BooleanSupplier;

/** Framework Instrumentation only: no JUnit, AndroidX or third-party test runtime. */
public final class DeviceChecks extends Instrumentation {
    private Context app;
    private Activity activity;
    private int passed;
    private final StringBuilder report=new StringBuilder();
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);passed++;report.append("PASS ").append(name).append('\n');}
    private void await(BooleanSupplier test,String name,long millis){long end=SystemClock.uptimeMillis()+millis;while(!test.getAsBoolean()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(40);check(test.getAsBoolean(),name);}
    private android.view.Display display(){return app.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);}
    private Rect bounds(){android.graphics.Point size=new android.graphics.Point();display().getRealSize(size);return new Rect(0,0,size.x,size.y);}
    private int setting(String key){return Settings.System.getInt(app.getContentResolver(),key,-1);}
    private void showApp(){activity=startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));waitForIdleSync();}
    private void enable(){showApp();runOnMainSync(()->app.startForegroundService(new Intent(app,RotationService.class).setAction(RotationService.START)));await(()->RotationService.running,"foreground service started",5000);}
    private void disable(){runOnMainSync(()->app.stopService(new Intent(app,RotationService.class)));await(()->!RotationService.running,"foreground service stopped",5000);SystemClock.sleep(250);}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root) {
        if(root==null)return null;
        CharSequence d=root.getContentDescription();if(d!=null&&(d.toString().equals("切换为竖屏")||d.toString().equals("切换为横屏")))return root;
        for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo found=find(root.getChild(n));if(found!=null)return found;}return null;
    }
    private AccessibilityNodeInfo byDescription(AccessibilityNodeInfo node,String label) {
        if(node==null)return null;
        if(label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=byDescription(node.getChild(i),label);if(found!=null)return found;}
        return null;
    }
    private AccessibilityNodeInfo overlay() {
        // Window movement can leave cached node coordinates behind the rendered surface.
        getUiAutomation().clearCache();
        for(AccessibilityWindowInfo window:getUiAutomation().getWindows()){
            AccessibilityNodeInfo found=find(window.getRoot());if(found!=null)return found;
        }return null;
    }
    private void motion(long down,int action,float x,float y) {
        android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        if(!getUiAutomation().injectInputEvent(event,true))throw new AssertionError("touch injection rejected");event.recycle();
    }
    private void twoPointers(long down,int action,float x,float y) {
        android.view.MotionEvent.PointerProperties[] properties=new android.view.MotionEvent.PointerProperties[2];
        android.view.MotionEvent.PointerCoords[] coords=new android.view.MotionEvent.PointerCoords[2];
        for(int i=0;i<2;i++) {
            properties[i]=new android.view.MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=android.view.MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new android.view.MotionEvent.PointerCoords();coords[i].x=x+i*10;coords[i].y=y;coords[i].pressure=1;coords[i].size=1;
        }
        android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        if(!getUiAutomation().injectInputEvent(event,true))throw new AssertionError("multi-touch injection rejected");event.recycle();
    }
    private void tap(Rect bounds) {
        long t=SystemClock.uptimeMillis();motion(t,0,bounds.centerX(),bounds.centerY());SystemClock.sleep(55);motion(t,1,bounds.centerX(),bounds.centerY());
    }
    private void drag(Rect bounds,int x,int y) {
        long t=SystemClock.uptimeMillis();motion(t,0,bounds.centerX(),bounds.centerY());
        for(int step=1;step<=16;step++){float ratio=step/16f;motion(t,2,bounds.centerX()+(x-bounds.centerX())*ratio,bounds.centerY()+(y-bounds.centerY())*ratio);SystemClock.sleep(12);}
        motion(t,1,x,y);
    }
    private Rect overlayBounds(){Rect rect=new Rect();AccessibilityNodeInfo node=overlay();if(node==null)throw new AssertionError("missing overlay");node.getBoundsInScreen(rect);return rect;}
    private void capture(String name) throws java.io.IOException {
        java.io.File dir=app.getExternalFilesDir("edge-checks");
        if(dir==null||(!dir.isDirectory()&&!dir.mkdirs()))throw new java.io.IOException("capture directory");
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap==null)throw new java.io.IOException("screenshot failed");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))) {
            if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("PNG compression");
        }finally{bitmap.recycle();}
    }
    private void edgeChecks(Prefs live) throws Exception {
        float density=app.getResources().getDisplayMetrics().density;
        int hit=Math.round(48*density);
        getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME").close();SystemClock.sleep(500);
        live.store.edit().putBoolean("snap",true).putBoolean("hide",true).putInt("opacity",56).commit();
        for(int size=0;size<3;size++)for(boolean left:new boolean[]{true,false}) {
            boolean landscape=bounds().width()>bounds().height();
            live.position(landscape,left?0:1,.5f);
            live.store.edit().putInt("size",size).commit();
            runOnMainSync(()->app.startService(new Intent(app,RotationService.class).setAction(RotationService.RESET)));
            SystemClock.sleep(450);Rect expanded=overlayBounds();
            check(expanded.width()==Math.round(live.touchDp()*density),"expanded size "+size+" left="+left);
            check(left?expanded.left<Math.round(8*density):bounds().width()-expanded.right<Math.round(8*density),"expanded control is settled at the selected edge");
            if(size==1)capture("expanded-"+(left?"left":"right"));
            SystemClock.sleep(3000);Rect edge=overlayBounds();
            check(Math.abs(edge.width()-hit)<=1&&edge.height()>=hit,"exact bounded 48dp edge hit area size="+size+" left="+left);
            check(left?edge.left==0:edge.right==bounds().width(),"handle docks at the physical edge size="+size+" left="+left);
            check(Math.abs(edge.centerY()-expanded.centerY())<=1,"collapse preserves vertical center size="+size+" left="+left+" "+expanded.toShortString()+" -> "+edge.toShortString());
            check(live.x(landscape)==(left?0:1)&&live.y(landscape)==.5f,"collapse does not overwrite saved position");
            capture("tucked-size-"+size+"-"+(left?"left":"right"));
            if(size==1) {
                int oldRotation=display().getRotation();float gripX=left?6*density:bounds().width()-6*density;
                long grip=SystemClock.uptimeMillis();motion(grip,0,gripX,edge.centerY());
                SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout()+100);
                for(int step=1;step<=16;step++) {
                    float t=step/16f;motion(grip,2,gripX+(left?120:-120)*density*t,edge.centerY()+80*density*t);SystemClock.sleep(12);
                }
                motion(grip,1,gripX+(left?120:-120)*density,edge.centerY()+80*density);SystemClock.sleep(350);
                check(overlayBounds().top>edge.top+40*density,"dragging the visible edge grip is not stolen by system Back left="+left);
                check(display().getRotation()==oldRotation,"visible-grip drag never rotates left="+left);
            }
        }
        Rect tucked=overlayBounds();int before=display().getRotation();
        long down=SystemClock.uptimeMillis();motion(down,0,tucked.centerX(),tucked.centerY());SystemClock.sleep(180);
        check(tucked.equals(overlayBounds()),"pressing tucked handle does not jump or shrink its hit area");
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout()+100);
        int targetX=bounds().width()/3,targetY=bounds().height()/3;
        for(int step=1;step<=16;step++) {
            float t=step/16f;motion(down,2,tucked.centerX()+(targetX-tucked.centerX())*t,tucked.centerY()+(targetY-tucked.centerY())*t);SystemClock.sleep(12);
        }
        motion(down,1,targetX,targetY);SystemClock.sleep(350);
        check(display().getRotation()==before,"long hold followed by dragging never rotates");
        check(overlayBounds().left<bounds().width()/4&&overlayBounds().top<tucked.top,"long hold does not disable dragging from the handle");
        check(!app.getPackageName().contentEquals(getUiAutomation().getRootInActiveWindow().getPackageName()),"dragging from a held handle never opens settings");
        SystemClock.sleep(3000);Rect leftHandle=overlayBounds();tap(leftHandle);
        await(()->display().getRotation()!=before,"one tap on the new left handle rotates directly",4000);SystemClock.sleep(600);
        live.store.edit().putInt("size",1).commit();SystemClock.sleep(400);capture("rotated-expanded");
        SystemClock.sleep(3100);capture("rotated-tucked");
        Rect handle=overlayBounds();int cancelRotation=display().getRotation();
        long cancel=SystemClock.uptimeMillis();motion(cancel,0,handle.centerX(),handle.centerY());motion(cancel,3,handle.centerX(),handle.centerY());SystemClock.sleep(350);
        check(display().getRotation()==cancelRotation,"cancelled edge press never rotates");
        Rect multi=overlayBounds();long multiple=SystemClock.uptimeMillis();
        motion(multiple,0,multi.centerX(),multi.centerY());
        twoPointers(multiple,android.view.MotionEvent.ACTION_POINTER_DOWN|(1<<8),multi.centerX(),multi.centerY());
        twoPointers(multiple,android.view.MotionEvent.ACTION_POINTER_UP|(1<<8),multi.centerX(),multi.centerY());
        motion(multiple,1,multi.centerX(),multi.centerY());SystemClock.sleep(350);
        check(display().getRotation()==cancelRotation,"multi-touch interruption never becomes a rotate click");
        Rect expanded=overlayBounds();long held=SystemClock.uptimeMillis();motion(held,0,expanded.centerX(),expanded.centerY());SystemClock.sleep(3300);
        check(overlayBounds().width()==Math.round(live.touchDp()*density),"idle timer never collapses the button under a held finger");
        motion(held,1,expanded.centerX(),expanded.centerY());SystemClock.sleep(300);
        check(display().getRotation()==cancelRotation,"a stationary hold past idle timeout does not rotate");
        // Test the reduced-animation path with the actual global setting, then restore it.
        float oldScale=Settings.Global.getFloat(app.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1);
        getUiAutomation().adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        try {
            Settings.Global.putFloat(app.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,0);SystemClock.sleep(300);
            runOnMainSync(()->app.startService(new Intent(app,RotationService.class).setAction(RotationService.RESET)));
            SystemClock.sleep(3300);
            check(overlayBounds().width()==hit,"animation-disabled mode still produces the bounded handle");
            Rect noMotion=overlayBounds();tap(noMotion);
            await(()->display().getRotation()!=cancelRotation,"animation-disabled handle still rotates in one tap",4000);
        }finally{
            Settings.Global.putFloat(app.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,oldScale);
            getUiAutomation().dropShellPermissionIdentity();
        }
        live.store.edit().putBoolean("snap",false).commit();SystemClock.sleep(3400);
        check(overlayBounds().width()==Math.round(live.touchDp()*density),"turning off snap also disables edge collapse");
        live.store.edit().putBoolean("snap",true).putBoolean("hide",false).commit();SystemClock.sleep(350);
    }
    @Override public void onStart() {
        app=getTargetContext();Bundle output=new Bundle();int result=Activity.RESULT_OK;
        try {
            AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(info);
            check("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE),"isolated emulator guard");
            check("de.xianmu.arotation".equals(app.getPackageName()),"personal application ID is de.xianmu.arotation");
            check("Arotation".contentEquals(app.getApplicationInfo().loadLabel(app.getPackageManager())),"application is named Arotation");
            android.content.pm.ServiceInfo tile=app.getPackageManager().getServiceInfo(
                new ComponentName(app,RotationTileService.class),android.content.pm.PackageManager.GET_META_DATA);
            check(tile.exported,"Quick Settings service is exported to SystemUI");
            check("android.permission.BIND_QUICK_SETTINGS_TILE".equals(tile.permission),"Quick Settings binding is system-protected");
            check(tile.metaData.getBoolean(android.service.quicksettings.TileService.META_DATA_TOGGLEABLE_TILE),"tile exposes switch accessibility semantics");
            check(!tile.metaData.getBoolean(android.service.quicksettings.TileService.META_DATA_ACTIVE_TILE),"tile only listens while SystemUI needs it");
            android.content.pm.ResolveInfo preferences=app.getPackageManager().resolveActivity(
                new Intent(android.service.quicksettings.TileService.ACTION_QS_TILE_PREFERENCES).setPackage(app.getPackageName()),0);
            check(preferences!=null&&preferences.activityInfo.name.equals(MainActivity.class.getName()),"tile long-press resolves to existing settings Activity");
            check(Settings.canDrawOverlays(app),"overlay permission is actually granted");
            check(Settings.System.canWrite(app),"system-write permission is actually granted");
            if(RotationService.running)disable();
            new Prefs(app).store.edit().clear().commit();
            Settings.System.putInt(app.getContentResolver(),Settings.System.USER_ROTATION,0);
            Settings.System.putInt(app.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1);
            SystemClock.sleep(500);enable();
            check(setting(Settings.System.ACCELEROMETER_ROTATION)==0,"automatic rotation disabled by app");
            await(()->overlay()!=null,"real cross-app overlay is accessible",4000);
            int before=display().getRotation();
            Rect beforeBounds=bounds();
            check(overlay().performAction(AccessibilityNodeInfo.ACTION_CLICK),"floating button accepts click");
            await(()->display().getRotation()!=before,"display really rotates",5000);
            SystemClock.sleep(500);
            Rect after=bounds();
            check((beforeBounds.width()>beforeBounds.height())!=(after.width()>after.height()),"display width and height swap");
            check(setting(Settings.System.ACCELEROMETER_ROTATION)==0,"manual rotation remains locked");
            await(()->overlay()!=null,"overlay survives configuration change",4000);
            await(()->{AccessibilityNodeInfo node=overlay();if(node==null)return false;Rect rect=new Rect();node.getBoundsInScreen(rect);Rect displayBounds=bounds();return rect.left>=0&&rect.top>=0&&rect.right<=displayBounds.width()&&rect.bottom<=displayBounds.height();},"button settles inside visible display bounds",3000);
            Rect bubble=new Rect();overlay().getBoundsInScreen(bubble);
            int beforeDrag=display().getRotation();
            drag(bubble,80,210);SystemClock.sleep(400);
            check(display().getRotation()==beforeDrag,"drag does not trigger rotation");
            Rect moved=new Rect();overlay().getBoundsInScreen(moved);
            check(moved.left<bubble.left&&moved.top<bubble.top,"finger drag really moves the floating window");
            check(new Prefs(app).x(after.width()>after.height())<.05f,"drag snaps and persists left-edge position");
            SystemClock.sleep(3100);tap(moved);
            await(()->display().getRotation()!=beforeDrag,"one tap works directly after idle fade",4000);
            SystemClock.sleep(1600);Rect other=new Rect();overlay().getBoundsInScreen(other);tap(other);
            await(()->display().getRotation()==beforeDrag,"second tap returns to prior orientation",4000);
            SystemClock.sleep(1600);Rect restored=new Rect();overlay().getBoundsInScreen(restored);
            check(Math.abs(restored.left-moved.left)<=2&&Math.abs(restored.top-moved.top)<=2,"orientation-specific position is restored: " + moved.toShortString() + " -> " + restored.toShortString());
            getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME").close();SystemClock.sleep(500);
            await(()->overlay()!=null,"overlay remains above launcher",4000);
            check(!overlay().isLongClickable(),"floating button exposes no long-press action");
            check(!overlay().performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK),"accessibility long press cannot open settings");
            Rect holdBounds=overlayBounds();int holdRotation=display().getRotation();
            long hold=SystemClock.uptimeMillis();motion(hold,0,holdBounds.centerX(),holdBounds.centerY());
            SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout()+150);
            check(!app.getPackageName().contentEquals(getUiAutomation().getRootInActiveWindow().getPackageName()),"holding the floating button does not leave the launcher");
            motion(hold,1,holdBounds.centerX(),holdBounds.centerY());SystemClock.sleep(300);
            check(display().getRotation()==holdRotation,"releasing a long hold does not rotate");
            android.service.notification.StatusBarNotification[] notes=app.getSystemService(NotificationManager.class).getActiveNotifications();
            check(notes.length==1,"exactly one foreground notification");
            check(notes[0].getNotification().actions.length==2,"notification has settings and stop actions");
            showApp();
            runOnMainSync(()->activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:"+app.getPackageName()))));
            await(()->{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();return root!=null&&"com.android.settings".contentEquals(root.getPackageName());},"system permission page is open above app settings",4000);
            notes[0].getNotification().actions[0].actionIntent.send();
            await(()->{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();return root!=null&&app.getPackageName().contentEquals(root.getPackageName());},"notification returns to app settings rather than nested permission page",4000);
            notes[0].getNotification().actions[1].actionIntent.send();
            await(()->!RotationService.running,"notification stop ends service",5000);SystemClock.sleep(300);
            check(setting(Settings.System.ACCELEROMETER_ROTATION)==1,"stop restores prior automatic rotation");
            check(setting(Settings.System.USER_ROTATION)==0,"stop restores prior user rotation");
            await(()->overlay()==null,"stop removes floating window",3000);
            check(!new Prefs(app).store.getBoolean("session",false),"stop clears ownership record");
            enable();Settings.System.putInt(app.getContentResolver(),Settings.System.USER_ROTATION,2);
            Settings.System.putInt(app.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1);
            await(()->!RotationService.running,"system automatic rotation takes precedence",5000);SystemClock.sleep(300);
            check(setting(Settings.System.USER_ROTATION)==2,"newer user choice is not overwritten");
            check(setting(Settings.System.ACCELEROMETER_ROTATION)==1,"newer automatic mode is preserved");
            enable();
            Prefs live=new Prefs(app);live.store.edit().putBoolean("snap",false).commit();SystemClock.sleep(250);
            Rect freeStart=new Rect();overlay().getBoundsInScreen(freeStart);Rect full=bounds();drag(freeStart,full.width()/2,full.height()/3);SystemClock.sleep(350);
            Rect freeEnd=new Rect();overlay().getBoundsInScreen(freeEnd);
            check(freeEnd.left>full.width()/4&&freeEnd.right<full.width()*3/4,"free placement is not forced to an edge");
            live.store.edit().putBoolean("snap",true).putBoolean("hide",true).commit();SystemClock.sleep(3450);
            Rect tucked=new Rect();overlay().getBoundsInScreen(tucked);
            check(tucked.width()>=Math.round(48*app.getResources().getDisplayMetrics().density)-1,"tucked button retains a 48dp touch target");
            int beforeTucked=display().getRotation();tap(tucked);
            await(()->display().getRotation()!=beforeTucked,"one tap on tucked button directly rotates",4000);SystemClock.sleep(500);
            live.store.edit().putBoolean("hide",false).putInt("size",2).commit();SystemClock.sleep(300);
            final int expectedSize=Math.round(live.touchDp()*app.getResources().getDisplayMetrics().density);
            await(()->{AccessibilityNodeInfo node=overlay();if(node==null)return false;Rect rect=new Rect();node.getBoundsInScreen(rect);return Math.abs(rect.width()-expectedSize)<=1;},"live size updates and pressed scale returns to normal",2000);
            edgeChecks(live);
            showApp();
            for(int index=0;index<IconCatalog.DRAWABLES.length;index++) {
                final int chosen=index;
                AccessibilityNodeInfo choice=byDescription(getUiAutomation().getRootInActiveWindow(),"图标："+IconCatalog.NAMES[index]);
                check(choice!=null&&choice.performAction(AccessibilityNodeInfo.ACTION_CLICK),"designer icon "+index+" is selectable in real UI");
                await(()->new Prefs(app).icon()==chosen,"designer icon "+index+" selection persists",2000);
                await(()->{AccessibilityNodeInfo button=overlay();return button!=null&&("图标："+IconCatalog.NAMES[chosen]).contentEquals(button.getStateDescription());},"designer icon "+index+" updates the live overlay",2000);
                java.util.List<android.content.pm.ResolveInfo> entries=app.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.getPackageName()),0);
                check(entries.size()==1&&entries.get(0).activityInfo.name.endsWith("."+IconCatalog.ALIASES[index]),"designer icon "+index+" leaves exactly one matching launcher entry");
                check(RotationService.running,"designer icon "+index+" keeps foreground service alive");
            }
            runOnMainSync(()->{IconCatalog.applyLauncher(app,0);new Prefs(app).store.edit().putInt("icon",0).commit();});
            getUiAutomation().executeShellCommand("input keyevent KEYCODE_SLEEP").close();
            await(()->overlay()==null,"screen-off hides the overlay",4000);
            getUiAutomation().executeShellCommand("input keyevent KEYCODE_WAKEUP").close();SystemClock.sleep(400);
            getUiAutomation().executeShellCommand("wm dismiss-keyguard").close();
            await(()->overlay()!=null,"unlock restores the overlay",4000);
            getUiAutomation().executeShellCommand("appops set de.xianmu.arotation SYSTEM_ALERT_WINDOW deny").close();
            await(()->!RotationService.running,"revoking overlay permission stops service",5000);SystemClock.sleep(300);
            check(setting(Settings.System.ACCELEROMETER_ROTATION)==1,"permission revocation restores prior rotation settings");
            getUiAutomation().executeShellCommand("appops set de.xianmu.arotation SYSTEM_ALERT_WINDOW allow").close();SystemClock.sleep(300);
            check(Settings.canDrawOverlays(app),"test restores overlay permission");
            new Prefs(app).store.edit().putInt("size",99).putInt("opacity",0).putInt("icon",99).commit();
            check(new Prefs(app).size()==2&&new Prefs(app).opacity()==25,"invalid saved sizes and opacity are bounded");
            check(new Prefs(app).icon()==5,"invalid saved icon is bounded");
            new Prefs(app).store.edit().clear().commit();
            Settings.System.putInt(app.getContentResolver(),Settings.System.USER_ROTATION,0);
            Settings.System.putInt(app.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,0);
            report.append("TOTAL ").append(passed).append(" passed\n");
        } catch(Throwable e) {
            result=Activity.RESULT_CANCELED;report.append("FAIL ").append(e).append('\n');
            for(StackTraceElement frame:e.getStackTrace())report.append(frame).append('\n');
        } finally {
            if(RotationService.running)disable();output.putString("stream",report.toString());output.putInt("passed",passed);finish(result,output);
        }
    }
}
