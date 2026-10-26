package dev.padbridge.app;
import java.io.*;
import java.net.SocketTimeoutException;

public final class Ipc {
    private Ipc(){}
    public static String line(Reader input,int limit)throws IOException {
        return line(input,limit,0);
    }
    /** Socket read timeouts bound idle reads; this additionally bounds slow senders. */
    public static String line(Reader input,int limit,long deadlineNanos)throws IOException {
        StringBuilder text=new StringBuilder();int consumed=0;
        while(true){
            checkDeadline(deadlineNanos);int ch=input.read();checkDeadline(deadlineNanos);
            if(ch==-1)return text.length()==0?null:text.toString();
            if(ch=='\n')return text.toString();
            if(++consumed>limit)throw new IOException("Message exceeds limit");
            if(ch!='\r')text.append((char)ch);
        }
    }
    private static void checkDeadline(long deadline)throws SocketTimeoutException {
        if(deadline!=0&&System.nanoTime()-deadline>=0)throw new SocketTimeoutException("Message deadline exceeded");
    }
}
