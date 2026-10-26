package dev.padbridge.app;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Original compact gamepad descriptor. Axis/button usages match Android's
 * Vendor_045e_Product_02e0.kl. This is an input-only HID profile, not a complete
 * implementation of the Microsoft Bluetooth protocol or authentication. */
public final class HidCodec {
    public static final int VENDOR = 0x045e, PRODUCT = 0x02e0;
    public static final String NAME = "Xbox Wireless Controller";
    public static final String PHYSICAL = "padbridge:virtual";
    public static final byte[] DESCRIPTOR = hex(
        "05 01 09 05 A1 01 " +
        // X,Y,Rx,Ry unsigned 16-bit sticks.
        "09 30 09 31 09 33 09 34 15 00 27 FF FF 00 00 75 10 95 04 81 02 " +
        // Z,Rz unsigned triggers.
        "09 32 09 35 15 00 26 FF 7F 75 10 95 02 81 02 " +
        // Ten contiguous buttons: A B X Y LB RB Back Start LS RS.
        "05 09 19 01 29 0A 15 00 25 01 75 01 95 0A 81 02 " +
        "75 01 95 06 81 03 " +
        // Eight-way hat with 0 as neutral (null).
        "05 01 09 39 15 01 25 08 35 00 46 3B 01 65 14 75 04 95 01 81 42 " +
        "65 00 75 04 95 01 81 03 C0");

    private HidCodec() {}
    private static byte[] hex(String text) {
        String[] parts = text.trim().split("\\s+");
        byte[] result = new byte[parts.length];
        for (int i=0;i<parts.length;i++) result[i]=(byte)Integer.parseInt(parts[i],16);
        return result;
    }
    public static byte[] create() {
        ByteBuffer b = ByteBuffer.allocate(280 + DESCRIPTOR.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(11); // UHID_CREATE2
        b.put(NAME.getBytes(StandardCharsets.UTF_8));
        b.position(132);
        b.put(PHYSICAL.getBytes(StandardCharsets.US_ASCII));
        b.position(260);
        b.putShort((short)DESCRIPTOR.length).putShort((short)5); // BUS_BLUETOOTH
        b.putInt(VENDOR).putInt(PRODUCT).putInt(0x0903).putInt(0);
        b.put(DESCRIPTOR);
        return b.array();
    }
    public static byte[] report(int lx,int ly,int rx,int ry,int lt,int rt,int buttons,int hat) {
        ByteBuffer b=ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)clamp(lx,65535)).putShort((short)clamp(ly,65535));
        b.putShort((short)clamp(rx,65535)).putShort((short)clamp(ry,65535));
        b.putShort((short)clamp(lt,32767)).putShort((short)clamp(rt,32767));
        b.putShort((short)(buttons & 1023)).put((byte)(hat>=0 && hat<=8 ? hat : 0));
        return b.array();
    }
    public static byte[] input(byte[] report) {
        ByteBuffer b=ByteBuffer.allocate(6+report.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(12).putShort((short)report.length).put(report);
        return b.array();
    }
    public static byte[] neutral() { return report(32768,32768,32768,32768,0,0,0,0); }
    public static int clamp(int value,int max) {return Math.max(0,Math.min(max,value));}
    public static int scale(int value,int min,int max,int target) {
        if (max<=min) return 0;
        return clamp((int)Math.round((value-(double)min)*target/(max-(double)min)),target);
    }
    public static int hat(int x,int y) {
        x=Integer.signum(x);y=Integer.signum(y);
        if(y<0) return x<0?8:x>0?2:1;
        if(y>0) return x<0?6:x>0?4:5;
        return x<0?7:x>0?3:0;
    }
}
