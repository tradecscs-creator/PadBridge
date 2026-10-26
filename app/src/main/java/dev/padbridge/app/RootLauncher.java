package dev.padbridge.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one user-requested su client. No privilege probes run at construction. */
public final class RootLauncher implements AutoCloseable {
    interface ProcessStarter { Process start() throws IOException; }
    interface Listener { void exited(Result result); }

    static final class Result {
        final int code;
        final boolean unavailable;
        final String detail;
        Result(int code,boolean unavailable,String detail){this.code=code;this.unavailable=unavailable;this.detail=detail;}
    }

    private final Object gate=new Object();
    private final AtomicBoolean started=new AtomicBoolean();
    private final String command,secret;
    private final ProcessStarter starter;
    private final Listener listener;
    private volatile boolean closed;
    private Process process;

    RootLauncher(String apkPath,int port,String token,Listener listener){
        this(RootCommand.create(apkPath,port,token),token,
            ()->new ProcessBuilder("su").redirectErrorStream(true).start(),listener);
    }

    // Injectable only inside this package for deterministic process lifecycle tests.
    RootLauncher(String command,String secret,ProcessStarter starter,Listener listener){
        this.command=command;this.secret=secret;this.starter=starter;this.listener=listener;
    }

    void start(){
        if(!started.compareAndSet(false,true))return;
        synchronized(gate){if(closed)return;}
        new Thread(this::run,"padbridge-root-launch").start();
    }

    private void run(){
        Process child=null;Result result;
        try{
            synchronized(gate){if(closed)return;}
            child=starter.start();
            synchronized(gate){if(closed){child.destroy();return;}process=child;}
            // A fixed validated command goes to su over stdin, never user profiles
            // or a downloaded script. exec keeps helper lifetime tied to this job.
            try(Writer input=new OutputStreamWriter(child.getOutputStream(),StandardCharsets.UTF_8)){
                input.write(command);input.write('\n');input.flush();
            }
            StringBuilder tail=new StringBuilder();
            try(Reader output=new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8)){
                char[] block=new char[1024];int count;
                while((count=output.read(block))!=-1){
                    tail.append(block,0,count);
                    if(tail.length()>8192)tail.delete(0,tail.length()-8192);
                }
            }
            result=new Result(child.waitFor(),false,redact(tail.toString()));
        }catch(IOException e){result=new Result(-1,child==null,redact(e.getClass().getSimpleName()+": "+e.getMessage()));}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();result=new Result(-1,false,"Root launch interrupted");}
        finally{
            synchronized(gate){if(process==child)process=null;}
            if(child!=null){child.destroy();closeStreams(child);}
        }
        if(!closed)listener.exited(result);
    }

    private String redact(String text){
        String clean=secret==null||secret.isEmpty()?text:text.replace(secret,"[activation token removed]");
        return clean.length()>4096?clean.substring(clean.length()-4096):clean;
    }

    public void close(){
        Process child;synchronized(gate){closed=true;child=process;process=null;}
        if(child!=null){child.destroy();closeStreams(child);}
    }

    private static void closeStreams(Process child){
        closeQuietly(child.getOutputStream());closeQuietly(child.getInputStream());closeQuietly(child.getErrorStream());
    }
    private static void closeQuietly(Closeable value){try{value.close();}catch(IOException ignored){}}
}
