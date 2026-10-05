package de.xianmu.arotation;

import de.xianmu.arotation.data.Prefs;
import de.xianmu.arotation.accessibility.NavigationAccessibilityService;
import de.xianmu.arotation.quick.QuickAction;

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
        CharSequence d=root.getContentDescription();
        if(d!=null&&(d.toString().equals("打开快捷操作")||d.toString().equals("收起快捷操作")))return root;
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
    private AccessibilityNodeInfo overlayNode(String label) {
        getUiAutomation().clearCache();
        for(AccessibilityWindowInfo window:getUiAutomation().getWindows()){
            AccessibilityNodeInfo found=byDescription(window.getRoot(),label);if(found!=null)return found;
        }return null;
    }
    /** Menu cells only: the settings switches reuse the same labels in the activity window. */
    private AccessibilityNodeInfo menuCell(String label) {
        getUiAutomation().clearCache();
        for(AccessibilityWindowInfo window:getUiAutomation().getWindows()){
            AccessibilityNodeInfo found=byDescription(window.getRoot(),label);
            if(found!=null&&found.getClassName()!=null&&found.getClassName().toString().endsWith("ImageButton"))return found;
        }return null;
    }
    /** One cache-clear pass for several ring keys, so animation timing cannot split the lookups. */
    private java.util.Map<String,Rect> overlayRects(String[] labels) {
        java.util.Map<String,Rect> found=new java.util.HashMap<>();
        getUiAutomation().clearCache();
        for(AccessibilityWindowInfo window:getUiAutomation().getWindows()){
            AccessibilityNodeInfo root=window.getRoot();
            if(root==null)continue;
            for(String label:labels) {
                if(found.containsKey(label))continue;
                AccessibilityNodeInfo node=byDescription(root,label);
                if(node!=null&&node.getClassName()!=null&&node.getClassName().toString().endsWith("ImageButton")){
                    Rect rect=new Rect();node.getBoundsInScreen(rect);found.put(label,rect);
                }
            }
        }
        return found;
    }
    /** The ball is "打开快捷操作" while closed and "收起快捷操作" while the ring is open. */
    private AccessibilityNodeInfo ballNode() {
        AccessibilityNodeInfo open=overlayNode("打开快捷操作");
        return open!=null?open:overlayNode("收起快捷操作");
    }
    /** The dot always opens the fan; rotating is one tap on the dot plus one on the rotate key. */
    private void rotateViaRing() {
        Rect dot=overlayBounds();
        tap(dot);
        await(()->overlayRects(new String[]{"旋转"}).size()==1,"rotate key appears in the fan",3000);
        SystemClock.sleep(350);
        Rect key=overlayRects(new String[]{"旋转"}).get("旋转");
        tap(key);
    }
    private AccessibilityNodeInfo nodeText(AccessibilityNodeInfo node,String text) {
        if(node==null)return null;
        if(text.contentEquals(node.getText()==null?"":node.getText()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=nodeText(node.getChild(i),text);if(found!=null)return found;}
        return null;
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
        getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME").close();SystemClock.sleep(500);
        live.store.edit().putBoolean("snap",true).putInt("opacity",56).commit();
        for(int size=0;size<3;size++)for(boolean left:new boolean[]{true,false}) {
            boolean landscape=bounds().width()>bounds().height();
            live.position(landscape,left?0:1,.5f);
            live.store.edit().putInt("size",size).commit();
            runOnMainSync(()->app.startService(new Intent(app,RotationService.class).setAction(RotationService.RESET)));
            SystemClock.sleep(450);Rect placed=overlayBounds();
            check(placed.width()==Math.round(live.touchDp()*density),"ball keeps its touch size "+size+" left="+left);
            check(placed.width()==placed.height(),"ball window stays square size="+size);
            check(left?placed.left<Math.round(8*density):bounds().width()-placed.right<Math.round(8*density),"ball is settled at the selected edge");
            if(size==1)capture("ball-"+(left?"left":"right"));
            SystemClock.sleep(3200);Rect idle=overlayBounds();
            check(idle.equals(placed),"idle only fades: the ball never changes bounds size="+size+" left="+left);
            check(live.x(landscape)==(left?0:1)&&live.y(landscape)==.5f,"idle never overwrites the saved position");
            if(size==1) {
                int oldRotation=display().getRotation();
                long grip=SystemClock.uptimeMillis();motion(grip,0,placed.centerX(),placed.centerY());
                SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout()+100);
                for(int step=1;step<=16;step++) {
                    float t=step/16f;motion(grip,2,placed.centerX()+(left?120:-120)*density*t,placed.centerY()+80*density*t);SystemClock.sleep(12);
                }
                motion(grip,1,placed.centerX()+(left?120:-120)*density,placed.centerY()+80*density);SystemClock.sleep(350);
                check(overlayBounds().top>placed.top+40*density,"dragging the ball is not stolen by system Back left="+left);
                check(display().getRotation()==oldRotation,"dragging the ball never rotates left="+left);
            }
        }
        Rect ball=overlayBounds();int before=display().getRotation();
        long down=SystemClock.uptimeMillis();motion(down,0,ball.centerX(),ball.centerY());SystemClock.sleep(180);
        check(ball.equals(overlayBounds()),"pressing the ball does not move or resize it");
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout()+100);
        int targetX=bounds().width()/3,targetY=bounds().height()/3;
        for(int step=1;step<=16;step++) {
            float t=step/16f;motion(down,2,ball.centerX()+(targetX-ball.centerX())*t,ball.centerY()+(targetY-ball.centerY())*t);SystemClock.sleep(12);
        }
        motion(down,1,targetX,targetY);SystemClock.sleep(350);
        check(display().getRotation()==before,"long hold followed by dragging never rotates");
        check(overlayBounds().left<bounds().width()/4,"long hold does not disable dragging");
        check(!app.getPackageName().contentEquals(getUiAutomation().getRootInActiveWindow().getPackageName()),"dragging the ball never opens settings");
        SystemClock.sleep(3000);rotateViaRing();
        await(()->display().getRotation()!=before,"a dimmed dot still rotates from the fan",5000);SystemClock.sleep(600);
        live.store.edit().putInt("size",1).commit();SystemClock.sleep(400);capture("ball-rotated");
        Rect handle=overlayBounds();int cancelRotation=display().getRotation();
        long cancel=SystemClock.uptimeMillis();motion(cancel,0,handle.centerX(),handle.centerY());motion(cancel,3,handle.centerX(),handle.centerY());SystemClock.sleep(350);
        check(display().getRotation()==cancelRotation,"cancelled edge press never rotates");
        Rect multi=overlayBounds();long multiple=SystemClock.uptimeMillis();
        motion(multiple,0,multi.centerX(),multi.centerY());
        twoPointers(multiple,android.view.MotionEvent.ACTION_POINTER_DOWN|(1<<8),multi.centerX(),multi.centerY());
        twoPointers(multiple,android.view.MotionEvent.ACTION_POINTER_UP|(1<<8),multi.centerX(),multi.centerY());
        motion(multiple,1,multi.centerX(),multi.centerY());SystemClock.sleep(350);
        check(display().getRotation()==cancelRotation,"multi-touch interruption never becomes a rotate click");
        Rect held=overlayBounds();long hold=SystemClock.uptimeMillis();motion(hold,0,held.centerX(),held.centerY());SystemClock.sleep(3300);
        check(held.equals(overlayBounds()),"a held ball never fades away or changes bounds");
        motion(hold,1,held.centerX(),held.centerY());SystemClock.sleep(300);
        check(display().getRotation()==cancelRotation,"a stationary hold past idle timeout does not rotate");
        // Test the reduced-animation path with the actual global setting, then restore it.
        float oldScale=Settings.Global.getFloat(app.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1);
        getUiAutomation().adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        try {
            Settings.Global.putFloat(app.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,0);SystemClock.sleep(300);
            runOnMainSync(()->app.startService(new Intent(app,RotationService.class).setAction(RotationService.RESET)));
            SystemClock.sleep(3300);
            check(overlayBounds().width()==Math.round(live.touchDp()*density),"animation-disabled mode keeps the full ball window");
            rotateViaRing();
            await(()->display().getRotation()!=cancelRotation,"animation-disabled fan still rotates",5000);
        }finally{
            Settings.Global.putFloat(app.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,oldScale);
            getUiAutomation().dropShellPermissionIdentity();
        }
        live.store.edit().putBoolean("snap",false).commit();SystemClock.sleep(3400);
        check(overlayBounds().width()==Math.round(live.touchDp()*density),"snap off keeps the ball at its full size");
        live.store.edit().putBoolean("snap",true).commit();SystemClock.sleep(350);
    }
    /**
     * Minimal navigation service declaration plus the quick menu driven by real overlay taps.
     * Dispatching the global actions themselves runs in scripts/check-device.py: UiAutomation
     * suppresses other accessibility services, so the host enables the service outside a session.
     */
    private void navigationChecks(Prefs live) throws Exception {
        android.content.pm.ServiceInfo nav=app.getPackageManager().getServiceInfo(
            new ComponentName(app,NavigationAccessibilityService.class),android.content.pm.PackageManager.GET_META_DATA);
        check(nav.exported&&"android.permission.BIND_ACCESSIBILITY_SERVICE".equals(nav.permission),"navigation service is bound by the system-protected accessibility permission");
        check(nav.metaData!=null&&nav.metaData.getInt("android.accessibilityservice")!=0,"navigation service ships its accessibility configuration");
        boolean readsWindows=true,performsGestures=true,takesScreenshots=true,tool=true;
        try(android.content.res.XmlResourceParser parser=app.getResources().getXml(nav.metaData.getInt("android.accessibilityservice"))){
            String namespace="http://schemas.android.com/apk/res/android";
            for(int event=parser.next();event!=android.content.res.XmlResourceParser.END_DOCUMENT;event=parser.next()){
                if(event!=android.content.res.XmlResourceParser.START_TAG)continue;
                android.util.AttributeSet attributes=android.util.Xml.asAttributeSet(parser);
                readsWindows=attributes.getAttributeBooleanValue(namespace,"canRetrieveWindowContent",true);
                performsGestures=attributes.getAttributeBooleanValue(namespace,"canPerformGestures",true);
                takesScreenshots=attributes.getAttributeBooleanValue(namespace,"canTakeScreenshot",true);
                tool=attributes.getAttributeBooleanValue(namespace,"isAccessibilityTool",true);
                break;
            }
        }
        check(!readsWindows&&!performsGestures&&!takesScreenshots,"navigation service cannot read windows, screenshot or inject gestures");
        check(!tool,"navigation service is declared as a convenience tool, not an accessibility tool");
        live.store.edit().putInt("quick_actions",0xF0).commit();
        check(live.quickActions()==QuickAction.ROTATE.bit,"unknown quick-action bits fall back to rotate");
        live.store.edit().putInt("quick_actions",0).commit();
        check(live.quickActions()==QuickAction.ROTATE.bit,"an empty quick-action selection falls back to rotate");
        check(!live.setQuickAction(QuickAction.ROTATE,false),"the last remaining quick action cannot be disabled");
        live.store.edit().putInt("quick_actions",QuickAction.ALL).commit();
        check(QuickAction.selected(live.quickActions()).length==4,"all four quick actions can be selected");
        live.store.edit().putInt("quick_actions",QuickAction.ROTATE.bit|QuickAction.HOME.bit).commit();
        check(!live.needsAccessibility(),"rotate and home need no accessibility service");
        live.store.edit().putInt("quick_actions",QuickAction.ROTATE.bit|QuickAction.BACK.bit).commit();
        check(live.needsAccessibility(),"back asks for the accessibility service");
        live.store.edit().putInt("quick_actions",QuickAction.ROTATE.bit|QuickAction.RECENTS.bit).commit();
        check(live.needsAccessibility(),"recents asks for the accessibility service");
        live.store.edit().putInt("quick_actions",QuickAction.ALL).commit();
        if(RotationService.running)disable();
        enable();
        await(()->overlayNode("打开快捷操作")!=null,"floating ball switches to menu mode with several actions",3000);
        int beforeRotate=display().getRotation();
        Rect bubble=new Rect();ballNode().getBoundsInScreen(bubble);tap(bubble);
        String[] ringLabels={"旋转","返回","桌面","最近任务"};
        await(()->overlayRects(ringLabels).size()==4,"quick menu lists every selected action",3000);
        SystemClock.sleep(350);
        java.util.Map<String,Rect> ringRects=overlayRects(ringLabels);
        check(ringRects.size()==4,"ring keys stay present for the geometry check");
        Rect ballBounds=new Rect();ballNode().getBoundsInScreen(ballBounds);
        double nearest=Double.MAX_VALUE,farthest=0;
        for(String label:ringLabels) {
            Rect cell=ringRects.get(label);
            double distance=Math.hypot(cell.exactCenterX()-ballBounds.exactCenterX(),cell.exactCenterY()-ballBounds.exactCenterY());
            nearest=Math.min(nearest,distance);farthest=Math.max(farthest,distance);
        }
        check(farthest-nearest<=app.getResources().getDisplayMetrics().density,"quick keys sit on one circle around the dot");
        boolean opensRight=ballBounds.exactCenterX()<bounds().width()/2.0;
        double inwardReach=0;
        for(String label:ringLabels) {
            double inward=(ringRects.get(label).exactCenterX()-ballBounds.exactCenterX())*(opensRight?1:-1);
            inwardReach=Math.max(inwardReach,inward);
        }
        check(inwardReach>Math.round(20*app.getResources().getDisplayMetrics().density),"quick keys fan out toward the screen interior");
        capture("quick-menu.png");
        Rect rotate=new Rect();menuCell("旋转").getBoundsInScreen(rotate);tap(rotate);
        await(()->display().getRotation()!=beforeRotate,"quick menu rotate action changes orientation",4000);
        await(()->menuCell("返回")==null,"menu closes after an action",3000);
        // Evidence captures: menu over the launcher, dark scheme, and the settings section.
        getUiAutomation().executeShellCommand("settings put system user_rotation 0").close();SystemClock.sleep(1500);
        getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME").close();SystemClock.sleep(1000);
        bubble=new Rect();ballNode().getBoundsInScreen(bubble);tap(bubble);
        await(()->menuCell("最近任务")!=null,"menu opens over the launcher",3000);
        capture("quick-menu-over-launcher.png");
        bubble=new Rect();ballNode().getBoundsInScreen(bubble);tap(bubble);
        await(()->menuCell("最近任务")==null,"menu closes on a second ball tap",3000);
        getUiAutomation().executeShellCommand("cmd uimode night yes").close();SystemClock.sleep(2500);
        bubble=new Rect();ballNode().getBoundsInScreen(bubble);tap(bubble);
        await(()->menuCell("最近任务")!=null,"menu opens in the dark scheme",3000);
        capture("quick-menu-dark.png");
        bubble=new Rect();ballNode().getBoundsInScreen(bubble);tap(bubble);
        await(()->menuCell("最近任务")==null,"dark menu closes on a second ball tap",3000);
        getUiAutomation().executeShellCommand("cmd uimode night no").close();SystemClock.sleep(2500);
        showApp();SystemClock.sleep(600);
        for(int i=0;i<3;i++){getUiAutomation().executeShellCommand("input swipe 800 850 800 300 400").close();SystemClock.sleep(700);}
        capture("settings-quick-actions.png");
        showApp();SystemClock.sleep(700);
        bubble=new Rect();ballNode().getBoundsInScreen(bubble);tap(bubble);
        await(()->menuCell("返回")!=null,"menu reopens for a navigation action",3000);
        SystemClock.sleep(350);
        Rect back=new Rect();menuCell("返回").getBoundsInScreen(back);tap(back);
        await(()->menuCell("返回")==null,"menu closes after a navigation action",3000);
        if(NavigationAccessibilityService.isConnected())
            await(()->{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
                return root!=null&&!app.getPackageName().contentEquals(root.getPackageName());},"connected navigation action leaves the app",5000);
        else
            await(()->{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
                return root!=null&&app.getPackageName().contentEquals(root.getPackageName());},"suppressed navigation action keeps the current app open",3000);
        // Home is a plain CATEGORY_HOME start: no accessibility service involved at all.
        check(!NavigationAccessibilityService.isConnected(),"home runs without the accessibility service");
        showApp();SystemClock.sleep(700);
        bubble=new Rect();ballNode().getBoundsInScreen(bubble);tap(bubble);
        await(()->menuCell("桌面")!=null,"menu reopens for the home action",3000);
        SystemClock.sleep(350);
        Rect home=new Rect();menuCell("桌面").getBoundsInScreen(home);tap(home);
        await(()->{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            return root!=null&&"com.android.launcher3".contentEquals(root.getPackageName());},"home action returns to the launcher without extra permission",5000);
        live.store.edit().putInt("quick_actions",QuickAction.ROTATE.bit).commit();
    }
    @Override public void onStart() {
        app=getTargetContext();Bundle output=new Bundle();int result=Activity.RESULT_OK;
        // A failed run can leave multi-action preferences behind; the regression flow below is built
        // around the single-action ball.
        new Prefs(app).store.edit().putInt("quick_actions",QuickAction.ROTATE.bit).putBoolean("snap",true).commit();
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
            check(overlay().performAction(AccessibilityNodeInfo.ACTION_CLICK),"floating dot accepts click");
            await(()->overlayRects(new String[]{"旋转"}).size()==1,"fan opens from an accessibility click",3000);
            SystemClock.sleep(350);
            Rect rotateKey=overlayRects(new String[]{"旋转"}).get("旋转");
            tap(rotateKey);
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
            SystemClock.sleep(3100);rotateViaRing();
            await(()->display().getRotation()!=beforeDrag,"rotation works from the fan after idle fade",5000);
            SystemClock.sleep(1600);rotateViaRing();
            await(()->display().getRotation()==beforeDrag,"a second run returns to the prior orientation",5000);
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
            live.store.edit().putBoolean("snap",true).commit();SystemClock.sleep(3450);
            Rect faded=new Rect();overlay().getBoundsInScreen(faded);
            check(faded.width()==Math.round(live.touchDp()*app.getResources().getDisplayMetrics().density),"idle fade never changes the ball window");
            int beforeFade=display().getRotation();rotateViaRing();
            await(()->display().getRotation()!=beforeFade,"a faded dot still rotates from the fan",5000);SystemClock.sleep(500);
            live.store.edit().putInt("size",2).commit();SystemClock.sleep(300);
            final int expectedSize=Math.round(live.touchDp()*app.getResources().getDisplayMetrics().density);
            await(()->{AccessibilityNodeInfo node=overlay();if(node==null)return false;Rect rect=new Rect();node.getBoundsInScreen(rect);return Math.abs(rect.width()-expectedSize)<=1;},"live size updates and pressed scale returns to normal",2000);
            edgeChecks(live);
            showApp();
            // Logos are fixed: one launcher entry, and each action keeps its own native Tabler glyph.
            java.util.List<android.content.pm.ResolveInfo> entries=app.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.getPackageName()),0);
            check(entries.size()==1&&entries.get(0).activityInfo.name.equals(MainActivity.class.getName()),
                "the launcher entry is the fixed MainActivity logo");
            check(RotationService.running,"the fixed launcher entry keeps the foreground service alive");
            java.util.Set<Integer> actionIcons=new java.util.HashSet<>();
            for(QuickAction action:QuickAction.values()) {
                check(app.getDrawable(action.icon)!=null,"quick action "+action.name()+" keeps its native icon");
                actionIcons.add(action.icon);
            }
            check(actionIcons.size()==QuickAction.values().length,"the four quick actions use four distinct native icons");
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
            navigationChecks(new Prefs(app));
            new Prefs(app).store.edit().putInt("size",99).putInt("opacity",0).commit();
            check(new Prefs(app).size()==2&&new Prefs(app).opacity()==25,"invalid saved sizes and opacity are bounded");
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
