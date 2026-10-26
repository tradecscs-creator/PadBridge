package dev.padbridge.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG=Color.rgb(11,16,23),CARD=Color.rgb(22,30,40),TEXT=Color.rgb(238,244,247),MUTED=Color.rgb(153,171,184),ACCENT=Color.rgb(121,232,192),WARN=Color.rgb(255,202,126);
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(int color,int radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static LinearLayout vertical(Context c){LinearLayout b=new LinearLayout(c);b.setOrientation(LinearLayout.VERTICAL);return b;}
    static TextView text(Context c,String value,int size,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(c,3),1);return v;}
    static TextView add(LinearLayout parent,String value,int size,int color){TextView v=text(parent.getContext(),value,size,color);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(parent.getContext(),10);parent.addView(v,p);return v;}
    static LinearLayout card(LinearLayout parent){Context c=parent.getContext();LinearLayout b=vertical(c);b.setPadding(dp(c,20),dp(c,20),dp(c,20),dp(c,14));b.setBackground(shape(CARD,20,c));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(c,16);parent.addView(b,p);return b;}
    static Button button(LinearLayout parent,String label,boolean primary,Runnable action){Context c=parent.getContext();Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(primary?BG:TEXT);b.setBackground(shape(primary?ACCENT:Color.rgb(39,52,64),14,c));b.setMinHeight(dp(c,50));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,6);p.bottomMargin=dp(c,6);parent.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    static LinearLayout screen(Activity a,String title){
        LinearLayout root=vertical(a);root.setBackgroundColor(BG);a.setContentView(root);insets(root);
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(a,20),dp(a,8),dp(a,20),dp(a,10));
        TextView back=text(a,"‹",34,ACCENT);back.setContentDescription("Back");back.setPadding(0,0,dp(a,20),0);back.setOnClickListener(v->a.finish());header.addView(back);header.addView(text(a,title,23,TEXT));root.addView(header);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);LinearLayout box=vertical(a);box.setPadding(dp(a,20),dp(a,10),dp(a,20),dp(a,24));scroll.addView(box);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return box;
    }
    static void insets(View root){if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,in)->{Insets s=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(s.left,s.top,s.right,s.bottom);return in;});else root.setFitsSystemWindows(true);}
    static void message(Activity a,String title,String body){new AlertDialog.Builder(a).setTitle(title).setMessage(body).setPositiveButton("Got it",null).show();}
    static void toast(Context c,String text){Toast.makeText(c,text,Toast.LENGTH_SHORT).show();}
}
