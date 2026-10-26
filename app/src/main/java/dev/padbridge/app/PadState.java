package dev.padbridge.app;

import java.util.*;

public final class PadState {
    private final Map<Integer,DeviceCatalog.Axis> ranges;
    private final Map<Integer,Integer> values=new HashMap<>();
    private final Set<Integer> pressed=new HashSet<>();
    private final PadMapping mapping;
    private boolean dropped;
    public PadState(String capabilities,PadMapping m){ranges=DeviceCatalog.axes(capabilities);mapping=m;mapping.validate(ranges);for(DeviceCatalog.Axis a:ranges.values())values.put(a.code,a.value);}
    public void event(int type,int code,int value){
        if(type==0&&code==3){dropped=true;return;}
        if(type==3&&ranges.containsKey(code))values.put(code,value);
        if(type==1){if(value==0)pressed.remove(code);else pressed.add(code);}
    }
    public boolean isDropped(){return dropped;}
    private int stick(int code,int slot){DeviceCatalog.Axis a=ranges.get(code);return mapping.stick(values.get(code),a.min,a.max,mapping.inverted[slot]);}
    private int trigger(int code,int slot){int result;if(code<0)result=pressed.contains(slot==4?mapping.ltButton:mapping.rtButton)?32767:0;else{DeviceCatalog.Axis a=ranges.get(code);result=HidCodec.scale(values.get(code),a.min,a.max,32767);}return mapping.inverted[slot]?32767-result:result;}
    public byte[] report(){
        int buttons=0;for(int raw:pressed){Integer bit=mapping.buttons.get(raw);if(bit!=null)buttons|=1<<bit;}
        int keyX=(pressed.contains(mapping.right)?1:0)-(pressed.contains(mapping.left)?1:0),keyY=(pressed.contains(mapping.down)?1:0)-(pressed.contains(mapping.up)?1:0);
        int x=keyX!=0?keyX:values.containsKey(16)?values.get(16):0;
        int y=keyY!=0?keyY:values.containsKey(17)?values.get(17):0;
        return HidCodec.report(stick(mapping.lx,0),stick(mapping.ly,1),stick(mapping.rx,2),stick(mapping.ry,3),trigger(mapping.lt,4),trigger(mapping.rt,5),buttons,HidCodec.hat(x,y));
    }
}
