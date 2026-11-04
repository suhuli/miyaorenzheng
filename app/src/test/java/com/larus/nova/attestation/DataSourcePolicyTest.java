package com.larus.nova.attestation;
import org.junit.Test;
import static org.junit.Assert.*;
public class DataSourcePolicyTest {
 @Test public void defaultIsOfficialFirst(){assertEquals(DataSourcePolicy.OFFICIAL_FIRST,DataSourcePolicy.normalize(null));assertEquals(DataSourcePolicy.OFFICIAL_FIRST,DataSourcePolicy.normalize("invalid"));}
 @Test public void mirrorPreferenceIsPreserved(){assertEquals(DataSourcePolicy.MIRROR_FIRST,DataSourcePolicy.normalize(DataSourcePolicy.MIRROR_FIRST));}
 @Test public void bothPresetsKeepLocalLast(){assertEquals("Google 官方 → jsDelivr → 本地缓存",DataSourcePolicy.label(null));assertEquals("jsDelivr → Google 官方 → 本地缓存",DataSourcePolicy.label(DataSourcePolicy.MIRROR_FIRST));}
}
