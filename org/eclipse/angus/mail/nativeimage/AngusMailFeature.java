package org.eclipse.angus.mail.nativeimage;

import jakarta.mail.Provider;
import jakarta.mail.Service;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import java.lang.reflect.Executable;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public class AngusMailFeature implements Feature {
	private static final boolean ENABLED = getOption("angus.mail.native-image.enable", true);
	private static final boolean DEBUG = getOption("angus.mail.native-image.trace", false);

	public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
		return ENABLED;
	}

	public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
		ServiceLoader<? extends Provider> providers = ServiceLoader.load(Provider.class);
		Iterator var3 = providers.iterator();

		while (var3.hasNext()) {
			Provider p = (Provider) var3.next();
			Class<? extends Service> pc = access.findClassByName(p.getClassName());
			if (pc != null) {
				log(() -> {
					return MessageFormat.format("Registering {0}", pc);
				});
				RuntimeReflection.register(new Class[]{pc});

				try {
					RuntimeReflection.register(new Executable[]{pc.getConstructor(Session.class, URLName.class)});
				} catch (NoSuchMethodException var7) {
					log(() -> {
						return MessageFormat.format("\tno constructor for {0}", pc);
					});
				}
			} else {
				log(() -> {
					return MessageFormat.format("Class '{0}' for provider '{1}' not found", p.getClassName(),
							p.getClass().getName());
				});
			}
		}

	}

	private static void log(Supplier<String> msg) {
		if (DEBUG) {
			System.out.println((String) msg.get());
		}

	}

	private static boolean getOption(String name, boolean def) {
		String prop = System.getProperty(name);
		return prop == null ? def : Boolean.parseBoolean(name);
	}
}