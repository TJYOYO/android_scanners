/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tj.zxingscanner.zxing.encode;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * This class does the work of decoding the user's request and extracting all
 * the data to be encoded in a barcode.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
final class QRCodeEncoder {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private Context context;
    private QREncode.Builder encodeBuild;

    QRCodeEncoder(QREncode.Builder build, Context context) {
        this.context = context;
        this.encodeBuild = build;
        encodeContentsFromZXing(build);
    }

    Context getContext() {
        return context;
    }

    private void encodeContentsFromZXing(QREncode.Builder build) {
        if (build.getBarcodeFormat() == null
                || build.getBarcodeFormat() == BarcodeFormat.QR_CODE) {
            build.setBarcodeFormat(BarcodeFormat.QR_CODE);
            encodeQRCodeContents(build);
        }
    }

    private void encodeQRCodeContents(QREncode.Builder build) {
        switch (build.getParsedResultType()) {
            case WIFI:
                encodeBuild.setEncodeContents(build.getContents());
                break;

            case CALENDAR:
                encodeBuild.setEncodeContents(build.getContents());
                break;

            case ISBN:
                encodeBuild.setEncodeContents(build.getContents());
                break;

            case PRODUCT:
                encodeBuild.setEncodeContents(build.getContents());
                break;

            case VIN:
                encodeBuild.setEncodeContents(build.getContents());
                break;

            case URI:
                encodeBuild.setEncodeContents(build.getContents());
                break;

            case TEXT:
                encodeBuild.setEncodeContents(build.getContents());
                break;

            case EMAIL_ADDRESS:
                encodeBuild.setEncodeContents("mailto:" + build.getContents());
                break;

            case TEL:
                encodeBuild.setEncodeContents("tel:" + build.getContents());
                break;

            case SMS:
                encodeBuild.setEncodeContents("sms:" + build.getContents());
                break;
            case ADDRESSBOOK:
                Bundle contactBundle = null;
                //uri解析
                Uri addressBookUri = build.getAddressBookUri();
                if (addressBookUri != null)
                    contactBundle = new ParserUriToVCard().parserUri(context, addressBookUri);
                //Bundle解析
                if ((contactBundle != null && contactBundle.isEmpty()) || contactBundle == null)
                    contactBundle = build.getBundle();
                if (contactBundle != null) {
                    String name = contactBundle.getString(ContactsContract.Intents.Insert.NAME);
                    String organization = contactBundle
                            .getString(ContactsContract.Intents.Insert.COMPANY);
                    String address = contactBundle.getString(ContactsContract.Intents.Insert.POSTAL);
                    List<String> phones = getAllBundleValues(contactBundle, ParserUriToVCard.PHONE_KEYS);
                    List<String> phoneTypes = getAllBundleValues(contactBundle, ParserUriToVCard.PHONE_TYPE_KEYS);
                    List<String> emails = getAllBundleValues(contactBundle, ParserUriToVCard.EMAIL_KEYS);
                    String url = contactBundle.getString(ParserUriToVCard.URL_KEY);
                    List<String> urls = url == null ? null : Collections.singletonList(url);
                    String note = contactBundle.getString(ParserUriToVCard.NOTE_KEY);
                    ContactEncoder encoder = build.isUseVCard() ?
                            new VCardContactEncoder() : new MECARDContactEncoder();
                    String[] encoded = encoder.encode(Collections.singletonList(name), organization,
                            Collections.singletonList(address), phones, phoneTypes, emails, urls, note);
                    // Make sure we've encoded at least one field.
                    if (!encoded[1].isEmpty()) {
                        encodeBuild.setEncodeContents(encoded[0]);
                    }
                }
                break;
            case GEO:
                Bundle locationBundle = build.getBundle();
                if (locationBundle != null) {
                    float latitude = locationBundle.getFloat("LAT", Float.MAX_VALUE);
                    float longitude = locationBundle.getFloat("LONG", Float.MAX_VALUE);
                    if (latitude != Float.MAX_VALUE && longitude != Float.MAX_VALUE) {
                        encodeBuild.setEncodeContents("geo:" + latitude + ',' + longitude);
                    }
                }
                break;
        }
    }

    private static List<String> getAllBundleValues(Bundle bundle, String[] keys) {
        List<String> values = new ArrayList<>(keys.length);
        for (String key : keys) {
            Object value = bundle.get(key);
            values.add(value == null ? null : value.toString());
        }
        return values;
    }

    Bitmap encodeAsBitmap(int dimension) throws WriterException {
        if (encodeBuild.getColor() == 0)
            encodeBuild.setColor(BLACK);
        String contentsToEncode = encodeBuild.getEncodeContents();
        if (contentsToEncode == null) {
            return null;
        }
        Map<EncodeHintType, Object> hints = null;
        String encoding = guessAppropriateEncoding(contentsToEncode);
        if (encoding != null) {
            hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, encoding);
        }
        BitMatrix result;
        try {
            result = new MultiFormatWriter().encode(contentsToEncode,
                    encodeBuild.getBarcodeFormat(), dimension, dimension, hints);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            return null;
        }
        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = result.get(x, y) ? encodeBuild.getColor() : WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private static String guessAppropriateEncoding(CharSequence contents) {
        // Very crude at the moment
        for (int i = 0; i < contents.length(); i++) {
            if (contents.charAt(i) > 0xFF) {
                return "UTF-8";
            }
        }
        return null;
    }

}
