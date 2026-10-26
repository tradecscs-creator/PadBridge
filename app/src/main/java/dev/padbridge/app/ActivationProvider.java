package dev.padbridge.app;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public final class ActivationProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    public Bundle call(String method,String arg,Bundle extras){
        int caller=Binder.getCallingUid();
        if(caller!=android.os.Process.myUid()&&caller!=0)
            throw new SecurityException("Private status endpoint");
        if(!"status".equals(method))throw new UnsupportedOperationException("Read-only status endpoint");
        Bundle result=new Bundle();try{
            // The transport port and authentication token never leave app memory here.
            JSONObject state=new JSONObject().put("protocol",1).put("ready",BridgeService.ready)
                    .put("active",BridgeService.active).put("phase",BridgeService.phase);
            result.putString("payload",Base64.encodeToString(state.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
        }catch(Exception e){result.putString("error",e.getMessage());}return result;
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    public String getType(Uri u){return "vnd.android.cursor.item/vnd.padbridge.activation";}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
