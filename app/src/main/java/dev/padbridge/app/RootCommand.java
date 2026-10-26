package dev.padbridge.app;

/** Pure-Java command construction and privilege policy for the bundled helper. */
public final class RootCommand {
    private RootCommand(){}

    /** Returns one foreground shell command for an already-authorized su session. */
    public static String create(String apkPath,int port,String token){
        // Android-generated installed APK paths use these characters. Reject
        // whitespace, shell syntax, empty/dot segments, and non-installed files.
        if(apkPath==null||!apkPath.matches("/data/app/(?:[A-Za-z0-9_+=~-][A-Za-z0-9_.+=~-]*/)+base\\.apk"))
            throw new IllegalArgumentException("Expected an installed /data/app/.../base.apk");
        validateEndpoint(port,token);
        return "export CLASSPATH='"+apkPath+"' && exec /system/bin/app_process /system/bin dev.padbridge.app.BridgeDaemon --root --tcp "+port+" "+token;
    }

    static boolean privilegedUid(int uid){return uid==0||uid==2000;}

    static final class Launch {
        final int uid,port;
        final String mode,token;
        private Launch(int uid,int port,String mode,String token){this.uid=uid;this.port=port;this.mode=mode;this.token=token;}
    }

    /** Root must be explicit. The legacy --tcp form remains shell-UID only. */
    static Launch parse(int uid,String[] args){
        if(!privilegedUid(uid))throw new SecurityException("The helper requires an authorized root or ADB session");
        if(args==null)throw new IllegalArgumentException("Missing helper arguments");
        boolean root=args.length==4&&"--root".equals(args[0])&&"--tcp".equals(args[1]);
        boolean adb=args.length==3&&"--tcp".equals(args[0]);
        if(!root&&!adb)throw new IllegalArgumentException("Expected --root --tcp PORT TOKEN or --tcp PORT TOKEN");
        if(root&&uid!=0)throw new SecurityException("Root mode requires UID 0");
        if(adb&&uid!=2000)throw new SecurityException("ADB mode requires UID 2000");
        int offset=root?2:1;
        if(args[offset]==null||!args[offset].matches("[0-9]{4,5}"))throw new IllegalArgumentException("Invalid activation port");
        int port=Integer.parseInt(args[offset]);String token=args[offset+1];validateEndpoint(port,token);
        return new Launch(uid,port,root?"root":"adb",token);
    }

    private static void validateEndpoint(int port,String token){
        if(port<1024||port>65535)throw new IllegalArgumentException("Invalid activation port");
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Invalid activation token");
    }
}
