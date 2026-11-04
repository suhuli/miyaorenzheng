package com.larus.nova.attestation;
import org.junit.Test;
import static org.junit.Assert.*;
public class ValidityPolicyTest {
    @Test public void knownFactoryCompatibility() {assertTrue(ValidityPolicy.allowExpired(true,true,false,false));}
    @Test public void neverRelaxRkp() {assertFalse(ValidityPolicy.allowExpired(true,true,true,false));}
    @Test public void unknownRootNotRelaxed() {assertFalse(ValidityPolicy.allowExpired(false,true,false,false));}
    @Test public void ambiguousLegacyPathNotRelaxed() {assertFalse(ValidityPolicy.allowExpired(true,false,false,false));}
    @Test public void strictModeAlwaysWins() {assertFalse(ValidityPolicy.allowExpired(true,true,false,true));}
}
