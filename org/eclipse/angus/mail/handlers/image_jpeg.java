package org.eclipse.angus.mail.handlers;

import jakarta.activation.ActivationDataFlavor;
import java.awt.Image;

public class image_jpeg extends image_gif {
	private static ActivationDataFlavor[] myDF = new ActivationDataFlavor[]{
			new ActivationDataFlavor(Image.class, "image/jpeg", "JPEG Image")};

	protected ActivationDataFlavor[] getDataFlavors() {
		return myDF;
	}
}