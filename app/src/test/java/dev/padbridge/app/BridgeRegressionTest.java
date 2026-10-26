package dev.padbridge.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Numeric AOSP getevent -p -v8 fixtures, including EVIOCGPHYS metadata. */
public final class BridgeRegressionTest {
    private static String device(int number,String name,String location,boolean joystick){
        return "add device "+number+": /dev/input/event"+number+"\n"
            +"  bus:      0005\n  vendor    045e\n  product   02e0\n  version   0903\n"
            +"  name:     \""+name+"\"\n  location: \""+location+"\"\n  id:       \"sample-unit-id\"\n"
            +"  events:\n    KEY (0001): "+(joystick?"0120 0121 0122 0123 0124 0125 0126 0127":"0130 0131 0133 0134 0136 0137 013a 013b 013d 013e")+"\n"
            +"    ABS (0003): 0000 : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0\n"
            +"                0001 : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0\n"
            +"                0003 : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0\n"
            +"                0004 : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0\n"
            +"  input props:\n    <none>\n";
    }

    @Test public void parsesInformationBeforeName(){
        List<DeviceCatalog.Device> found=DeviceCatalog.parse(device(9,"Generic USB Controller","usb-1/input0",false));
        assertEquals(1,found.size());assertEquals("/dev/input/event9",found.get(0).path);
        assertEquals(4,found.get(0).axes.size());assertTrue(found.get(0).buttons.contains(304));
    }

    @Test public void genuineXboxIsNotRejectedByItsName(){
        assertEquals(1,DeviceCatalog.parse(device(9,HidCodec.NAME,"bluetooth/input0",false)).size());
    }

    @Test public void ownOutputAndLegacyBridgeAreRejectedByPhysicalMarker(){
        assertTrue(DeviceCatalog.parse(device(10,HidCodec.NAME,HidCodec.PHYSICAL,false)).isEmpty());
        assertTrue(DeviceCatalog.parse(device(10,HidCodec.NAME,"padbridge:8bitdo",false)).isEmpty());
        assertTrue(DeviceCatalog.parse(device(10,HidCodec.NAME,HidCodec.PHYSICAL+"/input0",false)).isEmpty());
    }

    @Test public void markerIsMatchedAsLocationRatherThanArbitraryText(){
        assertEquals(1,DeviceCatalog.parse(device(9,HidCodec.NAME,"usb/input0",false).replace("sample-unit-id",HidCodec.PHYSICAL)).size());
    }

    @Test public void mixedEnumerationNeverFeedsBackOurVirtualPad(){
        String dump=device(9,HidCodec.NAME,"bluetooth/input0",false)
            +device(10,HidCodec.NAME,HidCodec.PHYSICAL,false)
            +device(11,"DirectInput Joystick","usb/input0",true);
        List<DeviceCatalog.Device> found=DeviceCatalog.parse(dump);
        assertEquals(2,found.size());assertEquals("/dev/input/event9",found.get(0).path);
        assertEquals("/dev/input/event11",found.get(1).path);
    }

    @Test public void classicJoystickButtonsCanReachTheMappingWizard(){
        List<DeviceCatalog.Device> found=DeviceCatalog.parse(device(9,"DirectInput Joystick","usb/input0",true));
        assertEquals(1,found.size());assertTrue(found.get(0).buttons.contains(288));
    }

    @Test public void expandedJoystickRangeDoesNotAllowTouchOrKeyboard(){
        String joystick=device(9,"Composite device","usb/input0",true);
        assertTrue(DeviceCatalog.parse(joystick.replace("<none>","INPUT_PROP_DIRECT")).isEmpty());
        assertTrue(DeviceCatalog.parse(joystick.replace("0120 0121","014a 0121")).isEmpty());
        assertTrue(DeviceCatalog.parse(joystick.replace("0120 0121 0122 0123 0124 0125 0126 0127","001e 0030 002e")).isEmpty());
    }

    @Test public void modelSignatureDoesNotPersistUnitLocationOrId(){
        String first=device(9,"Controller","bluetooth/input0",false);
        String second=first.replace("bluetooth/input0","usb/input4").replace("sample-unit-id","another-unit");
        assertEquals(DeviceCatalog.parse(first).get(0).signature,DeviceCatalog.parse(second).get(0).signature);
    }

    @Test public void parserAcceptsWindowsFixtureLineEndings(){
        assertEquals(1,DeviceCatalog.parse(device(9,"Controller","usb/input0",false).replace("\n","\r\n")).size());
    }

    @Test public void uhidPhysicalMarkerMatchesEnumerationFilter(){
        byte[] create=HidCodec.create();String physical=new String(create,132,HidCodec.PHYSICAL.length(),StandardCharsets.US_ASCII);
        assertEquals(HidCodec.PHYSICAL,physical);
    }
}
