package org.eclipse.angus.mail.smtp;

import jakarta.mail.MessagingException;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import org.eclipse.angus.mail.auth.OAuth2SaslClientFactory;
import org.eclipse.angus.mail.util.ASCIIUtility;
import org.eclipse.angus.mail.util.MailLogger;

public class SMTPSaslAuthenticator implements SaslAuthenticator {
	private SMTPTransport pr;
	private String name;
	private Properties props;
	private MailLogger logger;
	private String host;

	public SMTPSaslAuthenticator(SMTPTransport pr, String name, Properties props, MailLogger logger, String host) {
		this.pr = pr;
		this.name = name;
		this.props = props;
		this.logger = logger;
		this.host = host;
	}

	public boolean authenticate(String[] mechs, final String realm, String authzid, final String u, final String p)
			throws MessagingException {
		boolean done = false;
		if (this.logger.isLoggable(Level.FINE)) {
			this.logger.fine("SASL Mechanisms:");

			for (int i = 0; i < mechs.length; ++i) {
				this.logger.fine(" " + mechs[i]);
			}

			this.logger.fine("");
		}

		CallbackHandler cbh = new CallbackHandler() {
			public void handle(Callback[] callbacks) {
				if (SMTPSaslAuthenticator.this.logger.isLoggable(Level.FINE)) {
					SMTPSaslAuthenticator.this.logger.fine("SASL callback length: " + callbacks.length);
				}

				for (int i = 0; i < callbacks.length; ++i) {
					if (SMTPSaslAuthenticator.this.logger.isLoggable(Level.FINE)) {
						SMTPSaslAuthenticator.this.logger.fine("SASL callback " + i + ": " + callbacks[i]);
					}

					if (callbacks[i] instanceof NameCallback) {
						NameCallback ncb = (NameCallback) callbacks[i];
						ncb.setName(u);
					} else if (callbacks[i] instanceof PasswordCallback) {
						PasswordCallback pcb = (PasswordCallback) callbacks[i];
						pcb.setPassword(p.toCharArray());
					} else if (callbacks[i] instanceof RealmCallback) {
						RealmCallback rcb = (RealmCallback) callbacks[i];
						rcb.setText(realm != null ? realm : rcb.getDefaultText());
					} else if (callbacks[i] instanceof RealmChoiceCallback) {
						RealmChoiceCallback rcbx = (RealmChoiceCallback) callbacks[i];
						if (realm == null) {
							rcbx.setSelectedIndex(rcbx.getDefaultChoice());
						} else {
							String[] choices = rcbx.getChoices();

							for (int k = 0; k < choices.length; ++k) {
								if (choices[k].equals(realm)) {
									rcbx.setSelectedIndex(k);
									break;
								}
							}
						}
					}
				}

			}
		};

		SaslClient sc;
		try {
			Map<String, ?> propsMap = this.props;
			sc = Sasl.createSaslClient(mechs, authzid, this.name, this.host, propsMap, cbh);
		} catch (SaslException var15) {
			this.logger.log(Level.FINE, "Failed to create SASL client", var15);
			throw new UnsupportedOperationException(var15.getMessage(), var15);
		}

		if (sc == null) {
			this.logger.fine("No SASL support");
			throw new UnsupportedOperationException("No SASL support");
		} else {
			if (this.logger.isLoggable(Level.FINE)) {
				this.logger.fine("SASL client " + sc.getMechanismName());
			}

			String qop;
			int resp;
			try {
				qop = sc.getMechanismName();
				String ir = null;
				if (sc.hasInitialResponse()) {
					byte[] ba = sc.evaluateChallenge(new byte[0]);
					if (ba.length > 0) {
						ba = Base64.getEncoder().encode(ba);
						ir = ASCIIUtility.toString(ba, 0, ba.length);
					} else {
						ir = "=";
					}
				}

				if (ir != null) {
					resp = this.pr.simpleCommand("AUTH " + qop + " " + ir);
				} else {
					resp = this.pr.simpleCommand("AUTH " + qop);
				}

				if (resp == 530) {
					this.pr.startTLS();
					if (ir != null) {
						resp = this.pr.simpleCommand("AUTH " + qop + " " + ir);
					} else {
						resp = this.pr.simpleCommand("AUTH " + qop);
					}
				}

				if (resp == 235) {
					return true;
				}

				if (resp != 334) {
					return false;
				}
			} catch (Exception var14) {
				this.logger.log(Level.FINE, "SASL AUTHENTICATE Exception", var14);
				return false;
			}

			while (!done) {
				try {
					if (resp == 334) {
						byte[] ba = null;
						if (!sc.isComplete()) {
							ba = ASCIIUtility.getBytes(responseText(this.pr));
							if (ba.length > 0) {
								ba = Base64.getDecoder().decode(ba);
							}

							if (this.logger.isLoggable(Level.FINE)) {
								this.logger.fine("SASL challenge: " + ASCIIUtility.toString(ba, 0, ba.length) + " :");
							}

							ba = sc.evaluateChallenge(ba);
						}

						if (ba == null) {
							this.logger.fine("SASL: no response");
							resp = this.pr.simpleCommand("");
						} else {
							if (this.logger.isLoggable(Level.FINE)) {
								this.logger.fine("SASL response: " + ASCIIUtility.toString(ba, 0, ba.length) + " :");
							}

							ba = Base64.getEncoder().encode(ba);
							resp = this.pr.simpleCommand(ba);
						}
					} else {
						done = true;
					}
				} catch (Exception var13) {
					this.logger.log(Level.FINE, "SASL Exception", var13);
					done = true;
				}
			}

			if (resp != 235) {
				return false;
			} else {
				if (sc.isComplete()) {
					qop = (String) sc.getNegotiatedProperty("javax.security.sasl.qop");
					if (qop != null && (qop.equalsIgnoreCase("auth-int") || qop.equalsIgnoreCase("auth-conf"))) {
						this.logger.fine("SASL Mechanism requires integrity or confidentiality");
						return false;
					}
				}

				return true;
			}
		}
	}

	private static final String responseText(SMTPTransport pr) {
		String resp = pr.getLastServerResponse().trim();
		return resp.length() > 4 ? resp.substring(4) : "";
	}

	static {
		try {
			OAuth2SaslClientFactory.init();
		} catch (Throwable var1) {
		}

	}
}