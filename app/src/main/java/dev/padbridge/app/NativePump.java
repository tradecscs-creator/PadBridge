package dev.padbridge.app;

import android.system.*;
import android.annotation.SuppressLint;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class NativePump implements AutoCloseable {
    interface Listener {void event(String type,String detail);}
    private final DeviceCatalog.Device device;
    private final PadMapping mapping;
    private final Listener listener;
    private FileDescriptor input,uhid;
    private byte[] latestReport=HidCodec.neutral();
    private volatile boolean running;
    private final AtomicBoolean closed=new AtomicBoolean();
    private Thread worker,reader;
    NativePump(DeviceCatalog.Device d,PadMapping m,Listener l){device=d;mapping=m;listener=l;}
    void start()throws Exception {
        input=Os.open(device.path,OsConstants.O_RDONLY|OsConstants.O_NONBLOCK|OsConstants.O_CLOEXEC,0);
        grabFromPrivilegedHelper(input);
        PadState state=new PadState(device.capabilities,mapping);
        uhid=Os.open("/dev/uhid",OsConstants.O_RDWR|OsConstants.O_NONBLOCK|OsConstants.O_CLOEXEC,0);write(HidCodec.create());running=true;
        sendReport(state.report());listener.event("started",device.signature);
        reader=new Thread(this::readOutput,"padbridge-hid");reader.start();
        worker=new Thread(()->pump(state),"padbridge-controller");worker.start();
    }
    // This runs only in the user-started root/shell app_process, never in the APK UID.
    // Lint assumes ordinary app execution and cannot model that boundary. Keep this
    // exception local: API 36/OEM behavior still requires a physical-device test.
    // No hidden-API exemptions or Android security settings are changed.
    @SuppressLint("BlockedPrivateApi")
    private static void grabFromPrivilegedHelper(FileDescriptor input)throws Exception {
        if(!RootCommand.privilegedUid(Os.getuid()))throw new SecurityException("Input grab requires an authorized root or ADB helper");
        // EVIOCGRAB tests the nonzero pointer argument as a boolean. Close releases it.
        try{Method ioctl=Os.class.getDeclaredMethod("ioctlInt",FileDescriptor.class,int.class);ioctl.setAccessible(true);ioctl.invoke(null,input,0x40044590);}
        catch(NoSuchMethodException older){Class<?> ref=Class.forName("android.system.Int32Ref");Method ioctl=Os.class.getDeclaredMethod("ioctlInt",FileDescriptor.class,int.class,ref);ioctl.setAccessible(true);ioctl.invoke(null,input,0x40044590,ref.getConstructor(int.class).newInstance(0));}
    }
    private synchronized void write(byte[] data)throws Exception {if(uhid==null||Os.write(uhid,data,0,data.length)!=data.length)throw new IOException("UHID write failed");}
    private synchronized void sendReport(byte[] report)throws Exception {latestReport=report;write(HidCodec.input(report));}
    // CREATE2 probes the HID asynchronously. An early INPUT2 can be accepted by
    // write() before input registration completes. Replay the current state once
    // the HID starts/opens, including a stationary controller's initial centre.
    // Cache lookup and write share the pump's lock so an old replay cannot race
    // behind a newer input report and restore stale buttons or axes.
    private synchronized void replayReport()throws Exception {write(HidCodec.input(latestReport));}
    private void readOutput(){byte[] data=new byte[4380];boolean started=false;try{
        StructPollfd p=new StructPollfd();p.fd=uhid;p.events=(short)OsConstants.POLLIN;
        while(running){if(Os.poll(new StructPollfd[]{p},300)==0){if(running&&started)replayReport();continue;}if(!running)break;int n=Os.read(p.fd,data,0,data.length);if(n<4)throw new IOException("HID disconnected");
            ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);int type=b.getInt();
            // START and OPEN can themselves arrive before the kernel finishes
            // probing. The existing idle poll retries the latest full report so
            // a stationary controller cannot remain at the kernel's zero state.
            if(type==2||type==4){started=true;replayReport();} // UHID_START / UHID_OPEN
            if(type==4)listener.event("opened","Virtual controller opened by Android");
            if((type==9||type==13)&&n>=8)write(ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).putInt(type==9?10:14).putInt(b.getInt()).putShort((short)5).array());
        }
    }catch(Exception e){if(running)listener.event("error",BridgeDaemon.reason(e));}finally{close();}}
    private void pump(PadState state){int size=android.os.Process.is64Bit()?24:16;byte[] data=new byte[size*64];int carry=0;try{
        StructPollfd p=new StructPollfd();p.fd=input;p.events=(short)OsConstants.POLLIN;
        while(running){if(Os.poll(new StructPollfd[]{p},300)==0)continue;if(!running)break;int count=Os.read(p.fd,data,carry,data.length-carry);if(count<=0)throw new IOException("Controller disconnected");count+=carry;int offset=0;
            while(offset+size<=count){ByteBuffer b=ByteBuffer.wrap(data,offset+size-8,8).order(ByteOrder.LITTLE_ENDIAN);int type=b.getShort()&65535,code=b.getShort()&65535,value=b.getInt();state.event(type,code,value);if(state.isDropped())throw new IOException("Input queue overflow; reconnect controller");if(type==0&&code==0)sendReport(state.report());offset+=size;}
            carry=count-offset;if(carry>0)System.arraycopy(data,offset,data,0,carry);
        }
    }catch(Exception e){if(running)listener.event("error",BridgeDaemon.reason(e));}finally{close();}}
    public void close(){
        if(closed.compareAndSet(false,true)){running=false;synchronized(this){if(uhid!=null){try{write(HidCodec.input(HidCodec.neutral()));}catch(Exception ignored){}try{Os.close(uhid);}catch(Exception ignored){}uhid=null;}if(input!=null){try{Os.close(input);}catch(Exception ignored){}input=null;}}listener.event("stopped","Controller released");}
        // The command thread waits before starting another pump, so old readers
        // cannot consume events from a newly reused file descriptor. Worker close
        // never joins another worker, avoiding a reciprocal-join deadlock.
        Thread caller=Thread.currentThread();if(caller!=worker&&caller!=reader){join(worker);join(reader);}
    }
    private static void join(Thread thread){if(thread!=null)try{thread.join(1000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
}
