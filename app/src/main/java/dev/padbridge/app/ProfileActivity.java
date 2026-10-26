package dev.padbridge.app;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ProfileActivity extends Activity {
    private String id,deviceName;private JSONObject profile,device;private LinearLayout box;private TextView progress;
    private AlertDialog learn;private int learnTarget=-1;private boolean wizard;
    private static final String[] BUTTONS={"A","B","X","Y","LB","RB","Back","Start","L3","R3","LT","RT","↑","↓","←","→"};
    private static final String[] DIGITAL={"ltButton","rtButton","up","down","left","right"};
    public void onCreate(Bundle saved){super.onCreate(saved);id=getIntent().getStringExtra("id");profile=ProfileStore.get(this,id);device=profile.optJSONObject("device");if(device==null){finish();return;}deviceName=device.optString("name");render();}
    private void render(){box=Ui.screen(this,"Controller profile");Ui.add(box,profile.optString("name",deviceName),26,Ui.TEXT);Ui.add(box,deviceName,14,Ui.MUTED);
        Ui.add(box,"Changes are saved locally and applied the next time translation starts.",14,Ui.MUTED);
        LinearLayout buttons=Ui.card(box);Ui.add(buttons,"Your buttons",20,Ui.TEXT);progress=Ui.add(buttons,"",14,Ui.MUTED);updateProgress();
        Ui.button(buttons,"Map 10 main buttons",true,()->{wizard=true;listenFor(0);});
        Ui.button(buttons,"Map a single button",false,()->new AlertDialog.Builder(this).setTitle("Choose an output").setItems(BUTTONS,(d,n)->{wizard=false;listenFor(n);}).show());
        Ui.button(buttons,"Button preset",false,()->new AlertDialog.Builder(this).setTitle("Starting layout").setItems(new String[]{"Android / XInput","DirectInput 1–10","Nintendo: swap A/B and X/Y","Joystick 1–10"},(d,n)->preset(n)).show());
        LinearLayout sticks=Ui.card(box);Ui.add(sticks,"Stick precision",20,Ui.TEXT);TextView dead=Ui.add(sticks,"",15,Ui.MUTED);SeekBar zone=new SeekBar(this);zone.setMax(40);zone.setProgress((int)Math.round(profile.optDouble("deadzone",.06)*100));dead.setText(getString(R.string.dead_zone,zone.getProgress()));sticks.addView(zone);
        zone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){dead.setText(getString(R.string.dead_zone,value));if(user)try{profile.put("deadzone",value/100.0);save();}catch(JSONException ignored){}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        Ui.add(sticks,"Increase this if the camera or character moves when the stick is released.",13,Ui.MUTED);
        Ui.button(sticks,"Axes and inversion",false,this::axisEditor);
        LinearLayout files=Ui.card(box);Ui.add(files,"Manage profile",20,Ui.TEXT);
        Ui.button(files,"Rename",false,()->{EditText field=new EditText(this);field.setText(profile.optString("name",deviceName));field.setSingleLine(true);new AlertDialog.Builder(this).setTitle("Profile name").setView(field).setPositiveButton("Save",(d,w)->{try{profile.put("name",field.getText().toString().trim());save();render();}catch(JSONException e){Ui.toast(this,e.getMessage());}}).setNegativeButton("Cancel",null).show();});
        Ui.button(files,"Export JSON",false,()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"PadBridge-profile.json"),1));
        Ui.button(files,"Import JSON",false,()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),2));
        Ui.button(files,"Reset to defaults",false,()->new AlertDialog.Builder(this).setTitle("Reset this profile?").setMessage("Button assignments and stick settings will be reset.").setPositiveButton("Reset",(d,w)->{ProfileStore.reset(this,id);profile=new JSONObject();try{profile.put("device",device).put("name",deviceName);save();render();}catch(JSONException ignored){}}).setNegativeButton("Cancel",null).show());
    }
    private void updateProgress(){JSONObject keys=profile.optJSONObject("buttons");progress.setText(keys==null||keys.length()==0?"Using the automatic layout":"Mapped main buttons: "+keys.length()+" / 10");}
    private void save(){try{ProfileStore.save(this,id,profile);}catch(JSONException e){profile=ProfileStore.get(this,id);Ui.toast(this,e.getMessage());}}
    private void preset(int kind){try{JSONObject keys=new JSONObject();int[] raw=kind==3?new int[]{288,289,290,291,292,293,294,295,296,297}:kind==1?new int[]{304,305,306,307,308,309,310,311,312,313}:new int[]{304,305,307,308,310,311,314,315,317,318};for(int bit=0;bit<10;bit++){int output=kind==2&&bit<4?(bit^1):bit;keys.put(Integer.toString(raw[bit]),output);}for(String key:DIGITAL)if(keys.has(Integer.toString(profile.optInt(key,-1))))profile.put(key,-1);profile.put("buttons",keys);save();updateProgress();}catch(JSONException ignored){}}
    private void listenFor(int target){
        if(BridgeService.active){Ui.toast(this,"Pause translation first");return;}
        learnTarget=target;learn=new AlertDialog.Builder(this).setTitle("Press the button for "+BUTTONS[target]).setMessage("Press the physical controller button you want to assign to this output.")
            .setNegativeButton("Cancel",(d,w)->{wizard=false;learnTarget=-1;}).setNeutralButton("Skip",(d,w)->advance()).create();
        learn.setOnKeyListener((d,key,event)->learnEvent(event));learn.setOnCancelListener(d->{wizard=false;learnTarget=-1;});learn.show();
    }
    private boolean learnEvent(KeyEvent e){InputDevice d=e.getDevice();if(learnTarget<0||d==null||!d.getName().equals(deviceName)||e.getScanCode()<=0)return false;
        if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){try{if(learnTarget<10)ProfileStore.assign(this,id,e.getScanCode(),learnTarget);else ProfileStore.assignDigital(this,id,e.getScanCode(),learnTarget-10);profile=ProfileStore.get(this,id);if(learn!=null)learn.dismiss();advance();}catch(JSONException error){Ui.toast(this,error.getMessage());}return true;}return true;
    }
    private void advance(){int next=learnTarget+1;learnTarget=-1;updateProgress();if(wizard&&next<10)getWindow().getDecorView().postDelayed(()->{if(wizard&&!isFinishing()&&!isDestroyed())listenFor(next);},180);else{wizard=false;Ui.toast(this,"Assignments saved");}}
    public boolean dispatchKeyEvent(KeyEvent e){if(learnEvent(e))return true;return super.dispatchKeyEvent(e);}
    private void axisEditor(){LinearLayout content=Ui.vertical(this);content.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);ScrollView scroll=new ScrollView(this);scroll.addView(content);String[] roles={"lx","ly","rx","ry","lt","rt"};String[] labels={"Left stick: horizontal","Left stick: vertical","Right stick: horizontal","Right stick: vertical","LT","RT"};JSONObject current=profile.optJSONObject("axes");List<Spinner> choices=new ArrayList<>();List<List<Integer>> values=new ArrayList<>();List<CheckBox> flips=new ArrayList<>();JSONArray available=device.optJSONArray("axes");
        for(int index=0;index<6;index++){Ui.add(content,labels[index],14,Ui.MUTED);List<Integer> codes=new ArrayList<>();List<String> names=new ArrayList<>();if(index>=2){codes.add(-1);names.add("Automatic");}if(index>=4){codes.add(-2);names.add("Digital button / no axis");}if(available!=null)for(int a=0;a<available.length();a++){int code=available.optJSONObject(a).optInt("code");if(code>=16)continue;codes.add(code);names.add(axisName(code));}Spinner select=new Spinner(this);select.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));int selected=current==null?(index==0?0:index==1?1:-1):current.optInt(roles[index],index==0?0:index==1?1:-1);select.setSelection(Math.max(0,codes.indexOf(selected)));content.addView(select);choices.add(select);values.add(codes);CheckBox flip=new CheckBox(this);flip.setText("Invert");JSONArray inverted=profile.optJSONArray("inverted");flip.setChecked(inverted!=null&&inverted.optBoolean(index));content.addView(flip);flips.add(flip);}
        new AlertDialog.Builder(this).setTitle("Axis layout").setView(scroll).setPositiveButton("Save",(d,w)->{try{JSONObject axes=new JSONObject();JSONArray inverted=new JSONArray();for(int i=0;i<6;i++){axes.put(roles[i],values.get(i).get(choices.get(i).getSelectedItemPosition()));inverted.put(flips.get(i).isChecked());}JSONObject candidate=new JSONObject(profile.toString()).put("axes",axes).put("inverted",inverted);if(axes.getInt("lt")!=-2)candidate.remove("ltButton");if(axes.getInt("rt")!=-2)candidate.remove("rtButton");ProfileStore.mapping(candidate);profile=candidate;save();}catch(Exception e){Ui.toast(this,e.getMessage());}}).setNegativeButton("Cancel",null).show();
    }
    private static String axisName(int code){String[] names={"X","Y","Z","RX","RY","RZ","THROTTLE","RUDDER","WHEEL","GAS","BRAKE"};return code<names.length?names[code]:"Axis "+code;}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{
        if(request==1){JSONObject exported=new JSONObject(profile.toString());exported.remove("device");exported.put("schema",1).put("id",id);try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("Unable to open file");out.write(exported.toString(2).getBytes(StandardCharsets.UTF_8));}Ui.toast(this,"Profile exported");}
        if(request==2){ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new IOException("Unable to open file");byte[] b=new byte[4096];int n;while((n=in.read(b))>0){if(bytes.size()+n>32768)throw new IOException("Profile exceeds 32 KB");bytes.write(b,0,n);}}
            JSONObject imported=new JSONObject(bytes.toString("UTF-8"));ProfileStore.validate(imported);if(!imported.optString("id",id).equals(id))throw new IOException("This profile is for a different controller model");imported.put("device",device);ProfileStore.save(this,id,imported);profile=imported;render();Ui.toast(this,"Profile imported");}
    }catch(Exception e){Ui.message(this,"Unable to process profile",e.getMessage());}}
    public void onDestroy(){wizard=false;learnTarget=-1;if(learn!=null)learn.dismiss();super.onDestroy();}
}
