package com.larus.nova.attestation;
import org.junit.Test;
import static org.junit.Assert.*;
import co.nstant.in.cbor.*;
import co.nstant.in.cbor.model.*;
import java.io.*;
public class ProvisioningTreeTest {
 private byte[] encode(DataItem item)throws Exception{var out=new ByteArrayOutputStream();new CborEncoder(out).encode(item);return out.toByteArray();}
 private co.nstant.in.cbor.model.Map map(){return new co.nstant.in.cbor.model.Map();}
 @Test public void nestedFiveIsNotCollapsedOrSemanticallyGuessed()throws Exception{
  var child=map();child.put(new UnsignedInteger(1),new UnicodeString("APPROVED"));child.put(new UnsignedInteger(2),new UnicodeString("ab".repeat(32)));
  var root=map();root.put(new UnsignedInteger(5),child);var p=ProvisioningFields.parse(encode(root));
  assertEquals(3,p.extraFields.size());assertEquals("5.1",p.extraFields.get(1).path());assertEquals("文本",p.extraFields.get(1).type());assertEquals("APPROVED",p.extraFields.get(1).value());
  assertEquals("5.2",p.extraFields.get(2).path());assertEquals("ab".repeat(32),p.extraFields.get(2).value());assertTrue(p.unknown.get("5").contains("5.1"));
 }
 @Test public void bytesAreHexNotMistakenForTextOrNamedHash()throws Exception{
  var root=map();root.put(new UnsignedInteger(5),new ByteString(new byte[]{0,(byte)255,(byte)128}));var p=ProvisioningFields.parse(encode(root));
  assertEquals("字节串",p.extraFields.get(0).type());assertEquals("00ff80",p.extraFields.get(0).value());
 }
 @Test public void unknownArraysRetainFalseZeroAndNull()throws Exception{
  var root=map();var array=new Array();array.add(SimpleValue.FALSE);array.add(new UnsignedInteger(0));array.add(SimpleValue.NULL);root.put(new UnsignedInteger(99),array);
  var p=ProvisioningFields.parse(encode(root));assertEquals("99[0]",p.extraFields.get(1).path());assertEquals("false",p.extraFields.get(1).value());assertEquals("0",p.extraFields.get(2).value());assertEquals("null",p.extraFields.get(3).value());
 }
 @Test public void fiveDoesNotHaveToBeAMap()throws Exception{
  var root=map();root.put(new UnsignedInteger(5),new UnicodeString("future-format"));assertEquals("future-format",ProvisioningFields.parse(encode(root)).extraFields.get(0).value());
 }
 @Test public void tooManyNodesRejected()throws Exception{
  var root=map();var array=new Array();for(int i=0;i<1100;i++)array.add(new UnsignedInteger(i));root.put(new UnsignedInteger(5),array);byte[] raw=encode(root);
  assertThrows(IllegalArgumentException.class,()->ProvisioningFields.parse(raw));
 }
 @Test public void oversizedExtensionRejectedBeforeDecoding(){assertThrows(IllegalArgumentException.class,()->ProvisioningFields.parse(new byte[256*1024+1]));}
}
