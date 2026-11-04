package io.github.vvb2060.keyattestation.attestation;
import org.junit.Test;
import static org.junit.Assert.*;
public class RkpPolicyTest {
    @Test public void adjacentExtensionIsEvidence() { assertTrue(RkpPolicy.hasAdjacentEvidence(new boolean[]{false,true,false}, new boolean[]{false,false,true})); }
    @Test public void missingExtensionIsUnknown() { assertFalse(RkpPolicy.hasAdjacentEvidence(new boolean[]{false,false,false}, new boolean[]{false,false,true})); }
    @Test public void nonAdjacentIsRejected() { assertFalse(RkpPolicy.hasAdjacentEvidence(new boolean[]{false,true,false,false}, new boolean[]{false,false,false,true})); }
    @Test public void rootExtensionIsNotEvidence() { assertFalse(RkpPolicy.hasAdjacentEvidence(new boolean[]{true,false}, new boolean[]{false,true})); }
    @Test public void nearestExtensionWins() { assertFalse(RkpPolicy.hasAdjacentEvidence(new boolean[]{false,true,false,true,false}, new boolean[]{false,false,false,false,true})); }
    @Test public void malformedInputsAreRejected() { assertFalse(RkpPolicy.hasAdjacentEvidence(null,null)); assertFalse(RkpPolicy.hasAdjacentEvidence(new boolean[1],new boolean[2])); }
    @Test public void emptyChainIsUnknown() { assertFalse(RkpPolicy.hasAdjacentEvidence(new boolean[0],new boolean[0])); }
}
