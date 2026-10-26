package dev.padbridge.app;

import org.junit.Test;
import static org.junit.Assert.*;

/** Shell validation and UID policy run on a JVM; no su/device is invoked here. */
public final class RootCommandTest {
    private static final String APK="/data/app/~~Qw_123-AB==/dev.padbridge.app.beta-abc123==/base.apk";
    private static final String TOKEN="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN_12";
    private static String[] root(){return new String[]{"--root","--tcp","23456",TOKEN};}
    private static String[] adb(){return new String[]{"--tcp","23456",TOKEN};}

    @Test public void buildsForegroundCommandFromInstalledApk(){
        assertEquals("export CLASSPATH='"+APK+"' && exec /system/bin/app_process /system/bin dev.padbridge.app.BridgeDaemon --root --tcp 23456 "+TOKEN,
            RootCommand.create(APK,23456,TOKEN));
    }

    @Test public void supportsOlderSingleDirectoryInstalledPath(){
        assertTrue(RootCommand.create("/data/app/dev.padbridge.app-1/base.apk",1024,TOKEN).contains("--root --tcp 1024 "));
        assertTrue(RootCommand.create(APK,65535,TOKEN).contains("--root --tcp 65535 "));
    }

    @Test public void rejectsApkPathShellInjectionAndTraversal(){
        for(String path:new String[]{null,"", "/sdcard/base.apk","/data/local/tmp/base.apk","/data/app/base.apk",
                "/data/app/../package/base.apk","/data/app/package/../base.apk","/data/app//package/base.apk",
                "/data/app/a'/base.apk","/data/app/$(id)/base.apk","/data/app/`id`/base.apk",
                "/data/app/a;id/base.apk","/data/app/a b/base.apk","/data/app/a\n/base.apk",
                "/data/app/package/split_config.apk",APK+"\n",APK+"; id"}){
            expectRejected(()->RootCommand.create(path,23456,TOKEN));
        }
    }

    @Test public void rejectsTokenShellInjectionAndInvalidLengths(){
        for(String token:new String[]{null,"",TOKEN.substring(1),TOKEN+"a",TOKEN+"\n",TOKEN.substring(1)+";",
                TOKEN.substring(1)+"'",TOKEN.substring(1)+"$",TOKEN.substring(1)+" ",TOKEN.substring(1)+"/",TOKEN.substring(1)+"+"}){
            expectRejected(()->RootCommand.create(APK,23456,token));
        }
    }

    @Test public void rejectsOutOfRangePorts(){
        for(int port:new int[]{Integer.MIN_VALUE,-1,0,1023,65536,Integer.MAX_VALUE})expectRejected(()->RootCommand.create(APK,port,TOKEN));
    }

    @Test public void rootModeRequiresUidZero(){
        RootCommand.Launch launch=RootCommand.parse(0,root());
        assertEquals("root",launch.mode);assertEquals(0,launch.uid);assertEquals(23456,launch.port);assertEquals(TOKEN,launch.token);
        expectRejected(()->RootCommand.parse(2000,root()));
    }

    @Test public void legacyTcpModeRemainsAdbOnly(){
        RootCommand.Launch launch=RootCommand.parse(2000,adb());
        assertEquals("adb",launch.mode);assertEquals(2000,launch.uid);
        expectRejected(()->RootCommand.parse(0,adb()));
    }

    @Test public void ordinaryAppAndOtherSystemUidsCannotRunEitherMode(){
        for(int uid:new int[]{-1,1000,1001,10000,10234,99999}){
            assertFalse(RootCommand.privilegedUid(uid));
            expectRejected(()->RootCommand.parse(uid,root()));
            expectRejected(()->RootCommand.parse(uid,adb()));
        }
        assertTrue(RootCommand.privilegedUid(0));assertTrue(RootCommand.privilegedUid(2000));
    }

    @Test public void rejectsUnexpectedOrIncompleteArgumentForms(){
        for(String[] args:new String[][]{null,new String[0],new String[]{"--root"},new String[]{"--root","--tcp","23456"},
                new String[]{"--tcp","23456",TOKEN,"extra"},new String[]{"--root","--root","23456",TOKEN},
                new String[]{"--root","--tcp","23456",TOKEN,";id"},new String[]{null,"--tcp","23456",TOKEN}}){
            expectRejected(()->RootCommand.parse(0,args));
            expectRejected(()->RootCommand.parse(2000,args));
        }
    }

    @Test public void parserChecksEndpointBeforePrivilegeUse(){
        for(String port:new String[]{null,"", "1023","65536","23456;id","+23456","-23456"," 23456","2.3456","999999999999"}){
            expectRejected(()->RootCommand.parse(0,new String[]{"--root","--tcp",port,TOKEN}));
        }
        expectRejected(()->RootCommand.parse(0,new String[]{"--root","--tcp","23456",TOKEN+";id"}));
    }

    private static void expectRejected(Runnable operation){
        try{operation.run();fail("Expected invalid command or privilege rejection");}
        catch(IllegalArgumentException|SecurityException expected){/* Expected validation failure. */}
    }
}
