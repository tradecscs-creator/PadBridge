package dev.padbridge.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.concurrent.*;

/** User-started foreground session. Root is requested only by prepare_root. */
public final class BridgeService extends Service {
    private static final Object LIFECYCLE=new Object();
    private static final long ROOT_DEADLINE_MS=120000;
    public static volatile BridgeService instance;
    public static volatile boolean ready,active;
    public static volatile String activeSignature="",lastError="",phase="off",backend="root",rootState="unknown";
    public static volatile int helperUid=-1;
    static volatile long rootRequests;
    public static volatile JSONArray devices=new JSONArray();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService writer=Executors.newSingleThreadExecutor();
    private volatile boolean closing,waiting,userStopped;
    private boolean authenticated;
    private volatile long attempt;
    private ServerSocket server;
    private Socket socket;
    private PrintWriter commands;
    private RootLauncher rootLauncher;
    private static final StringBuilder log=new StringBuilder();

    public static synchronized String log(){return log.toString();}
    private static synchronized void record(String event){
        log.append(event).append('\n');if(log.length()>12000)log.delete(0,log.length()-10000);
    }

    public static void action(Context context,String action,String signature){
        Intent intent=new Intent(context,BridgeService.class).setAction(action);
        if(signature!=null){
            intent.putExtra("signature",signature);
            String selected=context.getSharedPreferences("app",0).getString("selectedPath","");
            JSONObject first=null;JSONArray known=devices;
            for(int n=0;n<known.length();n++){
                JSONObject device=known.optJSONObject(n);
                if(device==null||!signature.equals(device.optString("signature")))continue;
                if(first==null)first=device;
                if(selected.equals(device.optString("path"))){first=device;break;}
            }
            if(first!=null)intent.putExtra("path",first.optString("path"));
        }
        context.startForegroundService(intent);
    }

    public void onCreate(){
        super.onCreate();
        synchronized(LIFECYCLE){
            instance=this;ready=false;active=false;activeSignature="";helperUid=-1;
            phase="off";backend="root";rootState="unknown";
        }
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(
            "bridge","PadBridge controller",NotificationManager.IMPORTANCE_LOW));
        if(Build.VERSION.SDK_INT>=34)startForeground(1,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1,notification());
    }

    public static String title(){
        if(active)return "Controller connected";
        if(ready)return "Ready to play";
        if(phase.equals("requesting_root"))return "Starting root bridge";
        if(phase.equals("checking"))return "Checking the system bridge";
        if(phase.equals("error"))return "Bridge connection needs attention";
        return "Connect using root";
    }

    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,BridgeService.class).setAction("pause"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,BridgeService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"bridge").setSmallIcon(R.drawable.ic_padbridge_status)
            .setContentTitle("PadBridge · "+title())
            .setContentText(active?"Controller translation is on":
                "Root bridge session · stop at any time")
            .setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Pause",pause).build())
            .addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
    }

    private boolean ownsState(){return instance==this&&!closing;}
    private boolean ownsAttempt(long run){return ownsState()&&attempt==run;}
    private void changed(String event){
        if(!ownsState())return;record(event);
        main.post(()->{if(ownsState())getSystemService(NotificationManager.class).notify(1,notification());});
    }

    public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"stop":intent.getAction();
        if("stop".equals(action)){userStopped=true;stopSelf();return START_NOT_STICKY;}
        if("prepare_root".equals(action)){rootRequests++;beginRoot();}
        else if("scan".equals(action)){if(ready)send(json("scan"));else if(!waiting)stopSelf();}
        else if("pause".equals(action)){if(ready)send(json("pause"));else if(!waiting)stopSelf();}
        else if("start".equals(action)&&ready){
            try{
                String signature=intent.getStringExtra("signature"),path=intent.getStringExtra("path");
                boolean found=false;JSONArray known=devices;
                for(int n=0;n<known.length();n++){
                    JSONObject device=known.getJSONObject(n);
                    if(device.getString("signature").equals(signature)&&device.getString("path").equals(path))found=true;
                }
                if(!found)throw new JSONException("Choose a connected controller");
                lastError="";
                send(new JSONObject().put("cmd","start").put("signature",signature).put("path",path)
                    .put("profile",ProfileStore.get(this,signature)).toString());
            }catch(Exception invalid){lastError=invalid.getMessage();changed("invalid-profile");}
        }else if("start".equals(action)){
            if(!waiting)stopSelf();
        }else if(!"start".equals(action)&&!"prepare_root".equals(action)){
            // External/legacy intents never become an implicit privilege request.
            if(!ready&&!waiting)stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void beginRoot(){
        if(!BuildConfig.ROOT_START_ALLOWED){
            lastError="The root bridge is disabled in the advertising demo. Use PadBridge Root.";
            phase="error";rootState="disabled";stopSelf();return;
        }
        final long run;
        synchronized(LIFECYCLE){
            if(!ownsState())return;
            if(ready){send(json("scan"));return;}
            if(waiting)return;
            run=++attempt;waiting=true;authenticated=false;userStopped=false;lastError="";devices=new JSONArray();
            phase="requesting_root";rootState="requesting";helperUid=-1;
        }
        changed("root-requested");
        new Thread(()->listen(run),"padbridge-root-session").start();
    }

    private static String json(String command){return "{\"cmd\":\""+command+"\"}";}

    private void listen(long run){
        ServerSocket listener=null;Socket client=null;RootLauncher launcher=null;
        try{
            byte[] random=new byte[32];new SecureRandom().nextBytes(random);
            String secret=Base64.encodeToString(random,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
            listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));listener.setSoTimeout(1000);
            launcher=new RootLauncher(getApplicationInfo().sourceDir,listener.getLocalPort(),secret,result->rootExited(run,result));
            synchronized(LIFECYCLE){if(!ownsAttempt(run))return;server=listener;rootLauncher=launcher;}
            launcher.start();
            long deadline=SystemClock.elapsedRealtime()+ROOT_DEADLINE_MS;
            while(ownsAttempt(run)&&SystemClock.elapsedRealtime()<deadline){
                try{client=listener.accept();}catch(SocketTimeoutException retry){continue;}
                synchronized(LIFECYCLE){if(!ownsAttempt(run))return;socket=client;}
                client.setSoTimeout(3000);client.setTcpNoDelay(true);
                Reader incoming=new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8);
                String provided=null;
                long authDeadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.min(3000,Math.max(1,deadline-SystemClock.elapsedRealtime())));
                try{provided=Ipc.line(incoming,64,authDeadline);}catch(IOException rejected){/* Reject only this client. */}
                if(provided==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.US_ASCII),provided.getBytes(StandardCharsets.US_ASCII))){
                    closeQuietly(client);synchronized(LIFECYCLE){if(socket==client)socket=null;}client=null;continue;
                }
                synchronized(LIFECYCLE){if(!ownsAttempt(run)||!lastError.isEmpty())return;authenticated=true;}
                long preflightMs=Math.min(15000,Math.max(1,deadline-SystemClock.elapsedRealtime()));
                client.setSoTimeout((int)preflightMs);
                PrintWriter channel=new PrintWriter(new OutputStreamWriter(client.getOutputStream(),StandardCharsets.UTF_8),true);
                synchronized(LIFECYCLE){
                    if(!ownsAttempt(run))return;
                    commands=channel;server=null;phase="checking";
                }
                closeQuietly(listener);listener=null;changed("root-preflight");
                read(incoming,client,run,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(preflightMs));return;
            }
            synchronized(LIFECYCLE){if(ownsAttempt(run)){
                lastError="The root request timed out. Check your root manager and retry.";
                rootState="timeout";phase="error";
            }}
            changed("root-timeout");
        }catch(Exception failure){
            synchronized(LIFECYCLE){if(ownsAttempt(run)&&lastError.isEmpty()){
                lastError="System bridge connection failed: "+failure.getClass().getSimpleName()+": "+failure.getMessage();
                rootState="failed";
            }}
            changed("root-session-error");
        }finally{
            // Close sockets first. EOF makes the helper release evdev/UHID,
            // including when a root manager detached its process from su.
            closeQuietly(client);closeQuietly(listener);if(launcher!=null)launcher.close();
            synchronized(LIFECYCLE){
                if(socket==client)socket=null;if(server==listener)server=null;
                if(rootLauncher==launcher)rootLauncher=null;
                if(ownsAttempt(run)){
                    commands=null;waiting=false;authenticated=false;ready=false;active=false;activeSignature="";helperUid=-1;
                    if(lastError.isEmpty()&&!userStopped){lastError="The root bridge stopped. Connect it again.";rootState="disconnected";}
                    phase=lastError.isEmpty()?"off":"error";
                }
            }
            if(ownsAttempt(run))main.post(()->{if(ownsAttempt(run)&&!waiting)stopSelf();});
        }
    }

    private void rootExited(long run,RootLauncher.Result result){
        ServerSocket listener;Socket untrusted;
        synchronized(LIFECYCLE){
            if(!ownsAttempt(run)||authenticated)return; // Let an authenticated channel drain its final diagnostic.
            // Some root managers detach a successful helper from the su client.
            // Its authenticated connection still has to arrive before the deadline.
            if(result.code==0&&!result.unavailable)return;
            rootState=result.unavailable?"unavailable":"denied_or_failed";
            lastError=result.unavailable?
                "No root manager is available. Root must already be installed; PadBridge does not install it.":
                "The root bridge did not start. Check root access and the PadBridge permission in your root manager.";
            if(!result.detail.isEmpty())lastError+="\n"+result.detail;
            phase="error";listener=server;untrusted=socket;
        }
        changed("root-launch-ended");closeQuietly(untrusted);closeQuietly(listener);
    }

    private void read(Reader input,Socket client,long run,long preflightDeadline)throws Exception{
        String line;
        while(ownsAttempt(run)&&(line=Ipc.line(input,262144,ready?0:preflightDeadline))!=null){
            JSONObject event=new JSONObject(line);String type=event.optString("type");
            if(!ready&&!type.equals("ready")&&!(type.equals("error")&&event.optBoolean("fatal",false)))
                throw new IOException("The helper did not complete root preflight");
            if(type.equals("ready")){
                if(event.optInt("protocol")!=1||event.optInt("uid",-1)!=0||!event.optString("mode").equals("root"))
                    throw new IOException("Unrecognized root helper identity or protocol");
                synchronized(LIFECYCLE){if(!ownsAttempt(run))return;ready=true;phase="ready";rootState="granted";helperUid=0;}
                client.setSoTimeout(0);
            }
            JSONArray found=type.equals("devices")?event.getJSONArray("devices"):null;
            if(found!=null)registerProfiles(found);
            boolean fatal=type.equals("error")&&event.optBoolean("fatal",false);
            synchronized(LIFECYCLE){
                if(!ownsAttempt(run))return;
                if(found!=null)devices=found;
                if(type.equals("started")){active=true;activeSignature=event.getString("signature");phase="active";lastError="";}
                if(type.equals("stopped")){active=false;activeSignature="";phase="ready";}
                if(type.equals("error")){
                    lastError=event.optString("detail","System bridge error");
                    phase=active?"active":"error";if(fatal)rootState="blocked";
                }
            }
            changed(type);
            if(fatal)return;
            if(type.equals("ready"))send(json("scan"));
        }
        if(ownsAttempt(run)&&!ready)throw new IOException("Root helper disconnected before completing its checks");
    }

    private void registerProfiles(JSONArray found)throws JSONException{
        for(int n=0;n<found.length();n++){
            JSONObject device=found.getJSONObject(n);String id=device.getString("signature");
            JSONObject profile=ProfileStore.get(this,id);profile.put("device",device);
            if(!profile.has("name"))profile.put("name",device.getString("name"));ProfileStore.save(this,id,profile);
        }
    }

    private void send(String message){
        final PrintWriter channel;
        synchronized(LIFECYCLE){if(!ownsState()||commands==null)return;channel=commands;}
        try{writer.execute(()->{
            synchronized(LIFECYCLE){if(!ownsState()||commands!=channel)return;}
            channel.println(message);
            if(channel.checkError())main.post(()->{
                synchronized(LIFECYCLE){if(ownsState()&&commands==channel){lastError="The root bridge connection closed";stopSelf();}}
            });
        });}catch(RejectedExecutionException ignored){}
    }

    public void onDestroy(){
        final Socket oldSocket;final ServerSocket oldServer;final PrintWriter oldCommands;final RootLauncher oldRoot;
        synchronized(LIFECYCLE){
            closing=true;oldSocket=socket;oldServer=server;oldCommands=commands;oldRoot=rootLauncher;
            socket=null;server=null;commands=null;rootLauncher=null;waiting=false;authenticated=false;
            if(instance==this){
                ready=false;active=false;activeSignature="";helperUid=-1;
                if(userStopped){lastError="";rootState="stopped";}phase=lastError.isEmpty()?"off":"error";instance=null;
            }
        }
        writer.shutdownNow();
        new Thread(()->{
            closeQuietly(oldSocket);closeQuietly(oldServer);if(oldRoot!=null)oldRoot.close();if(oldCommands!=null)oldCommands.close();
        },"padbridge-root-close").start();
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    private static void closeQuietly(Closeable value){if(value!=null)try{value.close();}catch(IOException ignored){}}
    public IBinder onBind(Intent intent){return null;}
}
