package dev.padbridge.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

public final class MainActivity extends Activity {
    private static final int NOTIFICATIONS_REQUEST=10;
    private LinearLayout body,nav,errorPanel;
    private int tab;
    private TextView statusTitle,statusNote,errorMessage;
    private Button primary,detection,cancel;
    private String lastDevices="";
    private AdBanner ads;
    private AlertDialog rootConsent;
    private boolean waitingForNotifications,startSubmitted;
    private long submittedAfter;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,700);}};

    public void onCreate(Bundle saved){
        super.onCreate(saved);ads=new AdBanner(this);
        LinearLayout root=Ui.vertical(this);root.setBackgroundColor(Ui.BG);setContentView(root);Ui.insets(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18),Ui.dp(this,8));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_padbridge_store);header.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));
        LinearLayout brand=Ui.vertical(this);brand.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);brand.addView(Ui.text(this,"PadBridge",25,Ui.TEXT));brand.addView(Ui.text(this,BuildConfig.VERSION_NAME,11,Ui.MUTED));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(Ui.text(this,BuildConfig.ROOT_START_ALLOWED?"ROOT BETA":"ADS DEMO",12,Ui.ACCENT));root.addView(header);
        errorPanel=Ui.vertical(this);errorPanel.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),Ui.dp(this,8));errorPanel.setBackgroundColor(Ui.CARD);
        errorMessage=Ui.add(errorPanel,"",14,Ui.WARN);errorMessage.setMaxLines(4);errorMessage.setEllipsize(android.text.TextUtils.TruncateAt.END);errorMessage.setOnClickListener(v->diagnostics());
        TextView errorLink=Ui.text(this,"View diagnostics",13,Ui.ACCENT);errorLink.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,8));errorLink.setOnClickListener(v->diagnostics());errorPanel.addView(errorLink);errorPanel.setVisibility(View.GONE);root.addView(errorPanel);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=Ui.vertical(this);body.setPadding(Ui.dp(this,20),Ui.dp(this,16),Ui.dp(this,20),Ui.dp(this,16));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);nav.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,8));root.addView(nav);draw(0);
    }

    private void drawNav(){
        nav.removeAllViews();String[] names={"Home","Controllers","Profiles","Help"};
        for(int n=0;n<4;n++){final int index=n;TextView item=Ui.text(this,names[n],12,n==tab?Ui.ACCENT:Ui.MUTED);item.setGravity(Gravity.CENTER);item.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,16));item.setOnClickListener(v->draw(index));nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));}
    }
    private void draw(int page){
        tab=page;if(ads!=null)ads.pause();body.removeAllViews();statusTitle=null;statusNote=null;primary=null;detection=null;cancel=null;drawNav();
        if(page==0)home();else if(page==1)controllers();else if(page==2)profiles();else help();refresh();
    }
    private String selected(JSONArray snapshot){String id=getSharedPreferences("app",0).getString("selected","");if(id.isEmpty()&&snapshot.length()>0&&snapshot.optJSONObject(0)!=null)id=snapshot.optJSONObject(0).optString("signature");return id;}
    private JSONObject selectedDevice(JSONArray snapshot){
        JSONObject first=null;String id=selected(snapshot),path=getSharedPreferences("app",0).getString("selectedPath","");
        for(int n=0;n<snapshot.length();n++){JSONObject d=snapshot.optJSONObject(n);if(d==null||!d.optString("signature").equals(id))continue;if(first==null)first=d;if(d.optString("path").equals(path))return d;}return first;
    }
    private boolean rootBusy(){return waitingForNotifications||startSubmitted||"requesting_root".equals(BridgeService.phase)||"checking".equals(BridgeService.phase);}

    private void home(){
        Ui.add(body,"Your controller.\nMore possibilities.",30,Ui.TEXT);
        if(!BuildConfig.ROOT_START_ALLOWED){
            demoNotice(body);Ui.button(body,"Test the Android controller",false,()->startActivity(new Intent(this,TesterActivity.class)));ads.attach(body);return;
        }
        Ui.add(body,"Connect directly on a phone that already has root access.",15,Ui.MUTED);
        LinearLayout status=Ui.card(body);statusTitle=Ui.add(status,"",22,Ui.ACCENT);statusNote=Ui.add(status,"",14,Ui.MUTED);
        primary=Ui.button(status,"",true,this::primaryAction);cancel=Ui.button(status,"Cancel connection",false,this::stopBridge);
        Ui.button(status,"Test controller",false,()->startActivity(new Intent(this,TesterActivity.class)));
        LinearLayout next=Ui.card(body);Ui.add(next,"Three steps to play",19,Ui.TEXT);
        Ui.add(next,"01  Connect your controller to the phone over Bluetooth or USB.\n\n02  Allow root for PadBridge Root in your root manager. In Magisk 31.1: Superuser → + → PadBridge Root. Then tap Connect using root → Start bridge.\n\n03  Select the controller, check its profile, and tap Start playing.",14,Ui.MUTED);
        LinearLayout compatibility=Ui.card(body);Ui.add(compatibility,"Games and controller profiles",18,Ui.TEXT);
        Ui.add(compatibility,"Designed for games with native controller input, including Call of Duty: Mobile (CODM). Check your buttons and sticks in Test controller, adjust your profile, and share your setup through GitHub feedback.",14,Ui.MUTED);
    }
    private void primaryAction(){
        if(!BuildConfig.ROOT_START_ALLOWED||rootBusy())return;
        if(BridgeService.active){BridgeService.action(this,"pause",null);return;}
        if(BridgeService.ready){JSONArray snapshot=BridgeService.devices;if(selectedDevice(snapshot)==null){draw(1);return;}BridgeService.action(this,"start",selected(snapshot));return;}
        showRootConsent();
    }
    private void demoNotice(LinearLayout host){
        LinearLayout demo=Ui.card(host);Ui.add(demo,"Advertising demonstration",20,Ui.TEXT);
        Ui.add(demo,"This build is only for the test banner. Root requests and system bridge startup are disabled. The tester shows a controller that Android already detects.",14,Ui.MUTED);
    }

    private void controllers(){
        JSONArray snapshot=BridgeService.devices;Ui.add(body,"Choose a controller",27,Ui.TEXT);
        if(!BuildConfig.ROOT_START_ALLOWED){demoNotice(body);androidControllers(body);return;}
        Ui.add(body,"PadBridge detects dual-stick controllers. Settings are kept separately for each model.",14,Ui.MUTED);
        detection=Ui.button(body,"",true,()->{if(rootBusy())return;if(BridgeService.ready)BridgeService.action(this,"scan",null);else showRootConsent();});
        cancel=Ui.button(body,"Cancel connection",false,this::stopBridge);
        if(snapshot.length()==0){LinearLayout empty=Ui.card(body);Ui.add(empty,"No controllers available yet",18,Ui.TEXT);Ui.add(empty,"Connect your controller and allow root for PadBridge Root in your root manager. In Magisk 31.1, grant access beforehand: Superuser → + → PadBridge Root. Refresh the list after starting the bridge. Any error appears above.",14,Ui.MUTED);androidControllers(empty);}
        JSONObject current=selectedDevice(snapshot);String currentPath=current==null?"":current.optString("path"),currentId=selected(snapshot);
        for(int n=0;n<snapshot.length();n++){JSONObject d=snapshot.optJSONObject(n);if(d==null)continue;String id=d.optString("signature");boolean chosen=id.equals(currentId)&&d.optString("path").equals(currentPath);LinearLayout card=Ui.card(body);Ui.add(card,d.optString("name"),19,Ui.TEXT);Ui.add(card,chosen?"Selected for play":"Detected · check its profile",13,chosen?Ui.ACCENT:Ui.MUTED);
            Ui.button(card,"Select",true,()->{getSharedPreferences("app",0).edit().putString("selected",id).putString("selectedPath",d.optString("path")).apply();draw(0);});Ui.button(card,"Configure profile",false,()->profile(id));}
    }
    private void androidControllers(LinearLayout host){for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d!=null&&(d.supportsSource(InputDevice.SOURCE_GAMEPAD)||d.supportsSource(InputDevice.SOURCE_JOYSTICK)))Ui.add(host,"Android sees: "+d.getName(),13,Ui.MUTED);}}
    private void profiles(){
        Ui.add(body,"Your profiles",27,Ui.TEXT);Ui.add(body,"Buttons, axes, and dead zones for each controller.",14,Ui.MUTED);int count=0;
        for(String id:getSharedPreferences("profiles",0).getAll().keySet()){JSONObject p=ProfileStore.get(this,id);if(!p.has("device"))continue;count++;LinearLayout card=Ui.card(body);Ui.add(card,p.optString("name","Profile"),19,Ui.TEXT);Ui.add(card,"Dead zone: "+Math.round(p.optDouble("deadzone",.06)*100)+"%",14,Ui.MUTED);Ui.button(card,"Edit",false,()->profile(id));}
        if(count==0){LinearLayout empty=Ui.card(body);Ui.add(empty,BuildConfig.ROOT_START_ALLOWED?"Your first profile appears when a controller is detected.":"System detection and bridge startup are disabled in the advertising demo.",16,Ui.MUTED);if(BuildConfig.ROOT_START_ALLOWED)Ui.button(empty,"Find a controller",true,()->draw(1));}
    }
    private void profile(String id){if(BuildConfig.ROOT_START_ALLOWED&&BridgeService.active)BridgeService.action(this,"pause",null);startActivity(new Intent(this,ProfileActivity.class).putExtra("id",id));}

    private void help(){
        Ui.add(body,"Connection and help",27,Ui.TEXT);
        if(BuildConfig.ROOT_START_ALLOWED){
            LinearLayout access=Ui.card(body);Ui.add(access,"What root access is needed?",19,Ui.TEXT);
            Ui.add(access,"The phone must already have root configured, for example through Magisk. PadBridge does not install root or change the firmware. In Magisk 31.1, grant access beforehand in Superuser → + → PadBridge Root. The debug build is named PadBridge Root Beta. Other versions and managers may show a request after the bridge starts.",14,Ui.MUTED);
            Ui.button(access,"How to connect",false,this::rootHelp);
            LinearLayout denied=Ui.card(body);Ui.add(denied,"If root is not allowed",19,Ui.TEXT);
            Ui.add(denied,"Check that your root manager allows PadBridge Root, then try again. Magisk 31.1 does not show an interactive request: grant access beforehand through Superuser → +. If root is absent or the firmware blocks input-device access, connection cannot complete. PadBridge and its diagnostics show the reason.",14,Ui.MUTED);
            LinearLayout sessions=Ui.card(body);Ui.add(sessions,"Pause and stop",19,Ui.TEXT);
            Ui.add(sessions,"Pause releases the controller and keeps the bridge ready. Fully stop ends the bridge. After a phone reboot or a new session, start the connection in PadBridge again.",14,Ui.MUTED);
        }else demoNotice(body);
        LinearLayout privacy=Ui.card(body);Ui.add(privacy,"Privacy",19,Ui.TEXT);Ui.add(privacy,privacyText(),14,Ui.MUTED);Ui.button(privacy,"Learn more",false,()->Ui.message(this,"PadBridge privacy",privacyText()+"\n\n"+"Uninstalling removes local profiles. Profile export and diagnostic sharing happen only when you request them."));
        LinearLayout support=Ui.card(body);Ui.add(support,"Connection help",19,Ui.TEXT);Ui.button(support,"View diagnostics",false,this::diagnostics);Ui.button(support,"Share diagnostics",false,()->{try{ReportProvider.share(this,report());}catch(Exception e){Ui.toast(this,e.getMessage());}});Ui.add(support,"PadBridge is not a Microsoft or Activision product. Support depends on the phone, controller, and game version.",13,Ui.MUTED);
        Ui.button(body,"Fully stop PadBridge",false,this::stopBridge);
    }
    private String privacyText(){return AdBanner.privacySummary()+(BuildConfig.ROOT_START_ALLOWED?"\n\n"+"The system bridge runs with root at your request and reads the controller you select. The app does not read the screen, messages, or game files.":"");}
    private void rootHelp(){Ui.message(this,"Connect on the phone","1. Connect your controller over Bluetooth or USB.\n\n2. In your root manager, allow the installed PadBridge Root app. In Magisk 31.1: Superuser → + (Grant Superuser) → PadBridge Root or PadBridge Root Beta. This Magisk version has no interactive root prompt.\n\n3. On Home, tap Connect using root, read the explanation, and tap Start bridge. Other versions and managers may present a request at startup.\n\n4. Once access is checked, select the controller and start translation. Use Pause between games.");}

    private void showRootConsent(){
        if(!BuildConfig.ROOT_START_ALLOWED||rootBusy()||(rootConsent!=null&&rootConsent.isShowing()))return;
        rootConsent=new AlertDialog.Builder(this).setTitle("Root access for PadBridge")
            .setMessage("The phone must already have root configured, for example through Magisk. PadBridge does not install root or change the firmware.\n\nRoot grants broad system privileges. Allow it only for a PadBridge build you trust.\n\nGrant access to the installed PadBridge Root app in your root manager. In Magisk 31.1: Superuser → + → PadBridge Root (the debug build is PadBridge Root Beta). Other versions and managers may show a request after startup.\n\nTapping Start bridge checks access to the connected controller and virtual input device creation. You can cancel the connection or fully stop the bridge.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Start bridge",(dialog,which)->requestRootAfterConsent()).create();
        rootConsent.setOnDismissListener(dialog->rootConsent=null);rootConsent.show();
    }
    private void requestRootAfterConsent(){
        if(!BuildConfig.ROOT_START_ALLOWED||rootBusy())return;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){waitingForNotifications=true;refresh();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS_REQUEST);return;}
        startRootBridge();
    }
    private void startRootBridge(){
        if(!BuildConfig.ROOT_START_ALLOWED||isFinishing()||isDestroyed())return;
        submittedAfter=BridgeService.rootRequests;startSubmitted=true;refresh();
        try{BridgeService.action(this,"prepare_root",null);}catch(RuntimeException error){startSubmitted=false;Ui.message(this,"Unable to start connection",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());refresh();}
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==NOTIFICATIONS_REQUEST&&waitingForNotifications){waitingForNotifications=false;startRootBridge();}}
    private void stopBridge(){boolean started=startSubmitted||BridgeService.instance!=null||"requesting_root".equals(BridgeService.phase)||"checking".equals(BridgeService.phase);waitingForNotifications=false;startSubmitted=false;if(started)BridgeService.action(this,"stop",null);refresh();}

    private String report(){
        String rootStatus=!BuildConfig.ROOT_START_ALLOWED?"disabled in advertising demo":BridgeService.helperUid==0?"confirmed (uid 0)":rootBusy()?"request/check in progress":"not confirmed";
        return "PadBridge "+getString(R.string.app_name)+" "+BuildConfig.VERSION_NAME+" / "+BuildConfig.FLAVOR+"\nAndroid "+Build.VERSION.RELEASE+" / "+Build.MODEL+"\nMode: "+(BuildConfig.ROOT_START_ALLOWED?"root":"ads_demo")+"\nBackend: "+BridgeService.backend+"\nRoot helper UID: "+BridgeService.helperUid+"\nRoot status: "+rootStatus+"\nState: "+BridgeService.phase+"\nControllers: "+BridgeService.devices.toString()+"\nError: "+BridgeService.lastError+"\nEvents:\n"+BridgeService.log();
    }
    private void diagnostics(){Ui.message(this,"Diagnostics",report());}
    private void refresh(){
        String phase=BridgeService.phase;
        if(startSubmitted&&BridgeService.rootRequests!=submittedAfter)startSubmitted=false;
        boolean busy=rootBusy();String busyTitle=waitingForNotifications?"Notification permission":"checking".equals(phase)?"Checking controller access":"Starting the system bridge";
        String error=BridgeService.lastError;errorMessage.setText(error);errorPanel.setVisibility(error.isEmpty()?View.GONE:View.VISIBLE);
        if(statusTitle!=null){
            statusTitle.setText(busy?busyTitle:BridgeService.active?"Controller connected":BridgeService.ready?"Ready to play":"error".equals(phase)?"Connection did not complete":"Connect your controller");
            JSONArray snapshot=BridgeService.devices;JSONObject device=selectedDevice(snapshot);
            statusNote.setText(busy?("checking".equals(phase)?"Checking system access and virtual controller readiness. You can cancel the connection.":waitingForNotifications?"Respond to the Android prompt. Connection can continue without notification permission.":"PadBridge Root must be allowed in your root manager. Magisk 31.1 requires granting access beforehand in Superuser → +; other managers may show a request. You can cancel the connection."):BridgeService.active?"Controls are being sent to the virtual gamepad.":BridgeService.ready?(device==null?"Choose a connected controller.":device.optString("name")):"Root must already be installed and your root manager must allow PadBridge Root. In Magisk 31.1, grant access beforehand through Superuser → +.");
            primary.setEnabled(!busy);primary.setAlpha(busy?.55f:1f);primary.setText(busy?busyTitle:BridgeService.active?"Pause translation":BridgeService.ready?"Start playing":"Connect using root");
        }
        if(detection!=null){detection.setEnabled(!busy);detection.setAlpha(busy?.55f:1f);detection.setText(busy?busyTitle:BridgeService.ready?"Refresh list":"Connect using root");}
        if(cancel!=null)cancel.setVisibility(busy?View.VISIBLE:View.GONE);
        String current=BridgeService.devices.toString();if(!current.equals(lastDevices)){lastDevices=current;if(tab==1||tab==2)draw(tab);}
    }
    public void onResume(){super.onResume();draw(tab);handler.post(tick);}
    protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
    public void onPause(){handler.removeCallbacks(tick);if(ads!=null)ads.pause();super.onPause();}
    public void onDestroy(){waitingForNotifications=false;startSubmitted=false;if(rootConsent!=null)rootConsent.dismiss();if(ads!=null)ads.destroy();super.onDestroy();}
}
