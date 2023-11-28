package org.eclipse.angus.mail.handlers;

import jakarta.activation.ActivationDataFlavor;
import jakarta.activation.DataContentHandler;
import jakarta.activation.DataSource;
import java.io.IOException;

public abstract class handler_base implements DataContentHandler {
	protected abstract ActivationDataFlavor[] getDataFlavors();

	protected Object getData(ActivationDataFlavor aFlavor, DataSource ds) throws IOException {
		return this.getContent(ds);
	}

	public ActivationDataFlavor[] getTransferDataFlavors() {
		ActivationDataFlavor[] adf = this.getDataFlavors();
		if (adf.length == 1) {
			return new ActivationDataFlavor[]{adf[0]};
		} else {
			ActivationDataFlavor[] df = new ActivationDataFlavor[adf.length];
			System.arraycopy(adf, 0, df, 0, adf.length);
			return df;
		}
	}

	public Object getTransferData(ActivationDataFlavor df, DataSource ds) throws IOException {
		ActivationDataFlavor[] adf = this.getDataFlavors();

		for (int i = 0; i < adf.length; ++i) {
			if (adf[i].equals(df)) {
				return this.getData(adf[i], ds);
			}
		}

		return null;
	}
}