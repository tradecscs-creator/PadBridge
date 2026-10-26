package dev.padbridge.app;

import java.util.*;
import java.util.regex.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Filters by Linux input capabilities, never by a marketing brand list. */
public final class DeviceCatalog {
    public static final class Axis {
        public final int code,value,min,max;
        Axis(int c,int v,int lo,int hi){code=c;value=v;min=lo;max=hi;}
    }
    public static final class Device {
        public final String name,path,capabilities,signature;
        public final Map<Integer,Axis> axes;
        public final Set<Integer> buttons;
        Device(String n,String p,String c,Map<Integer,Axis> a,Set<Integer> b){
            name=n;path=p;capabilities=c;axes=a;buttons=b;
            StringBuilder identity=new StringBuilder(name);
            for(int code:new TreeSet<>(axes.keySet())){Axis x=axes.get(code);identity.append('|').append(code).append(':').append(x.min).append(':').append(x.max);}
            identity.append(new TreeSet<>(buttons));signature=hash(identity.toString());
        }
    }
    private static final Pattern AXIS=Pattern.compile("(?m)^\\s*(?:ABS\\s+\\(0003\\):\\s*)?([0-9a-fA-F]{4})\\s*:\\s*value\\s+(-?\\d+),\\s*min\\s+(-?\\d+),\\s*max\\s+(-?\\d+)");
    public static Map<Integer,Axis> axes(String text){
        Map<Integer,Axis> result=new TreeMap<>();Matcher m=AXIS.matcher(text);
        while(m.find()){int code=Integer.parseInt(m.group(1),16);result.put(code,new Axis(code,Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4))));}
        return result;
    }
    public static Set<Integer> buttons(String text){
        Set<Integer> result=new TreeSet<>();boolean key=false;
        for(String line:text.split("\\n")){
            if(line.contains("KEY (0001):")){key=true;line=line.substring(line.indexOf("KEY (0001):")+11);}
            else if(line.contains(":"))key=false;
            if(key){Matcher m=Pattern.compile("\\b[0-9a-fA-F]{4}\\b").matcher(line);while(m.find())result.add(Integer.parseInt(m.group(),16));}
        }
        return result;
    }
    public static List<Device> parse(String text){
        List<Device> result=new ArrayList<>();
        // With getevent -p -v8, bus/vendor/product precede name. Keep complete
        // blocks so location (EVIOCGPHYS) can identify our own virtual output.
        Matcher m=Pattern.compile("(?ms)^add device \\d+: (/dev/input/event\\d+)[ \\t]*\\r?\\n(.*?)(?=^add device \\d+:|\\z)").matcher(text);
        while(m.find()){
            String body=m.group(2);Matcher named=Pattern.compile("(?m)^\\s*name:\\s*\"([^\"]+)\"[ \\t]*\\r?$").matcher(body);if(!named.find())continue;
            String name=named.group(1);Map<Integer,Axis> axes=axes(body);Set<Integer> keys=buttons(body);
            // Never allow the phone touchscreen, a mouse, or our virtual output to be grabbed.
            // Legacy 0.3 used padbridge:8bitdo; reserve the whole phys prefix.
            boolean ownOutput=Pattern.compile("(?m)^\\s*location:\\s*\"padbridge:[^\"]*\"[ \\t]*\\r?$").matcher(body).find();
            if(ownOutput||name.toLowerCase(Locale.ROOT).contains("padbridge")||body.contains("INPUT_PROP_DIRECT")||keys.contains(330))continue;
            boolean gameButtons=false;for(int k:keys)if(k>=288&&k<=319)gameButtons=true;
            boolean sticks=axes.containsKey(0)&&axes.containsKey(1)&&((axes.containsKey(2)&&axes.containsKey(5))||(axes.containsKey(3)&&axes.containsKey(4)));
            if(gameButtons&&sticks)result.add(new Device(name,m.group(1),"add device 1: "+m.group(1)+"\n"+body,axes,keys));
        }
        return result;
    }
    public static String hash(String value){
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(int i=0;i<12;i++)out.append(String.format(Locale.ROOT,"%02x",bytes[i]&255));return out.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
