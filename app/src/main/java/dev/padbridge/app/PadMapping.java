package dev.padbridge.app;

import java.util.*;

/** All mapping and bounds checks are platform independent and covered by JVM tests. */
public final class PadMapping {
    public int lx=0,ly=1,rx=-1,ry=-1,lt=-1,rt=-1;
    public int ltButton=312,rtButton=313,up=544,down=545,left=546,right=547;
    public double deadzone=.06;
    public final boolean[] inverted=new boolean[6];
    public final Map<Integer,Integer> buttons=new HashMap<>();
    public PadMapping(){int[] raw={304,305,307,308,310,311,314,315,317,318};for(int bit=0;bit<raw.length;bit++)buttons.put(raw[bit],bit);}
    public void applyButtons(Map<Integer,Integer> custom){
        Set<Integer> outputs=new HashSet<>();for(Map.Entry<Integer,Integer> e:custom.entrySet())if(e.getKey()<0||e.getKey()>767||e.getValue()<0||e.getValue()>9||!outputs.add(e.getValue()))throw new IllegalArgumentException("Invalid or duplicate button mapping");
        for(Integer bit:custom.values())buttons.entrySet().removeIf(e->e.getValue().equals(bit));
        for(Integer raw:custom.keySet())clearDigital(raw);
        buttons.putAll(custom);
    }
    /** Explicit assignments take ownership of their physical button, including defaults. */
    public void applyDigital(Map<Integer,Integer> custom){
        validateDigitalAssignments(custom);
        for(Integer raw:custom.values())if(raw>=0){buttons.remove(raw);clearDigital(raw);}
        for(Map.Entry<Integer,Integer> e:custom.entrySet()){setDigital(e.getKey(),e.getValue());if(e.getValue()>=0){if(e.getKey()==0)lt=-2;if(e.getKey()==1)rt=-2;}}
    }
    public static void validateDigitalAssignments(Map<Integer,Integer> custom){
        Set<Integer> inputs=new HashSet<>();for(Map.Entry<Integer,Integer> e:custom.entrySet())if(e.getKey()<0||e.getKey()>5||e.getValue()< -1||e.getValue()>767||(e.getValue()>=0&&!inputs.add(e.getValue())))throw new IllegalArgumentException("Invalid or duplicate digital assignment");
    }
    private void clearDigital(int raw){for(int role=0;role<6;role++)if(digital(role)==raw)setDigital(role,-1);}
    private int digital(int role){switch(role){case 0:return ltButton;case 1:return rtButton;case 2:return up;case 3:return down;case 4:return left;default:return right;}}
    private void setDigital(int role,int raw){switch(role){case 0:ltButton=raw;break;case 1:rtButton=raw;break;case 2:up=raw;break;case 3:down=raw;break;case 4:left=raw;break;default:right=raw;}}
    public void validate(Map<Integer,DeviceCatalog.Axis> axes){
        boolean rotated=axes.containsKey(3)&&axes.containsKey(4);if(rx<0)rx=rotated?3:2;if(ry<0)ry=rotated?4:5;
        int autoLt=-1,autoRt=-1;if(axes.containsKey(10)&&axes.containsKey(9)){autoLt=10;autoRt=9;}else if(rx==3&&ry==4&&axes.containsKey(2)&&axes.containsKey(5)){autoLt=2;autoRt=5;}
        if(lt==-1)lt=autoLt;if(rt==-1)rt=autoRt;
        int[] required={lx,ly,rx,ry};Set<Integer> used=new HashSet<>();
        for(int code:required){if(!axes.containsKey(code)||axes.get(code).max<=axes.get(code).min||!used.add(code))throw new IllegalArgumentException("Invalid or duplicate stick axis");}
        for(int code:new int[]{lt,rt})if(code>=0&&(!axes.containsKey(code)||!used.add(code)))throw new IllegalArgumentException("Invalid or duplicate trigger axis");
        if(lt<0&&buttons.containsKey(ltButton))ltButton=-1;
        if(rt<0&&buttons.containsKey(rtButton))rtButton=-1;
        if(!Double.isFinite(deadzone)||deadzone<0||deadzone>.4)throw new IllegalArgumentException("Deadzone outside 0–40%");
    }
    public int stick(int value,int min,int max,boolean flip){
        double x=(value-(double)min)*2/(max-(double)min)-1;
        if(flip)x=-x;double a=Math.abs(x);
        x=a<=deadzone?0:Math.copySign((Math.min(1,a)-deadzone)/(1-deadzone),x);
        return HidCodec.clamp((int)Math.round((x+1)*32767.5),65535);
    }
}
