package com.jio.calling.provisioning;

import android.util.Log;
import android.util.Xml;

import com.jio.calling.model.JioAccount;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;

/**
 * Parses the wap-provisioningdoc XML document returned by the Jio router.
 */
public class JioProvisioningParser {
    private static final String TAG = "JioProvisioningParser";

    /**
     * Parses the router XML configuration into a JioAccount model.
     *
     * @param xmlContent Raw XML response from port 8443
     * @param routerHost The host/IP of the router used for connection
     * @return Fully populated JioAccount or null if invalid
     */
    public static JioAccount parse(String xmlContent, String routerHost) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            Log.e(TAG, "Empty XML content received");
            return null;
        }

        JioAccount account = new JioAccount();
        account.setProxyHost(routerHost);
        account.setProxyPort(5068);

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xmlContent));

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "parm".equalsIgnoreCase(parser.getName())) {
                    String name = parser.getAttributeValue(null, "name");
                    String value = parser.getAttributeValue(null, "value");

                    if (name != null && value != null) {
                        name = name.trim().toLowerCase();
                        value = value.trim();

                        switch (name) {
                            case "username":
                                account.setUsername(value);
                                break;
                            case "userpwd":
                                account.setPassword(value);
                                break;
                            case "realm":
                                account.setRealm(value);
                                break;
                            case "home_network_domain_name":
                                account.setHomeDomain(value);
                                break;
                            case "validity":
                                try {
                                    long validitySec = Long.parseLong(value);
                                    if (validitySec > 0) {
                                        account.setExpiresAtTimestamp(System.currentTimeMillis() + (validitySec * 1000));
                                    }
                                } catch (NumberFormatException ignored) {}
                                break;
                            case "msisdn":
                                if (account.getMsisdn() == null || account.getMsisdn().isEmpty()) {
                                    account.setMsisdn(value);
                                }
                                break;
                            case "imsi":
                                account.setImsi(value);
                                break;
                            case "uuid_value":
                                account.setInstanceUuid(value);
                                break;
                        }
                    }
                }
                eventType = parser.next();
            }

            // Derive MSISDN from username if missing (e.g. 91XXXXXXXXXX@wb.wln.ims.jio.com)
            if (account.getMsisdn() == null && account.getUsername() != null) {
                String u = account.getUsername();
                int atIdx = u.indexOf('@');
                if (atIdx != -1) {
                    account.setMsisdn(u.substring(0, atIdx));
                }
            }

            if (account.isValid()) {
                Log.i(TAG, "Successfully parsed JioAccount for user: " + account.getUsername());
                return account;
            } else {
                Log.e(TAG, "Parsed XML is missing essential SIP fields (username, password, or server)");
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing XML provisioning doc: " + e.getMessage(), e);
            return null;
        }
    }
}

