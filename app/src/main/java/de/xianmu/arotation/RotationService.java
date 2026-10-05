package de.xianmu.arotation;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;

import de.xianmu.arotation.data.Prefs;
import de.xianmu.arotation.accessibility.NavigationAccessibilityService;
import de.xianmu.arotation.overlay.OverlayHost;
import de.xianmu.arotation.quick.QuickAction;
import de.xianmu.arotation.rotation.RotationController;

/** User-started foreground service. No boot receiver, network, sensors or wake lock. */
public final class RotationService extends Service implements OverlayHost.Actions,
        DisplayManager.DisplayListener, android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String START="de.xianmu.arotation.START",STOP="de.xianmu.arotation.STOP",
        RESET="de.xianmu.arotation.RESET",STATE="de.xianmu.arotation.STATE";
    public static volatile boolean running=false;
    private static final String CHANNEL="floating_button";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private RotationController rotation;
    private OverlayHost overlay;
    private DisplayManager displays;
    private AppOpsManager appOps;
    private boolean registered,observing,stopping;
    private int pendingRotation=-1;
    private long lastTap;
    private int rotationRequest;
    private final AppOpsManager.OnOpChangedListener permissionListener=(op,pkg)->handler.post(()->{
        if(running&&(!Settings.canDrawOverlays(this)||!Settings.System.canWrite(this))) lostPermission();
    });
    private final ContentObserver autoObserver=new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) {
            if(running&&Settings.System.getInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,0)!=0) {
                Toast.makeText(RotationService.this,"已开启系统自动旋转，Arotation已停用",Toast.LENGTH_SHORT).show();stopSelf();
            }
        }
    };
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {updateLockState();}
    };
    @Override public void onCreate() {
        super.onCreate();prefs=new Prefs(this);rotation=new RotationController(this,prefs);
        displays=getSystemService(DisplayManager.class);appOps=getSystemService(AppOpsManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,getString(R.string.notification_channel),NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("手动旋转");channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        String action=intent==null?null:intent.getAction();
        if(STOP.equals(action)){prefs.desired(false);stopSelf();return START_NOT_STICKY;}
        if(RESET.equals(action)){if(running){overlay.refresh();return START_STICKY;}stopSelf();return START_NOT_STICKY;}
        if(running)return START_STICKY;
        if(intent==null&&!prefs.desired()){stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)||!rotation.canWrite()) {
            fail("需要悬浮显示和修改系统设置两项授权");return START_NOT_STICKY;
        }
        try {
            startForeground(17,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            rotation.begin();overlay=new OverlayHost(this,prefs,this);overlay.show();
            running=true;prefs.desired(true);prefs.store.edit().remove("last_error").apply();
            displays.registerDisplayListener(this,handler);
            prefs.store.registerOnSharedPreferenceChangeListener(this);
            IntentFilter screen=new IntentFilter();screen.addAction(Intent.ACTION_SCREEN_OFF);screen.addAction(Intent.ACTION_SCREEN_ON);screen.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenReceiver,screen,Context.RECEIVER_NOT_EXPORTED);registered=true;
            getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),false,autoObserver);observing=true;
            appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,getPackageName(),permissionListener);
            appOps.startWatchingMode(AppOpsManager.OPSTR_WRITE_SETTINGS,getPackageName(),permissionListener);
            updateLockState();notifyState();return START_STICKY;
        }catch(RuntimeException failure) {android.util.Log.e("Arotation","Unable to start floating window",failure);fail("暂时无法启用，请检查系统授权后重试");return START_NOT_STICKY;}
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,settingsIntent(),PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RotationService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_rotate)
            .setContentTitle(getString(R.string.notification_title)).setContentText(getString(R.string.notification_text))
            .setContentIntent(open).addAction(new Notification.Action.Builder(null,getString(R.string.settings),open).build())
            .addAction(new Notification.Action.Builder(null,getString(R.string.stop),stop).build())
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).build();
    }
    @Override public void rotate() {
        long now=SystemClock.uptimeMillis();if(stopping)return;
        // A confirmed display change must not wait for a late DisplayListener callback.
        if(pendingRotation>=0&&rotation.rotation()==pendingRotation)pendingRotation=-1;
        if(pendingRotation>=0||now-lastTap<350)return;lastTap=now;
        try {
            if(!rotation.canWrite()){lostPermission();return;}
            pendingRotation=rotation.toggle();
            final int request=++rotationRequest;
            handler.postDelayed(()->{
                if(pendingRotation<0||!running||request!=rotationRequest)return;
                if(rotation.rotation()!=pendingRotation)Toast.makeText(this,"当前应用未响应方向切换，试试返回桌面",Toast.LENGTH_SHORT).show();
                pendingRotation=-1;
            },1400);
        }catch(RuntimeException failure){pendingRotation=-1;android.util.Log.e("Arotation","Rotation request failed",failure);fail("切换未生效，请重新检查系统授权");}
    }
    @Override public void navigate(QuickAction action) {
        if(stopping||action==null||!action.isNavigation())return;
        if(action==QuickAction.HOME) {
            // The visible overlay keeps the background-activity-start allowance, so home needs no
            // elevated permission at all.
            try{startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
            catch(RuntimeException failure){Toast.makeText(this,R.string.navigation_unavailable,Toast.LENGTH_SHORT).show();}
            return;
        }
        if(!NavigationAccessibilityService.isConnected()) {
            Toast.makeText(this,getString(R.string.navigation_required_description,getString(action.label)),Toast.LENGTH_LONG).show();
            return;
        }
        if(!NavigationAccessibilityService.perform(action))
            Toast.makeText(this,R.string.navigation_unavailable,Toast.LENGTH_SHORT).show();
    }
    private Intent settingsIntent() {
        // Permission pages can live above our Activity in the same task; return to our actual settings.
        return new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
            Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    @Override public void lostPermission() {if(!stopping)fail("授权已被关闭，悬浮按钮已停用");}
    private void fail(String message) {
        if(stopping)return;stopping=true;prefs.desired(false);prefs.store.edit().putString("last_error",message).apply();
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();stopSelf();notifyState();
    }
    private void notifyState() {sendBroadcast(new Intent(STATE).setPackage(getPackageName()));}
    private void updateLockState() {
        if(overlay==null)return;
        boolean visible=getSystemService(PowerManager.class).isInteractive()&&!getSystemService(KeyguardManager.class).isKeyguardLocked();
        overlay.setScreenVisible(visible);
    }
    @Override public void onDisplayChanged(int id) {
        if(!running||id!=android.view.Display.DEFAULT_DISPLAY)return;
        if(rotation.rotation()==pendingRotation)pendingRotation=-1;
        // Metrics can lag the display event; bounded settling, never an idle polling loop.
        handler.removeCallbacks(settleDisplay);handler.postDelayed(settleDisplay,120);
    }
    private final Runnable settleDisplay=()->{if(overlay!=null&&running)overlay.displayChanged();};
    @Override public void onDisplayAdded(int id) {}
    @Override public void onDisplayRemoved(int id) {}
    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);if(overlay!=null)overlay.refresh();
    }
    @Override public void onSharedPreferenceChanged(android.content.SharedPreferences p,String key) {
        // A null key means the whole store was cleared; refresh for every geometry change.
        if(overlay!=null&&(key==null||"size".equals(key)||"opacity".equals(key)||"snap".equals(key)||
            "quick_actions".equals(key)))overlay.refresh();
    }
    @Override public void onDestroy() {
        stopping=true;running=false;handler.removeCallbacksAndMessages(null);
        if(registered)unregisterReceiver(screenReceiver);
        if(observing)getContentResolver().unregisterContentObserver(autoObserver);
        if(displays!=null)displays.unregisterDisplayListener(this);
        if(appOps!=null)appOps.stopWatchingMode(permissionListener);
        if(prefs!=null)prefs.store.unregisterOnSharedPreferenceChangeListener(this);
        if(overlay!=null){overlay.close();overlay=null;}
        try {if(rotation!=null)rotation.restore();}catch(RuntimeException e){prefs.store.edit().putString("last_error","原先设置尚未恢复，请检查修改系统设置授权").apply();}
        if(prefs!=null)prefs.desired(false);stopForeground(STOP_FOREGROUND_REMOVE);notifyState();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
