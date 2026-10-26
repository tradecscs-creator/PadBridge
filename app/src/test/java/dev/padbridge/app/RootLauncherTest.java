package dev.padbridge.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** No actual su is executed: exercises cancellation, ownership and diagnostics. */
public final class RootLauncherTest {
    private static final String TOKEN="abcdefghijklmnopqrstuvwxyz0123456789_-ABCDE";
    private static class FakeProcess extends Process {
        final ByteArrayOutputStream stdin=new ByteArrayOutputStream();
        final InputStream stdout;final int exitCode;final AtomicInteger destroys=new AtomicInteger();
        FakeProcess(String output,int code){stdout=new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));exitCode=code;}
        FakeProcess(InputStream output){stdout=output;exitCode=0;}
        public OutputStream getOutputStream(){return stdin;}
        public InputStream getInputStream(){return stdout;}
        public InputStream getErrorStream(){return new ByteArrayInputStream(new byte[0]);}
        public int waitFor(){return exitCode;}
        public int exitValue(){return exitCode;}
        public void destroy(){destroys.incrementAndGet();try{stdout.close();}catch(IOException ignored){}}
    }
    @Test public void constructionAndCloseDoNotRequestRoot()throws Exception {
        AtomicInteger calls=new AtomicInteger();
        RootLauncher launch=new RootLauncher("fixed command",TOKEN,()->{calls.incrementAndGet();return new FakeProcess("",0);},r->fail());
        assertEquals(0,calls.get());launch.close();launch.start();assertEquals(0,calls.get());
    }
    @Test public void onlyOneProcessStartsAndCommandGoesToStdin()throws Exception {
        FakeProcess child=new FakeProcess("",0);AtomicInteger calls=new AtomicInteger();CountDownLatch exit=new CountDownLatch(1);
        RootLauncher launch=new RootLauncher("exec fixed-command",TOKEN,()->{calls.incrementAndGet();return child;},r->exit.countDown());
        launch.start();launch.start();assertTrue(exit.await(3,TimeUnit.SECONDS));
        assertEquals(1,calls.get());assertEquals("exec fixed-command\n",child.stdin.toString("UTF-8"));assertTrue(child.destroys.get()>0);launch.close();
    }
    @Test public void missingSuIsReportedWithoutThrowingOnCaller()throws Exception {
        CountDownLatch exit=new CountDownLatch(1);AtomicReference<RootLauncher.Result> result=new AtomicReference<>();
        RootLauncher launch=new RootLauncher("fixed",TOKEN,()->{throw new IOException("su unavailable");},r->{result.set(r);exit.countDown();});
        launch.start();assertTrue(exit.await(3,TimeUnit.SECONDS));assertTrue(result.get().unavailable);assertEquals(-1,result.get().code);launch.close();
    }
    @Test public void managerDenialAndSecretRedactionArePreserved()throws Exception {
        FakeProcess child=new FakeProcess("permission denied: "+TOKEN,1);CountDownLatch exit=new CountDownLatch(1);AtomicReference<RootLauncher.Result> result=new AtomicReference<>();
        RootLauncher launch=new RootLauncher("fixed",TOKEN,()->child,r->{result.set(r);exit.countDown();});
        launch.start();assertTrue(exit.await(3,TimeUnit.SECONDS));assertEquals(1,result.get().code);assertFalse(result.get().unavailable);
        assertTrue(result.get().detail.contains("permission denied"));assertFalse(result.get().detail.contains(TOKEN));launch.close();
    }
    @Test public void diagnosticOutputHasABound()throws Exception {
        StringBuilder text=new StringBuilder();for(int n=0;n<20000;n++)text.append('x');text.append(" final diagnostic");
        CountDownLatch exit=new CountDownLatch(1);AtomicReference<RootLauncher.Result> result=new AtomicReference<>();
        RootLauncher launch=new RootLauncher("fixed",TOKEN,()->new FakeProcess(text.toString(),1),r->{result.set(r);exit.countDown();});
        launch.start();assertTrue(exit.await(3,TimeUnit.SECONDS));assertTrue(result.get().detail.length()<=4096);assertTrue(result.get().detail.endsWith("final diagnostic"));launch.close();
    }
    @Test public void lateProcessAfterCancellationIsDestroyedWithoutRunningCommand()throws Exception {
        CountDownLatch spawned=new CountDownLatch(1),allowReturn=new CountDownLatch(1),destroyed=new CountDownLatch(1);
        AtomicInteger callbacks=new AtomicInteger();
        FakeProcess child=new FakeProcess("",0){public void destroy(){super.destroy();destroyed.countDown();}};
        RootLauncher launch=new RootLauncher("must not run",TOKEN,()->{
            spawned.countDown();try{if(!allowReturn.await(3,TimeUnit.SECONDS))throw new IOException("test timeout");}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}return child;
        },r->callbacks.incrementAndGet());
        launch.start();assertTrue(spawned.await(3,TimeUnit.SECONDS));launch.close();allowReturn.countDown();
        assertTrue(destroyed.await(3,TimeUnit.SECONDS));assertEquals(0,child.stdin.size());assertEquals(0,callbacks.get());
    }
    @Test public void cancelUnblocksAWaitingClientAndSuppressesCallback()throws Exception {
        CountDownLatch reading=new CountDownLatch(1),closed=new CountDownLatch(1),returned=new CountDownLatch(1);
        InputStream blocking=new InputStream(){
            public int read()throws IOException {
                reading.countDown();try{if(!closed.await(3,TimeUnit.SECONDS))throw new IOException("test timeout");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}
                returned.countDown();return -1;
            }
            public void close(){closed.countDown();}
        };
        FakeProcess child=new FakeProcess(blocking);AtomicInteger callbacks=new AtomicInteger();
        RootLauncher launch=new RootLauncher("fixed",TOKEN,()->child,r->callbacks.incrementAndGet());
        launch.start();assertTrue(reading.await(3,TimeUnit.SECONDS));launch.close();assertTrue(returned.await(3,TimeUnit.SECONDS));
        assertTrue(child.destroys.get()>0);assertEquals(0,callbacks.get());
    }
}
