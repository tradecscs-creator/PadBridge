package dev.padbridge.app;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class TesterActivity extends Activity {
    private TextView source,details;private PadView pad;
    public void onCreate(Bundle b){super.onCreate(b);LinearLayout box=Ui.screen(this,"Controller test");Ui.add(box,"Move the sticks and press triggers and buttons. With translation on, the source will be the virtual Xbox.",15,Ui.MUTED);pad=new PadView(this);box.addView(pad,new LinearLayout.LayoutParams(-1,Ui.dp(this,250)));LinearLayout info=Ui.card(box);source=Ui.add(info,"Waiting for controller",18,Ui.ACCENT);details=Ui.add(info,"Your inputs appear here",14,Ui.MUTED);}
    public boolean dispatchKeyEvent(KeyEvent e){InputDevice d=e.getDevice();if(d!=null&&(d.supportsSource(InputDevice.SOURCE_GAMEPAD)||d.supportsSource(InputDevice.SOURCE_JOYSTICK))){if(e.getAction()==0)pad.buttons.add(e.getKeyCode());else pad.buttons.remove(e.getKeyCode());source.setText(d.getName());details.setText(getString(R.string.button_status,KeyEvent.keyCodeToString(e.getKeyCode()).replace("KEYCODE_BUTTON_","").replace("KEYCODE_",""),e.getAction()==0?"pressed":"released"));pad.invalidate();return true;}return super.dispatchKeyEvent(e);}
    public boolean dispatchGenericMotionEvent(MotionEvent e){if(e.isFromSource(InputDevice.SOURCE_JOYSTICK)){
        InputDevice device=e.getDevice();source.setText(device==null?"Gamepad":device.getName());pad.lx=e.getAxisValue(MotionEvent.AXIS_X);pad.ly=e.getAxisValue(MotionEvent.AXIS_Y);
        boolean rotated=hasAxis(device,MotionEvent.AXIS_RX)&&hasAxis(device,MotionEvent.AXIS_RY);
        pad.rx=e.getAxisValue(rotated?MotionEvent.AXIS_RX:MotionEvent.AXIS_Z);pad.ry=e.getAxisValue(rotated?MotionEvent.AXIS_RY:MotionEvent.AXIS_RZ);
        pad.lt=Math.max(trigger(e,MotionEvent.AXIS_LTRIGGER),trigger(e,MotionEvent.AXIS_BRAKE));pad.rt=Math.max(trigger(e,MotionEvent.AXIS_RTRIGGER),trigger(e,MotionEvent.AXIS_GAS));
        if(rotated&&!hasAxis(device,MotionEvent.AXIS_LTRIGGER)&&!hasAxis(device,MotionEvent.AXIS_BRAKE))pad.lt=trigger(e,MotionEvent.AXIS_Z);
        if(rotated&&!hasAxis(device,MotionEvent.AXIS_RTRIGGER)&&!hasAxis(device,MotionEvent.AXIS_GAS))pad.rt=trigger(e,MotionEvent.AXIS_RZ);
        pad.hx=e.getAxisValue(MotionEvent.AXIS_HAT_X);pad.hy=e.getAxisValue(MotionEvent.AXIS_HAT_Y);details.setText(String.format(Locale.ROOT,"L %.2f / %.2f     R %.2f / %.2f\nLT %.2f     RT %.2f",pad.lx,pad.ly,pad.rx,pad.ry,pad.lt,pad.rt));pad.invalidate();return true;}return super.dispatchGenericMotionEvent(e);}
    private static boolean hasAxis(InputDevice device,int axis){return device!=null&&device.getMotionRange(axis,InputDevice.SOURCE_JOYSTICK)!=null;}
    private static float trigger(MotionEvent event,int axis){InputDevice device=event.getDevice();InputDevice.MotionRange range=device==null?null:device.getMotionRange(axis,InputDevice.SOURCE_JOYSTICK);if(range==null||range.getMax()<=range.getMin())return 0;return Math.max(0,Math.min(1,(event.getAxisValue(axis)-range.getMin())/(range.getMax()-range.getMin())));}
    static final class PadView extends View {
        private final Paint paint=new Paint(3);final Set<Integer> buttons=new HashSet<>();float lx,ly,rx,ry,lt,rt,hx,hy;
        PadView(Context c){super(c);setContentDescription("Live stick, trigger, and button indicators");}
        private void circle(Canvas c,float x,float y,float r,int color){paint.setColor(color);c.drawCircle(x,y,r,paint);}
        private void label(Canvas c,String s,float x,float y,int color,float size){paint.setColor(color);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));c.drawText(s,x,y,paint);}
        protected void onDraw(Canvas original){super.onDraw(original);Canvas c=original;float scale=Math.min(getWidth()/360f,getHeight()/240f);if(scale<=0)return;c.save();c.translate((getWidth()-360*scale)/2,(getHeight()-240*scale)/2);c.scale(scale,scale);paint.setColor(Ui.CARD);c.drawRoundRect(8,36,352,230,36,36,paint);
            stick(c,87,112,lx,ly);stick(c,244,166,rx,ry);
            paint.setColor(Color.rgb(46,61,72));c.drawRoundRect(50,12,123,27,7,7,paint);c.drawRoundRect(237,12,310,27,7,7,paint);paint.setColor(Ui.ACCENT);c.drawRoundRect(50,12,50+73*Math.max(lt,buttons.contains(KeyEvent.KEYCODE_BUTTON_L2)?1:0),27,7,7,paint);c.drawRoundRect(237,12,237+73*Math.max(rt,buttons.contains(KeyEvent.KEYCODE_BUTTON_R2)?1:0),27,7,7,paint);
            int[] keys={96,97,99,100};String[] names={"A","B","X","Y"};float[] x={277,308,246,277},ys={112,83,83,54};for(int i=0;i<4;i++){circle(c,x[i],ys[i],13,buttons.contains(keys[i])?Ui.ACCENT:Color.rgb(46,61,72));label(c,names[i],x[i],ys[i]+4,buttons.contains(keys[i])?Ui.BG:Ui.TEXT,11);}
            label(c,"+",111+hx*8,185+hy*8,(hx!=0||hy!=0)?Ui.ACCENT:Ui.MUTED,35);label(c,"L",87,112,Ui.MUTED,10);label(c,"R",244,166,Ui.MUTED,10);c.restore();
        }
        private void stick(Canvas c,float x,float y,float a,float b){circle(c,x,y,32,Color.rgb(36,49,61));circle(c,x+a*17,y+b*17,17,Ui.ACCENT);}
    }
}
