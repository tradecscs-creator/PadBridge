package dev.padbridge.app;
import android.app.Activity;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ReportProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    static void share(Activity activity,String text)throws IOException {
        File directory=new File(activity.getCacheDir(),"reports");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create report");
        File[] old=directory.listFiles();if(old!=null)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>86400000L)f.delete();
        String name="diagnostic-"+UUID.randomUUID()+".txt";File report=new File(directory,name);try(FileOutputStream out=new FileOutputStream(report)){out.write(text.getBytes(StandardCharsets.UTF_8));}
        Uri uri=Uri.parse("content://"+activity.getPackageName()+".reports/"+name);Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);share.setClipData(ClipData.newRawUri("PadBridge diagnostics",uri));activity.startActivity(Intent.createChooser(share,"Share diagnostics"));
    }
    private void validate(Uri uri){if(uri.getPath()==null||!uri.getPath().matches("/diagnostic-[0-9a-f-]{36}\\.txt"))throw new IllegalArgumentException("Unknown report");}
    private File file(Uri uri){validate(uri);return new File(new File(getContext().getCacheDir(),"reports"),uri.getLastPathSegment());}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {if(!mode.equals("r"))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});File f=file(u);c.addRow(new Object[]{"PadBridge-diagnostic.txt",f.length()});return c;}
    public String getType(Uri u){validate(u);return "text/plain";}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
