package io.github.vvb2060.keyattestation.attestation;

import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.github.vvb2060.keyattestation.AppApplication;
import io.github.vvb2060.keyattestation.R;

public record RevocationList(String status, String reason) {
    public static RevocationList get(BigInteger serialNumber) {
        String serialNumberString = serialNumber.toString(16).toLowerCase();
        JSONObject revocationStatus;
        try {
            revocationStatus = OfficialData.entries().getJSONObject(serialNumberString);
        } catch (JSONException e) {
            return null;
        }
        try {
            var status = revocationStatus.getString("status");
            var reason = revocationStatus.optString("reason", "unspecified");
            return new RevocationList(status, reason);
        } catch (JSONException e) {
            return new RevocationList("", "");
        }
    }

    @Override
    public String toString() {
        return "status is " + status + ", reason is " + reason;
    }
}
