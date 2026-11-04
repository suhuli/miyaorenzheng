package io.github.vvb2060.keyattestation.dashboard;
import org.junit.Test;
import static org.junit.Assert.*;
public class CertificateVerdictTest {
    CertificateVerdict verdict(boolean sign,boolean root,boolean revoked,boolean dates,boolean compat,boolean fresh,boolean device,boolean lost){return CertificateVerdict.evaluate(sign,root,revoked,dates,compat,fresh,device,lost);}
    @Test public void allGoodIsGreen(){assertEquals(0,verdict(true,true,false,true,false,true,false,false).level());}
    @Test public void badSignatureIsRed(){assertEquals(2,verdict(false,true,false,true,false,true,false,false).level());}
    @Test public void revokedIsRed(){assertEquals(2,verdict(true,true,true,true,false,true,false,false).level());}
    @Test public void expiredIsRed(){assertEquals(2,verdict(true,true,false,false,false,true,false,false).level());}
    @Test public void unknownRootIsYellow(){assertEquals(1,verdict(true,false,false,true,false,true,false,false).level());}
    @Test public void staleDataNeverGreen(){assertEquals(1,verdict(true,true,false,true,false,false,false,false).level());}
    @Test public void factoryExceptionIsYellowNotFalseRed(){assertEquals("兼容通过",verdict(true,true,false,true,true,true,false,false).title());}
    @Test public void deviceWarningNotPhoneSafe(){assertEquals(1,verdict(true,true,false,true,false,true,true,false).level());}
    @Test public void lostFlagWarns(){assertEquals(1,verdict(true,true,false,true,false,true,false,true).level());}
}
