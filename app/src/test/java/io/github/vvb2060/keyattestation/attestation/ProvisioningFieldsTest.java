package io.github.vvb2060.keyattestation.attestation;
import org.junit.Test;
import static org.junit.Assert.*;
import co.nstant.in.cbor.*;
import co.nstant.in.cbor.model.*;
import java.io.*;
public class ProvisioningFieldsTest {
    byte[] bytes(co.nstant.in.cbor.model.Map map) throws Exception {var b=new ByteArrayOutputStream();new CborEncoder(b).encode(map);return b.toByteArray();}
    @Test public void allKnownFields() throws Exception {
        var m=new co.nstant.in.cbor.model.Map();m.put(new UnsignedInteger(1),new UnsignedInteger(8));m.put(new UnsignedInteger(3),new UnicodeString("Vendor"));m.put(new UnsignedInteger(4),new UnicodeString("TEE"));m.put(new UnsignedInteger(6),SimpleValue.TRUE);
        var p=ProvisioningFields.parse(bytes(m));assertEquals(Long.valueOf(8),p.certsIssued);assertEquals("Vendor",p.manufacturer);assertEquals("TEE",p.validatedAttestedEntity);assertEquals(Boolean.TRUE,p.lostDevice);
    }
    @Test public void missingNotFalse() throws Exception {var p=ProvisioningFields.parse(bytes(new co.nstant.in.cbor.model.Map()));assertNull(p.lostDevice);assertNull(p.certsIssued);}
    @Test public void explicitFalse() throws Exception {var m=new co.nstant.in.cbor.model.Map();m.put(new UnsignedInteger(6),SimpleValue.FALSE);assertEquals(Boolean.FALSE,ProvisioningFields.parse(bytes(m)).lostDevice);}
    @Test public void unknownFieldsPreserved() throws Exception {var m=new co.nstant.in.cbor.model.Map();m.put(new UnsignedInteger(99),new UnicodeString("future"));assertFalse(ProvisioningFields.parse(bytes(m)).unknown.isEmpty());}
    @Test public void negativeCountRejected() throws Exception {var m=new co.nstant.in.cbor.model.Map();m.put(new UnsignedInteger(1),new NegativeInteger(-2));assertThrows(IllegalArgumentException.class,()->ProvisioningFields.parse(bytes(m)));}
    @Test public void wrongFlagTypeRejected() throws Exception {var m=new co.nstant.in.cbor.model.Map();m.put(new UnsignedInteger(6),new UnicodeString("false"));assertThrows(IllegalArgumentException.class,()->ProvisioningFields.parse(bytes(m)));}
    @Test public void wrongRootTypeRejected() {assertThrows(IllegalArgumentException.class,()->ProvisioningFields.parse(new byte[]{1}));}
}
