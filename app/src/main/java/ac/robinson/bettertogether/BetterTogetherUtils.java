/*
 * Copyright (C) 2017 The Better Together Toolkit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package ac.robinson.bettertogether;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BetterTogetherUtils {
	private static final String RANDOM_DICTIONARY = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	private static SecureRandom SECURE_RANDOM = new SecureRandom();

	public static String getRandomString(int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++)
			sb.append(RANDOM_DICTIONARY.charAt(SECURE_RANDOM.nextInt(RANDOM_DICTIONARY.length())));
		return sb.toString();
	}

	public static String trimQuotes(String name) {
		if (TextUtils.isEmpty(name)) {
			return null;
		}
		Matcher m = Pattern.compile("^\"(.*)\"$").matcher(name);
		if (m.find()) {
			return m.group(1);
		}
		return name;
	}

	public static String reversePackageString(String s) {
		if (TextUtils.isEmpty(s)) {
			return s;
		}
		String[] components = s.split("\\.");
		StringBuilder result = new StringBuilder(s.length());
		for (int i = components.length - 1; i > 0; i -= 1) {
			result.append(components[i]).append('.');
		}
		result.append(components[0]);
		return result.toString();
	}

	@Nullable
	public static Bitmap generateQrCode(String text) {
		Hashtable<EncodeHintType, Object> hintMap = new Hashtable<>();

		// medium error correction (15% data loss) - trade-off of QR size against coping with low-quality screens
		hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
		hintMap.put(EncodeHintType.MARGIN, 1); // qr margin size - default = 4

		int size = 256;
		QRCodeWriter qrCodeWriter = new QRCodeWriter();
		BitMatrix bitMatrix;
		try {
			bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size, hintMap);
		} catch (WriterException e) {
			// TODO: handle this better
			return null;
		}

		int matrixSize = bitMatrix.getWidth();
		Bitmap bitmap = Bitmap.createBitmap(matrixSize, matrixSize, Bitmap.Config.RGB_565);
		for (int x = 0; x < matrixSize; x++) {
			for (int y = 0; y < matrixSize; y++) {
				//noinspection SuspiciousNameCombination - reverse to orient QR code correctly
				bitmap.setPixel(y, x, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
			}
		}
		return bitmap;
	}

	// get the colourId (e.g., R.attr.colorButtonNormal, etc) from the given theme
	public static int getThemeColour(Context context, int theme, int colourId) {
		TypedArray typedArray = context.obtainStyledAttributes(theme, new int[]{colourId});
		int colour = typedArray.getColor(0, Color.BLACK);
		typedArray.recycle();
		return colour;
	}
}
