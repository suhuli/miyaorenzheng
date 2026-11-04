package io.github.vvb2060.keyattestation.attestation;
import java.util.*;
import co.nstant.in.cbor.*;
import co.nstant.in.cbor.model.*;
/** Android provisioning map; unknown values preserved for future formats. No Android APIs. */
public final class ProvisioningFields {
    public Long certsIssued;
    public String manufacturer, validatedAttestedEntity;
    public Boolean lostDevice;
    public final java.util.Map<String,String> unknown = new LinkedHashMap<>();
    public static ProvisioningFields parse(byte[] bytes) throws CborException {
        var list = CborDecoder.decode(bytes);
        if (list.size()!=1 || !(list.get(0) instanceof co.nstant.in.cbor.model.Map map))
            throw new IllegalArgumentException("Provisioning info must be one CBOR map");
        var result = new ProvisioningFields();
        for (var key : map.getKeys()) {
            var value = map.get(key);
            if (!(key instanceof co.nstant.in.cbor.model.Number n)) throw new IllegalArgumentException("Non-integer map key");
            if (n.getValue().bitLength()>31) throw new IllegalArgumentException("Map key too large");
            int tag = n.getValue().intValue();
            switch(tag) {
                case 1 -> { if (!(value instanceof UnsignedInteger num)) throw new IllegalArgumentException("Invalid count"); if(num.getValue().bitLength()>63) throw new IllegalArgumentException("Count too large"); result.certsIssued = num.getValue().longValue(); }
                case 3 -> { if (!(value instanceof UnicodeString text)) throw new IllegalArgumentException("Invalid manufacturer"); result.manufacturer = text.getString(); }
                case 4 -> { if (!(value instanceof UnicodeString text)) throw new IllegalArgumentException("Invalid entity"); result.validatedAttestedEntity = text.getString(); }
                case 6 -> { if (!(value instanceof SimpleValue v) || (v.getSimpleValueType()!=SimpleValueType.TRUE && v.getSimpleValueType()!=SimpleValueType.FALSE)) throw new IllegalArgumentException("Invalid lost flag"); result.lostDevice = v.getSimpleValueType()==SimpleValueType.TRUE; }
                default -> result.unknown.put(key.toString(),value.toString());
            }
        }
        return result;
    }
}
