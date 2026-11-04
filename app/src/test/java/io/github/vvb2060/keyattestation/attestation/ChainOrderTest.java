package io.github.vvb2060.keyattestation.attestation;
import org.junit.Test;
import java.security.cert.*;
import java.util.*;
import static org.junit.Assert.*;

public class ChainOrderTest {
    private X509Certificate cert(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/chains/" + name + ".der")) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }
    private boolean signedBy(X509Certificate c, X509Certificate p) {
        if (!c.getIssuerX500Principal().equals(p.getSubjectX500Principal())) return false;
        try { c.verify(p.getPublicKey()); return true; } catch (Exception e) { return false; }
    }
    private List<X509Certificate> select(List<X509Certificate> bag, X509Certificate preferred) {
        return ChainOrder.select(bag, preferred, this::signedBy, c -> signedBy(c,c));
    }
    private List<X509Certificate> path(String family) throws Exception {
        var p = new ArrayList<X509Certificate>();
        p.add(cert(family+"-leaf"));
        for(int i=family.equals("new")?3:2;i>=0;i--) p.add(cert(family+"-"+i));
        return p;
    }
    private void reportShape(String family) throws Exception {
        var expected=path(family);
        var bag=new ArrayList<>(expected);
        bag.add(cert(family+"-attest"));
        bag.addAll(expected.subList(1,expected.size()));
        // Exactly the two-full-chains shape seen in BOTH user reports.
        var actual=select(bag,expected.get(0));
        assertEquals(expected,actual);
        assertEquals(new HashSet<>(actual).size(),actual.size());
        for(int i=0;i+1<actual.size();i++) assertTrue(signedBy(actual.get(i),actual.get(i+1)));
        assertTrue(signedBy(actual.get(actual.size()-1),actual.get(actual.size()-1)));
    }
    @Test public void newEcRootTwoChainsRegression() throws Exception { reportShape("new"); }
    @Test public void oldRsaRootTwoChainsRegression() throws Exception { reportShape("old"); }
    @Test public void unorderedNewImport() throws Exception { var p=path("new");var b=new ArrayList<>(p);Collections.reverse(b);assertEquals(p,select(b,null)); }
    @Test public void unorderedOldImport() throws Exception { var p=path("old");var b=new ArrayList<>(p);Collections.reverse(b);assertEquals(p,select(b,null)); }
    @Test public void duplicateCertificatesAreDeduplicated() throws Exception { var p=path("new");var b=new ArrayList<>(p);b.addAll(p);assertEquals(p,select(b,null)); }
    @Test public void selectedAttestationBranchIsSeparate() throws Exception {var p=path("old");var b=new ArrayList<>(p);var a=cert("old-attest");b.add(a);var result=select(b,a);assertEquals(a,result.get(0));assertFalse(result.contains(p.get(0)));}
    @Test public void missingIssuerRejected() throws Exception {var p=path("new");var leaf=p.get(0);p.remove(1);assertThrows(IllegalArgumentException.class,()->select(p,leaf));}
    @Test public void forgedSameIssuerNameRejected() throws Exception {var p=path("new");var bad=cert("new-forged");p.set(0,bad);assertThrows(IllegalArgumentException.class,()->select(p,bad));}
    @Test public void ambiguousImportRejectedRatherThanConcatenated() throws Exception {var p=path("old");p.add(cert("old-attest"));assertThrows(IllegalArgumentException.class,()->select(p,null));}
    @Test public void unrelatedImportRejected() throws Exception {var p=path("new");p.add(cert("old-0"));assertThrows(IllegalArgumentException.class,()->select(p,null));}
    @Test public void unknownPreferredLeafRejected() throws Exception {var p=path("old");var a=cert("new-leaf");assertThrows(IllegalArgumentException.class,()->select(p,a));}
    @Test public void emptyInputRejected() {assertThrows(IllegalArgumentException.class,()->select(List.of(),null));}
    @Test public void issuerCycleRejected() { assertThrows(IllegalArgumentException.class,()->ChainOrder.select(List.of("a","b"),"a",(c,p)->!c.equals(p),c->false)); }
}
