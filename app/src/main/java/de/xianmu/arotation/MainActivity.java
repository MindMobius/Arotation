package de.xianmu.arotation;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import de.xianmu.arotation.data.Prefs;
import de.xianmu.arotation.rotation.RotationController;
import de.xianmu.arotation.ui.BackgroundGuide;
import de.xianmu.arotation.ui.IconCatalog;
import de.xianmu.arotation.ui.Palette;

import java.nio.charset.StandardCharsets;

/** Native preferences and foreground entry points. */
public final class MainActivity extends Activity {
    public static final String ACTION_ENABLE = "de.xianmu.arotation.ENABLE_FROM_TILE";
    private BackgroundGuide backgroundGuide;
    private Button addTile;
    private boolean pendingEnable;
    private Prefs prefs;
    private Palette colors;
    private Switch power,tuck;
    private TextView error,opacityLabel;
    private LinearLayout permissionArea;
    private ImageButton[] iconButtons;
    private boolean receiverRegistered,refreshing;
    private final BroadcastReceiver stateReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){refreshState();}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);colors=new Palette(this);
        pendingEnable=state!=null?state.getBoolean("pending_enable",false):ACTION_ENABLE.equals(getIntent().getAction());
        getIntent().setAction(null);
        backgroundGuide=new BackgroundGuide(this);
        recover();IconCatalog.applyLauncher(this,prefs.icon());build();
        registerReceiver(stateReceiver,new IntentFilter(RotationService.STATE),Context.RECEIVER_NOT_EXPORTED);receiverRegistered=true;
    }
    private void recover(){
        if(!RotationService.running&&prefs.store.getBoolean("session",false)&&Settings.System.canWrite(this)){
            try{new RotationController(this,prefs).restore();prefs.desired(false);}
            catch(RuntimeException e){android.util.Log.w("Arotation","Restore pending",e);}
        }
    }
    @Override protected void onResume(){
        super.onResume();recover();refreshState();backgroundGuide.refresh();
        if(pendingEnable){pendingEnable=false;setEnabled(true);}
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putBoolean("pending_enable",pendingEnable);super.onSaveInstanceState(state);
    }
    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);setIntent(i);
        pendingEnable=ACTION_ENABLE.equals(i.getAction());i.setAction(null);refreshState();
    }
    @Override protected void onDestroy(){if(receiverRegistered)unregisterReceiver(stateReceiver);backgroundGuide.dismiss();super.onDestroy();}
    private void build(){
        getWindow().setNavigationBarContrastEnforced(false);getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(colors.background);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        scroll.setPadding(dp(24),dp(20),dp(24),dp(24));scroll.setVerticalScrollBarEnabled(false);
        int width=Math.min(getResources().getConfiguration().screenWidthDp,600);
        root.addView(scroll,new FrameLayout.LayoutParams(dp(width),-1,Gravity.CENTER_HORIZONTAL));
        LinearLayout content=column();scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        LinearLayout header=row();TextView title=text("Arotation",24,colors.ink,true);title.setAccessibilityHeading(true);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton more=iconButton(R.drawable.ic_tabler_dots,"更多选项");more.setOnClickListener(this::more);
        header.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(header);
        space(content,24);
        power=toggle(content,"悬浮按钮",false,value->{if(!refreshing)setEnabled(value);});
        permissionArea=column();content.addView(permissionArea);
        error=text("",13,colors.error,false);error.setPadding(0,dp(6),0,dp(6));error.setVisibility(View.GONE);content.addView(error);
        LinearLayout shortcuts=row();
        addTile=button(getString(R.string.add_tile));addTile.setTextColor(colors.accent);
        addTile.setMinHeight(dp(48));addTile.setOnClickListener(v->requestTile());
        shortcuts.addView(addTile,new LinearLayout.LayoutParams(0,-2,1));
        Button background=button(getString(R.string.background_title));background.setTextColor(colors.accent);
        background.setMinHeight(dp(48));background.setOnClickListener(v->backgroundGuide.show());
        shortcuts.addView(background,new LinearLayout.LayoutParams(0,-2,1));content.addView(shortcuts);
        space(content,12);divider(content);space(content,24);
        content.addView(text("图标",14,colors.muted,false));space(content,12);icons(content,width-48);space(content,20);
        LinearLayout size=row();size.addView(text("大小",16,colors.ink,false),new LinearLayout.LayoutParams(0,-2,1));
        size.addView(segments(new String[]{"小","中","大"},prefs.size(),i->prefs.store.edit().putInt("size",i).apply()),new LinearLayout.LayoutParams(dp(180),dp(48)));
        content.addView(size,new LinearLayout.LayoutParams(-1,dp(56)));space(content,12);
        LinearLayout opacity=row();opacity.addView(text("闲置透明度",16,colors.ink,false),new LinearLayout.LayoutParams(0,-2,1));
        opacityLabel=text(getString(R.string.percent,100-prefs.opacity()),14,colors.muted,false);opacity.addView(opacityLabel);content.addView(opacity,new LinearLayout.LayoutParams(-1,dp(32)));
        SeekBar slider=new SeekBar(this);slider.setMin(15);slider.setMax(75);slider.setProgress(100-prefs.opacity());slider.setContentDescription("闲置透明度");
        slider.setProgressTintList(ColorStateList.valueOf(colors.accent));slider.setThumbTintList(ColorStateList.valueOf(colors.accent));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(colors.line));content.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int value,boolean user){opacityLabel.setText(getString(R.string.percent,value));}
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){prefs.store.edit().putInt("opacity",100-b.getProgress()).apply();}
        });
        space(content,12);divider(content);space(content,12);
        toggle(content,"自动贴边",prefs.snap(),value->{prefs.store.edit().putBoolean("snap",value).apply();tuck.setEnabled(value);});
        tuck=toggle(content,"贴边收起",prefs.store.getBoolean("hide",false),value->prefs.store.edit().putBoolean("hide",value).apply());tuck.setEnabled(prefs.snap());
        setContentView(root);
        getWindow().getInsetsController().setSystemBarsAppearance(colors.dark?0:
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        root.requestApplyInsets();
    }
    private void icons(LinearLayout parent,int widthDp){
        iconButtons=new ImageButton[IconCatalog.DRAWABLES.length];int columns=widthDp>=400?6:3;
        for(int start=0;start<iconButtons.length;start+=columns){
            LinearLayout line=row();
            for(int i=start;i<Math.min(start+columns,iconButtons.length);i++){
                final int index=i;ImageButton button=iconButton(IconCatalog.DRAWABLES[i],"图标："+IconCatalog.NAMES[i]);
                button.setOnClickListener(v->{
                    try{IconCatalog.applyLauncher(this,index);prefs.store.edit().putInt("icon",index).apply();styleIcons();}
                    catch(RuntimeException e){Toast.makeText(this,"图标未更改，请重试",Toast.LENGTH_SHORT).show();}
                });
                iconButtons[i]=button;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(56),1);
                if(i>start)lp.leftMargin=dp(8);line.addView(button,lp);
            }
            parent.addView(line);if(start+columns<iconButtons.length)space(parent,8);
        }
        styleIcons();
    }
    private void styleIcons(){
        for(int i=0;i<iconButtons.length;i++){
            boolean selected=i==prefs.icon();ImageButton b=iconButtons[i];b.setSelected(selected);
            b.setImageTintList(ColorStateList.valueOf(selected?colors.accent:colors.muted));
            GradientDrawable bg=round(selected?colors.tonal:colors.background,12);
            bg.setStroke(dp(1),selected?colors.accent:colors.line);b.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.line),bg,null));
            b.setStateDescription(selected?"已选中":"未选中");
        }
    }
    private void setEnabled(boolean enabled){
        if(!enabled){if(RotationService.running)startService(new Intent(this,RotationService.class).setAction(RotationService.STOP));return;}
        if(!Settings.canDrawOverlays(this)){openPermission(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);refreshState();return;}
        if(!Settings.System.canWrite(this)){openPermission(Settings.ACTION_MANAGE_WRITE_SETTINGS);refreshState();return;}
        try{
            prefs.store.edit().remove("last_error").apply();startForegroundService(new Intent(this,RotationService.class).setAction(RotationService.START));
            if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!prefs.store.getBoolean("asked_notification",false)){
                prefs.store.edit().putBoolean("asked_notification",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
            }
        }catch(RuntimeException e){error.setText("无法启用，请检查系统授权");error.setVisibility(View.VISIBLE);refreshing=true;power.setChecked(false);refreshing=false;}
    }
    private void refreshState(){
        if(power==null)return;refreshing=true;power.setChecked(RotationService.running);refreshing=false;
        String message=prefs.store.getString("last_error",null);error.setText(message);error.setVisibility(message==null?View.GONE:View.VISIBLE);
        permissionArea.removeAllViews();
        if(!Settings.canDrawOverlays(this))permission("悬浮显示",Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        if(!Settings.System.canWrite(this))permission("修改系统设置",Settings.ACTION_MANAGE_WRITE_SETTINGS);
    }
    private void permission(String title,String action){
        LinearLayout line=row();line.addView(text(title,14,colors.muted,false),new LinearLayout.LayoutParams(0,-2,1));
        Button allow=button("允许");allow.setTextColor(colors.accent);allow.setOnClickListener(v->openPermission(action));
        line.addView(allow,new LinearLayout.LayoutParams(dp(72),dp(48)));permissionArea.addView(line,new LinearLayout.LayoutParams(-1,dp(52)));
    }
    private void openPermission(String action){
        try{startActivity(new Intent(action,Uri.parse("package:"+getPackageName())));}
        catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    }
    private void requestTile(){
        addTile.setEnabled(false);
        try{
            getSystemService(StatusBarManager.class).requestAddTileService(
                new ComponentName(this,RotationTileService.class),getString(R.string.app_name),
                Icon.createWithResource(this,IconCatalog.DRAWABLES[prefs.icon()]),getMainExecutor(),result->{
                    if(isFinishing()||isDestroyed())return;
                    addTile.setEnabled(true);
                    int message=switch(result){
                        case StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added;
                        case StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added;
                        default -> R.string.tile_add_manually;
                    };
                    Toast.makeText(this,message,Toast.LENGTH_LONG).show();
                });
        }catch(RuntimeException e){
            addTile.setEnabled(true);Toast.makeText(this,R.string.tile_add_manually,Toast.LENGTH_LONG).show();
        }
    }
    private void more(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,"横屏方向");menu.getMenu().add(0,2,1,"重置位置");menu.getMenu().add(0,3,2,"使用方法");menu.getMenu().add(0,4,3,"通知设置");menu.getMenu().add(0,5,4,"素材许可");
        menu.setOnMenuItemClickListener(item->{
            switch(item.getItemId()){
                case 1:new AlertDialog.Builder(this).setTitle("横屏方向").setSingleChoiceItems(new String[]{"默认","反向"},prefs.reverseLandscape()?1:0,(dialog,i)->{prefs.store.edit().putBoolean("reverse",i==1).apply();dialog.dismiss();}).setNegativeButton("取消",null).show();break;
                case 2:prefs.resetPositions();if(RotationService.running)startService(new Intent(this,RotationService.class).setAction(RotationService.RESET));Toast.makeText(this,"位置已重置",Toast.LENGTH_SHORT).show();break;
                case 3:new AlertDialog.Builder(this).setTitle("使用方法").setMessage("轻点切换横竖屏，按住拖动。\n\n图标同时用于悬浮按钮和桌面。\n\n停用后恢复之前的旋转设置。").setPositiveButton("关闭",null).show();break;
                case 4:startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));break;
                case 5:licenses();break;
                default:return false;
            }return true;
        });menu.show();
    }
    private void licenses(){
        StringBuilder text=new StringBuilder("Arotation ").append(getString(R.string.app_version)).append("\n\n图标：Tabler Icons\n配色：Radix Colors\n\n");
        try{
            for(String file:new String[]{"Tabler-Icons-MIT.txt","Radix-Colors-MIT.txt"}){
                try(java.io.InputStream in=getAssets().open("licenses/"+file)){text.append(new String(in.readAllBytes(),StandardCharsets.UTF_8)).append("\n\n");}
            }
            new AlertDialog.Builder(this).setTitle("素材许可").setMessage(text).setPositiveButton("关闭",null).show();
        }catch(java.io.IOException e){Toast.makeText(this,"无法读取许可文件",Toast.LENGTH_SHORT).show();}
    }
    private Switch toggle(LinearLayout parent,String label,boolean checked,java.util.function.Consumer<Boolean> action){
        LinearLayout row=row();TextView name=text(label,16,colors.ink,false);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setId(View.generateViewId());name.setLabelFor(toggle.getId());toggle.setContentDescription(label);toggle.setChecked(checked);
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{colors.accent,colors.muted}));
        toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{colors.tonal,colors.line}));
        row.addView(toggle,new LinearLayout.LayoutParams(dp(56),dp(48)));toggle.setOnCheckedChangeListener((b,v)->action.accept(v));row.setOnClickListener(v->{if(toggle.isEnabled())toggle.performClick();});
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(56)));return toggle;
    }
    private LinearLayout segments(String[] labels,int selected,java.util.function.IntConsumer action){
        LinearLayout row=row();Button[] buttons=new Button[labels.length];
        for(int i=0;i<labels.length;i++){
            final int index=i;Button b=button(labels[i]);buttons[i]=b;segmentStyle(b,i==selected);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)lp.leftMargin=dp(8);row.addView(b,lp);
            b.setOnClickListener(v->{for(int n=0;n<buttons.length;n++)segmentStyle(buttons[n],n==index);action.accept(index);});
        }return row;
    }
    private void segmentStyle(Button button,boolean selected){button.setSelected(selected);button.setTextColor(selected?colors.selectedText:colors.muted);button.setBackground(ripple(selected?colors.tonal:colors.background,10));button.setStateDescription(selected?"已选中":"未选中");}
    private ImageButton iconButton(int drawable,String description){ImageButton b=new ImageButton(this);b.setImageResource(drawable);b.setImageTintList(ColorStateList.valueOf(colors.muted));b.setScaleType(ImageView.ScaleType.CENTER);b.setContentDescription(description);b.setBackground(ripple(colors.background,12));return b;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String value,int size,int color,boolean medium){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);b.setStateListAnimator(null);b.setTextColor(colors.ink);b.setBackground(ripple(colors.background,10));return b;}
    private android.graphics.drawable.Drawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(colors.line),round(color,radius),null);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void space(LinearLayout p,int height){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private void divider(LinearLayout p){View v=new View(this);v.setBackgroundColor(colors.line);p.addView(v,new LinearLayout.LayoutParams(-1,Math.max(1,dp(.5f))));}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
