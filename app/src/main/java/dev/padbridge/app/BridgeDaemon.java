package dev.padbridge.app;

import android.system.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Entry point is bundled in the installed base APK. No downloaded executable code. */
public final class BridgeDaemon {
    private static PrintWriter out;
    private static NativePump pump;
    private static String active="";
    private static synchronized void emit(JSONObject event){String line=event.toString();if(out!=null){out.println(line);if(!out.checkError())return;}System.err.println(line);}
    private static void emit(String type,String detail){try{emit(new JSONObject().put("type",type).put("detail",detail));}catch(Exception ignored){}}
    private static void error(String stage,boolean fatal,Throwable cause){try{emit(new JSONObject().put("type","error").put("stage",stage).put("fatal",fatal).put("detail",reason(cause)));}catch(Exception ignored){}}
    static String reason(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName()+": "+e.getMessage();}
    static List<DeviceCatalog.Device> devices()throws Exception {
        // -v8 adds EVIOCGPHYS/location metadata to -p. Unlike -i, it does not
        // request HID debug descriptors; numeric event/axis output stays intact.
        Process process=new ProcessBuilder("/system/bin/getevent","-p","-v8").redirectErrorStream(true).start();
        ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();watchdog.schedule(process::destroy,5,TimeUnit.SECONDS);
        try(InputStream in=process.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] data=new byte[4096];int n;while((n=in.read(data))>0){if(bytes.size()+n>524288)throw new IOException("Device information exceeds limit");bytes.write(data,0,n);}
            if(process.waitFor()!=0)throw new IOException("Input device access unavailable");return DeviceCatalog.parse(bytes.toString("UTF-8"));
        }finally{watchdog.shutdownNow();process.destroy();}
    }
    private static JSONObject deviceJson(DeviceCatalog.Device d)throws JSONException {
        JSONArray axes=new JSONArray();for(DeviceCatalog.Axis a:d.axes.values())axes.put(new JSONObject().put("code",a.code).put("min",a.min).put("max",a.max));
        return new JSONObject().put("name",d.name).put("path",d.path).put("signature",d.signature).put("axes",axes).put("buttons",new JSONArray(d.buttons));
    }
    private static void scan()throws Exception {
        checkUhid();
        JSONArray list=new JSONArray();for(DeviceCatalog.Device d:devices()){if(list.length()>=16)break;list.put(deviceJson(d));}
        emit(new JSONObject().put("type","devices").put("devices",list));
    }
    private static void checkUhid()throws Exception {FileDescriptor fd=Os.open("/dev/uhid",OsConstants.O_RDWR|OsConstants.O_CLOEXEC,0);Os.close(fd);}
    private static void stop(){NativePump old=pump;pump=null;active="";if(old!=null)old.close();}
    public static void main(String[] args){
        int exit=0;String stage="preflight";
        try {
            RootCommand.Launch launch=RootCommand.parse(Os.getuid(),args);
            try(Socket socket=new Socket("127.0.0.1",launch.port)){
                try{
                socket.setTcpNoDelay(true);out=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),"UTF-8"),true);out.println(launch.token);
                if(out.checkError())throw new IOException("Activation channel closed");
                // Privilege alone does not prove this kernel/SELinux policy permits
                // UHID and input access. Empty detection is valid: connect later.
                checkUhid();devices();
                emit(new JSONObject().put("type","ready").put("protocol",1).put("uid",launch.uid).put("mode",launch.mode));stage="session";
                Reader input=new InputStreamReader(socket.getInputStream(),"UTF-8");String line;
                while((line=Ipc.line(input,65536))!=null){try{
                    JSONObject cmd=new JSONObject(line);String action=cmd.optString("cmd");
                    if(action.equals("scan"))scan();else if(action.equals("pause"))stop();else if(action.equals("quit"))break;
                    else if(action.equals("start")){
                        stop();String signature=cmd.getString("signature"),path=cmd.getString("path");DeviceCatalog.Device selected=null;
                        for(DeviceCatalog.Device d:devices())if(d.signature.equals(signature)&&d.path.equals(path)){selected=d;break;}
                        if(selected==null)throw new IOException("Selected controller is no longer connected");
                        PadMapping mapping=ProfileStore.mapping(cmd.optJSONObject("profile")==null?new JSONObject():cmd.getJSONObject("profile"));
                        final String identity=signature;
                        NativePump candidate=new NativePump(selected,mapping,(type,detail)->{if(type.equals("stopped"))active="";if(type.equals("started")){try{emit(new JSONObject().put("type","started").put("signature",detail));}catch(JSONException ignored){}}else emit(type,detail);});
                        try{pump=candidate;active=identity;candidate.start();}catch(Exception e){candidate.close();pump=null;active="";throw e;}
                    }
                }catch(Exception e){error("command",false,e);}}
                }catch(Exception e){exit=1;error(stage,true,e);}
            }
        }catch(Exception e){exit=1;error(stage,true,e);}
        finally{stop();if(out!=null)out.close();}
        System.exit(exit);
    }
}
