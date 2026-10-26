package dev.padbridge.app;
import android.content.Context;
import org.json.*;
import java.util.*;

public final class ProfileStore {
    private static final String[] DIGITAL={"ltButton","rtButton","up","down","left","right"};
    public static JSONObject get(Context c,String id){try{return new JSONObject(c.getSharedPreferences("profiles",0).getString(id,"{}"));}catch(Exception e){return new JSONObject();}}
    public static void save(Context c,String id,JSONObject profile)throws JSONException {validateDevice(profile);profile.put("schema",1).put("id",id);c.getSharedPreferences("profiles",0).edit().putString(id,profile.toString()).apply();}
    public static void validateDevice(JSONObject profile)throws JSONException {
        validate(profile);JSONObject device=profile.optJSONObject("device");if(device==null)return;JSONArray list=device.optJSONArray("axes");if(list==null)return;
        Map<Integer,DeviceCatalog.Axis> axes=new TreeMap<>();for(int n=0;n<list.length();n++){JSONObject a=list.getJSONObject(n);int code=a.getInt("code"),min=a.getInt("min"),max=a.getInt("max");axes.put(code,new DeviceCatalog.Axis(code,min,min,max));}
        try{mapping(profile).validate(axes);}catch(IllegalArgumentException invalid){throw new JSONException(invalid.getMessage());}
    }
    public static void reset(Context c,String id){c.getSharedPreferences("profiles",0).edit().remove(id).apply();}
    public static void validate(JSONObject profile)throws JSONException {
        if(profile.optInt("schema",1)!=1)throw new JSONException("Unsupported profile version");
        if(profile.toString().length()>32768)throw new JSONException("Profile too large");
        JSONObject buttons=profile.optJSONObject("buttons");Set<Integer> assigned=new HashSet<>();
        if(buttons!=null){Iterator<String> keys=buttons.keys();while(keys.hasNext()){String k=keys.next();int scan;try{scan=Integer.parseInt(k);}catch(NumberFormatException e){throw new JSONException("Invalid button code");}int bit=buttons.getInt(k);if(scan<0||scan>767||!Integer.toString(scan).equals(k)||bit<0||bit>9||!assigned.add(bit))throw new JSONException("Invalid or duplicate button assignment");}}
        JSONObject axes=profile.optJSONObject("axes");if(axes!=null)for(String key:new String[]{"lx","ly","rx","ry","lt","rt"})if(axes.has(key)){int code=axes.getInt(key);if(code< (key.equals("lt")||key.equals("rt")?-2:-1)||code>63)throw new JSONException("Invalid axis");}
        Map<Integer,Integer> digital=digitalAssignments(profile);try{PadMapping.validateDigitalAssignments(digital);}catch(IllegalArgumentException invalid){throw new JSONException(invalid.getMessage());}
        if(buttons!=null)for(int raw:digital.values())if(raw>=0&&buttons.has(Integer.toString(raw)))throw new JSONException("A button cannot have both a main and digital assignment");
        double deadzone=profile.optDouble("deadzone",.06);if(!Double.isFinite(deadzone)||deadzone<0||deadzone>.4)throw new JSONException("Invalid deadzone");
        String name=profile.optString("name","");if(name.length()>80)throw new JSONException("Profile name too long");
    }
    public static PadMapping mapping(JSONObject json)throws JSONException {
        validate(json);PadMapping result=new PadMapping();JSONObject keys=json.optJSONObject("buttons");
        if(keys!=null){Map<Integer,Integer> custom=new HashMap<>();Iterator<String> it=keys.keys();while(it.hasNext()){String k=it.next();custom.put(Integer.parseInt(k),keys.getInt(k));}result.applyButtons(custom);}
        JSONObject axes=json.optJSONObject("axes");if(axes!=null){result.lx=axes.optInt("lx",0);result.ly=axes.optInt("ly",1);result.rx=axes.optInt("rx",-1);result.ry=axes.optInt("ry",-1);result.lt=axes.optInt("lt",-1);result.rt=axes.optInt("rt",-1);}
        result.deadzone=json.optDouble("deadzone",.06);JSONArray flip=json.optJSONArray("inverted");if(flip!=null)for(int i=0;i<Math.min(6,flip.length());i++)result.inverted[i]=flip.optBoolean(i,false);
        result.applyDigital(digitalAssignments(json));
        return result;
    }
    private static Map<Integer,Integer> digitalAssignments(JSONObject profile)throws JSONException {Map<Integer,Integer> result=new HashMap<>();for(int role=0;role<DIGITAL.length;role++)if(profile.has(DIGITAL[role]))result.put(role,profile.getInt(DIGITAL[role]));return result;}
    public static void assign(Context c,String id,int raw,int bit)throws JSONException {
        JSONObject profile=get(c,id),buttons=profile.optJSONObject("buttons");if(buttons==null)buttons=new JSONObject();
        List<String> remove=new ArrayList<>();Iterator<String> it=buttons.keys();while(it.hasNext()){String key=it.next();if(buttons.optInt(key,-1)==bit)remove.add(key);}for(String key:remove)buttons.remove(key);
        for(String key:DIGITAL)if(profile.optInt(key,-1)==raw)profile.put(key,-1);
        buttons.put(Integer.toString(raw),bit);profile.put("buttons",buttons);save(c,id,profile);
    }
    public static void assignDigital(Context c,String id,int raw,int role)throws JSONException {
        if(role<0||role>=DIGITAL.length||raw<0||raw>767)throw new JSONException("Invalid digital assignment");
        JSONObject profile=get(c,id),buttons=profile.optJSONObject("buttons");if(buttons!=null)buttons.remove(Integer.toString(raw));
        for(String key:DIGITAL)if(profile.optInt(key,-1)==raw)profile.put(key,-1);
        profile.put(DIGITAL[role],raw);if(role<2){JSONObject axes=profile.optJSONObject("axes");if(axes==null)axes=new JSONObject();axes.put(role==0?"lt":"rt",-2);profile.put("axes",axes);}
        save(c,id,profile);
    }
}
