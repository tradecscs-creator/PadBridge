package dev.padbridge.app;

import java.io.*;
import java.net.SocketTimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;

public final class IpcDeadlineTest {
    @Test public void carriageReturnsCannotBypassWireLimit()throws Exception {
        try{Ipc.line(new StringReader("\r\r\r\r\r\n"),4);fail();}
        catch(IOException expected){assertEquals("Message exceeds limit",expected.getMessage());}
    }
    @Test public void ordinaryCrLfStillWorks()throws Exception {
        assertEquals("token",Ipc.line(new StringReader("token\r\n"),64,System.nanoTime()+1000000000L));
    }
    @Test public void expiredRequestNeverStartsAnotherRead()throws Exception {
        Reader forbidden=new Reader(){
            public int read(char[] chars,int offset,int length){throw new AssertionError("expired request read input");}
            public void close(){}
        };
        try{Ipc.line(forbidden,64,System.nanoTime()-1);fail();}
        catch(SocketTimeoutException expected){assertEquals("Message deadline exceeded",expected.getMessage());}
    }
}
