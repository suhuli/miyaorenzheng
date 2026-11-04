package com.larus.nova.attestation;
import java.util.*;
import co.nstant.in.cbor.*;
import co.nstant.in.cbor.model.*;
/** Android provisioning map; unknown values preserved for future formats. No Android APIs. */
public final class ProvisioningFields {
    public Long certsIssued;
    public String manufacturer, validatedAttestedEntity;
    public Boolean lostDevice;
    public record ExtraField(String path, String type, String value) {}
    public final List<ExtraField> extraFields = new ArrayList<>();
    public final java.util.Map<String,String> unknown = new LinkedHashMap<>();
    public static ProvisioningFields parse(byte[] bytes) throws CborException {
        if (bytes.length > 256 * 1024) throw new IllegalArgumentException("Provisioning extension too large");
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
                default -> {
                    int start = result.extraFields.size();
                    result.expand(key.toString(), value, 0);
                    StringBuilder text = new StringBuilder();
                    for (int i = start; i < result.extraFields.size(); i++) {
                        ExtraField field = result.extraFields.get(i);
                        if (text.length() > 0) text.append("\n");
                        text.append(field.path()).append(" [").append(field.type()).append("]: ").append(field.value());
                    }
                    result.unknown.put(key.toString(), text.toString());
                }
            }
        }
        return result;
    }
    private void expand(String path, DataItem value, int depth) {
        if (depth > 32 || extraFields.size() >= 1024) throw new IllegalArgumentException("Provisioning structure too complex");
        String type, display;
        if (value instanceof co.nstant.in.cbor.model.Map map) {
            type = "映射"; display = map.getKeys().size() + " 个子字段";
        } else if (value instanceof Array array) {
            type = "数组"; display = array.getDataItems().size() + " 个元素";
        } else if (value instanceof UnicodeString text) {
            type = "文本"; display = text.getString();
        } else if (value instanceof ByteString bytes) {
            type = "字节串";
            byte[] raw = bytes.getBytes();
            if (raw == null) display = "null";
            else { StringBuilder hex = new StringBuilder(); for (byte b : raw) hex.append(String.format(Locale.ROOT,"%02x",b & 255)); display = hex.toString(); }
        } else if (value instanceof co.nstant.in.cbor.model.Number number) {
            type = "整数"; display = number.getValue().toString();
        } else if (value instanceof SimpleValue simple) {
            switch (simple.getSimpleValueType()) {
                case TRUE -> { type = "布尔"; display = "true"; }
                case FALSE -> { type = "布尔"; display = "false"; }
                case NULL -> { type = "空值"; display = "null"; }
                default -> { type = "简单值"; display = simple.toString(); }
            }
        } else { type = "CBOR " + value.getMajorType(); display = value.toString(); }
        if (value.hasTag()) type += " / 标签 " + value.getTag().getValue();
        extraFields.add(new ExtraField(path, type, display));
        if (value instanceof co.nstant.in.cbor.model.Map map) {
            for (DataItem key : map.getKeys()) {
                String child = key instanceof co.nstant.in.cbor.model.Number ? "." + key : "[" + key + "]";
                expand(path + child, map.get(key), depth + 1);
            }
        } else if (value instanceof Array array) {
            int index = 0; for (DataItem item : array.getDataItems()) expand(path + "[" + index++ + "]", item, depth + 1);
        }
    }
}
